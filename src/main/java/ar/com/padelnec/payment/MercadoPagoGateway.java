package ar.com.padelnec.payment;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import com.mercadopago.client.common.PhoneRequest;
import com.mercadopago.client.order.AdditionalInfoRequest;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.order.OrderConfigRequest;
import com.mercadopago.client.order.OrderCreateRequest;
import com.mercadopago.client.order.OrderItemRequest;
import com.mercadopago.client.order.OrderOnlineConfig;
import com.mercadopago.client.order.OrderPayerRequest;
import com.mercadopago.client.order.PayerInfo;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;
import com.mercadopago.resources.order.OrderPayment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Envoltorio del SDK de MercadoPago para Checkout Pro vía Orders API.
 *
 * <p>Cada club cobra con su propia cuenta (conectada por OAuth, ver
 * {@link MercadoPagoOAuthService}), asi que el access token viaja por request en
 * {@link MPRequestOptions} y nunca por el estatico global del SDK: ese estatico es
 * compartido por toda la JVM y, con varios clubes atendiendo en paralelo,
 * terminaria cobrandole a uno la sena de otro.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoGateway {

    /** Piso defensivo: una order con 0 o negativo de vigencia no tiene sentido pedirsela a MercadoPago. */
    private static final Duration MIN_EXPIRATION = Duration.ofMinutes(1);

    /** Categoria de la lista estandar de MercadoPago que corresponde a alquilar una cancha. */
    private static final String ITEM_CATEGORY = "services";

    /** Fechas como las muestran los ejemplos de MercadoPago: {@code 2026-09-18T20:00:00.000-03:00}. */
    private static final DateTimeFormatter MP_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final AppProperties properties;
    private final Clock clock;

    /** Order de pago creada, con el link al que se manda al jugador. */
    public record Checkout(String orderId, String checkoutUrl) {
    }

    /**
     * Crea la order de la sena.
     *
     * <p>La order expira junto con el DRAFT: si el jugador se queda en el checkout
     * mas de lo que dura la reserva, MercadoPago le rechaza el pago en vez de
     * cobrarle una cancha que ya se libero.
     *
     * <p>{@code payer.email} es obligatorio para la API de Orders y este sistema
     * nunca le pide un mail al jugador -reserva por telefono, via WhatsApp-, asi
     * que se genera uno sintetico por reserva a partir del remitente real de la
     * plataforma ({@code app.mail.from}): si MercadoPago llega a mandar algo ahi,
     * cae en una casilla que existe de verdad en vez de rebotar.
     */
    public Checkout createDepositCheckout(Tenant club, Booking booking, DepositPayer payer) {
        // Sin external_code: MercadoPago lo limita a 30 caracteres y un UUID tiene
        // 36. external_reference a nivel order ya ata el pago a la reserva.
        OrderItemRequest item = OrderItemRequest.builder()
                .title("Seña %s - %s".formatted(club.getName(), booking.getCourt().getName()))
                .description("Turno del %s".formatted(
                        booking.getStartTime().atZone(club.zoneId()).toLocalDateTime()))
                .unitPrice(booking.getDepositAmount().toPlainString())
                .quantity(1)
                // Un turno de cancha es un servicio con fecha: el antifraude pondera
                // distinto una reserva para esta noche que una compra sin fecha.
                .categoryId(ITEM_CATEGORY)
                .eventDate(MP_DATE_TIME.format(booking.getStartTime().atZone(club.zoneId())))
                .build();

        OrderOnlineConfig online = OrderOnlineConfig.builder()
                .successUrl(returnUrl(booking))
                .pendingUrl(returnUrl(booking))
                .failureUrl(returnUrl(booking))
                .autoReturn("approved")
                .build();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .type("online")
                // Manual: la order queda creada y el pago se resuelve del lado del
                // checkout hospedado de MercadoPago, no en esta misma llamada.
                .processingMode("manual")
                // Ata el pago a la reserva: es lo que permite reconciliar cuando
                // llega el webhook.
                .externalReference(booking.getId().toString())
                .totalAmount(booking.getDepositAmount().toPlainString())
                .payer(payerRequest(booking, payer))
                //.additionalInfo(additionalInfo(club, payer))
                .items(List.of(item))
                .config(OrderConfigRequest.builder().online(online).build())
                .expirationTime(expirationTime(club, booking))
                .build();

        try {
            Order order = new OrderClient().create(request, createOptionsFor(club));
            return new Checkout(order.getId(), order.getCheckoutUrl());
        } catch (MPApiException ex) {
            log.error("MercadoPago rechazo la order del club {}: {} - {}",
                    club.getSlug(), ex.getStatusCode(), ex.getApiResponse().getContent());
            throw new PaymentGatewayException(
                    "No pudimos generar el link de pago. Probá de nuevo en un momento.");
        } catch (MPException ex) {
            log.error("Fallo la comunicacion con MercadoPago para el club {}", club.getSlug(), ex);
            throw new PaymentGatewayException(
                    "No pudimos generar el link de pago. Probá de nuevo en un momento.");
        }
    }

    /**
     * Consulta una order contra la API de MercadoPago y devuelve sus pagos.
     *
     * <p>El webhook solo trae el id de la order: el estado se pregunta siempre a la
     * fuente, porque el cuerpo de la notificacion no es prueba de que el dinero
     * exista. Una order puede acumular mas de un intento de pago (un rechazo y
     * despues uno aprobado, por ejemplo), asi que se devuelven todos y quien llama
     * decide que hacer con cada uno.
     */
    public List<ApprovedPayment> fetchOrderPayments(Tenant club, String orderId) {
        try {
            Order order = new OrderClient().get(orderId, optionsFor(club));
            if (order.getTransactions() == null || order.getTransactions().getPayments() == null) {
                return List.of();
            }
            return order.getTransactions().getPayments().stream()
                    .map(payment -> toApprovedPayment(order, payment))
                    .toList();
        } catch (MPApiException ex) {
            log.warn("MercadoPago no devolvio la order {} del club {}: {}",
                    orderId, club.getSlug(), ex.getStatusCode());
            return List.of();
        } catch (MPException ex) {
            log.warn("Fallo la consulta de la order {} del club {}", orderId, club.getSlug(), ex);
            return List.of();
        }
    }

    private ApprovedPayment toApprovedPayment(Order order, OrderPayment payment) {
        BigDecimal amount = payment.getAmount() == null ? null : new BigDecimal(payment.getAmount());
        return new ApprovedPayment(payment.getId(), payment.getStatus(), order.getExternalReference(), amount);
    }

    /**
     * Datos de un pago dentro de una order, tal como los devuelve MercadoPago.
     *
     * <p>{@code status} es el de la transaccion de pago dentro de la Orders API
     * ({@code processed}, {@code processing}, {@code action_required},
     * {@code canceled}, {@code charged_back}, {@code expired}, {@code failed},
     * {@code refunded}), no el de la vieja API de Payments ({@code approved},
     * {@code rejected}): son vocabularios distintos.
     */
    public record ApprovedPayment(String paymentId, String status, String externalReference,
                                  BigDecimal amount) {

        public boolean isApproved() {
            return "processed".equalsIgnoreCase(status);
        }

        public boolean isRejected() {
            return "failed".equalsIgnoreCase(status)
                    || "canceled".equalsIgnoreCase(status)
                    || "expired".equalsIgnoreCase(status);
        }
    }

    /**
     * Quien paga, con todo lo que se sepa de el.
     *
     * <p>El mail real solo sale si la cuenta lo tiene verificado; si no, va el
     * sintetico. Un mail que no coincide con quien paga es de las senales que mas
     * suman para el antifraude, pero uno sin verificar puede no ser de el.
     */
    private OrderPayerRequest payerRequest(Booking booking, DepositPayer payer) {
        OrderPayerRequest.OrderPayerRequestBuilder request = OrderPayerRequest.builder()
                .email(payer.email() != null ? payer.email() : syntheticPayerEmail(booking))
                .firstName(payer.firstName())
                .lastName(payer.lastName());
        if (payer.phoneNumber() != null) {
            request.phone(PhoneRequest.builder()
                    .areaCode(payer.phoneAreaCode())
                    .number(payer.phoneNumber())
                    .build());
        }
        return request.build();
    }

    /** Contexto de la compra que no entra en {@code payer}: desde donde y con que cuenta. */
    private AdditionalInfoRequest additionalInfo(Tenant club, DepositPayer payer) {
        boolean hasAccount = payer.registeredAt() != null;
        return AdditionalInfoRequest.builder()
                .payer(PayerInfo.builder()
                        .ipAddress(payer.ipAddress())
                        // Solo el que tiene cuenta se autentico; del invitado no se sabe nada.
                        .authenticationType(hasAccount ? "WEB" : null)
                        .registrationDate(hasAccount
                                ? MP_DATE_TIME.format(payer.registeredAt().atZone(club.zoneId()))
                                : null)
                        .build())
                .build();
    }

    private String syntheticPayerEmail(Booking booking) {
        String from = properties.getMail().getFrom();
        int at = from.indexOf('@');
        return from.substring(0, at) + "+reserva-" + booking.getId() + from.substring(at);
    }

    private String returnUrl(Booking booking) {
        return properties.getBaseUrl() + "/manage/" + booking.getManagementToken();
    }

    /**
     * Duracion ISO 8601 (ej. "PT9M42S") hasta que vence el DRAFT, formato que exige
     * {@code expiration_time}.
     *
     * <p>Truncada a segundos: {@code Instant.now()} trae nanosegundos, y
     * {@code Duration.toString()} los vuelca tal cual ("PT9M59.806925705S").
     * MercadoPago devuelve {@code 400 property_value} ante esa fraccion -no
     * documentado, encontrado probando contra la API real- asi que se descarta.
     */
    private String expirationTime(Tenant club, Booking booking) {
        Instant expiresAt = booking.getDraftExpiresAt() != null
                ? booking.getDraftExpiresAt()
                : clock.instant().plus(Duration.ofMinutes(club.getDraftTtlMinutes()));
        Duration remaining = Duration.between(clock.instant(), expiresAt).truncatedTo(ChronoUnit.SECONDS);
        if (remaining.compareTo(MIN_EXPIRATION) < 0) {
            remaining = MIN_EXPIRATION;
        }
        return remaining.toString();
    }

    private MPRequestOptions optionsFor(Tenant club) {
        return MPRequestOptions.builder()
                .accessToken(club.getMpAccessToken())
                // Sin esto el SDK no tiene limite: un MercadoPago lento cuelga el
                // hilo que esta cobrando o consultando un pago indefinidamente.
                .connectionTimeout(5000)
                .socketTimeout(10000)
                .build();
    }

    /** Igual que {@link #optionsFor}, mas la clave de idempotencia que exige crear una order. */
    private MPRequestOptions createOptionsFor(Tenant club) {
        return MPRequestOptions.builder()
                .accessToken(club.getMpAccessToken())
                .connectionTimeout(5000)
                .socketTimeout(10000)
                .customHeaders(Map.of("X-Idempotency-Key", UUID.randomUUID().toString()))
                .build();
    }
}
