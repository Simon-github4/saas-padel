package ar.com.padelnec.payment;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Envoltorio del SDK de MercadoPago.
 *
 * <p>Cada club cobra con su propia cuenta, asi que el access token viaja por
 * request en {@link MPRequestOptions} y nunca por el estatico global del SDK:
 * ese estatico es compartido por toda la JVM y, con varios clubes atendiendo en
 * paralelo, terminaria cobrandole a uno la sena de otro.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoGateway {

    private static final String CURRENCY = "ARS";

    private final AppProperties properties;
    private final Clock clock;

    /** Preferencia de pago creada, con el link al que se manda al jugador. */
    public record Checkout(String preferenceId, String checkoutUrl) {
    }

    /**
     * Crea la preferencia de la sena.
     *
     * <p>La preferencia expira junto con el DRAFT: si el jugador se queda en el
     * checkout mas de lo que dura la reserva, MercadoPago le rechaza el pago en vez
     * de cobrarle una cancha que ya se libero.
     */
    public Checkout createDepositCheckout(Tenant club, Booking booking) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(booking.getId().toString())
                .title("Sena %s - %s".formatted(club.getName(), booking.getCourt().getName()))
                .description("Turno del %s".formatted(
                        booking.getStartTime().atZone(club.zoneId()).toLocalDateTime()))
                .quantity(1)
                .currencyId(CURRENCY)
                .unitPrice(booking.getDepositAmount())
                .build();

        OffsetDateTime expiresAt = booking.getDraftExpiresAt() != null
                ? booking.getDraftExpiresAt().atOffset(ZoneOffset.UTC)
                : clock.instant().plus(Duration.ofMinutes(club.getDraftTtlMinutes()))
                        .atOffset(ZoneOffset.UTC);

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(item))
                // Ata el pago a la reserva: es lo que permite reconciliar cuando
                // llega el webhook.
                .externalReference(booking.getId().toString())
                .notificationUrl(webhookUrl(club))
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(returnUrl(booking))
                        .pending(returnUrl(booking))
                        .failure(returnUrl(booking))
                        .build())
                .autoReturn("approved")
                .expires(true)
                .expirationDateTo(expiresAt)
                .build();

        try {
            Preference preference = new PreferenceClient().create(request, optionsFor(club));
            return new Checkout(preference.getId(), preference.getInitPoint());
        } catch (MPApiException ex) {
            log.error("MercadoPago rechazo la preferencia del club {}: {} - {}",
                    club.getSlug(), ex.getStatusCode(), ex.getApiResponse().getContent());
            throw new PaymentGatewayException(
                    "No pudimos generar el link de pago. Proba de nuevo en un momento.");
        } catch (MPException ex) {
            log.error("Fallo la comunicacion con MercadoPago para el club {}", club.getSlug(), ex);
            throw new PaymentGatewayException(
                    "No pudimos generar el link de pago. Proba de nuevo en un momento.");
        }
    }

    /**
     * Consulta un pago contra la API de MercadoPago.
     *
     * <p>El webhook solo trae un identificador: el estado se pregunta siempre a la
     * fuente, porque el cuerpo de la notificacion no es prueba de que el dinero
     * exista.
     */
    public Optional<ApprovedPayment> fetchPayment(Tenant club, String paymentId) {
        try {
            com.mercadopago.resources.payment.Payment payment =
                    new PaymentClient().get(Long.parseLong(paymentId), optionsFor(club));
            return Optional.of(new ApprovedPayment(
                    String.valueOf(payment.getId()),
                    payment.getStatus(),
                    payment.getExternalReference(),
                    payment.getTransactionAmount()));
        } catch (NumberFormatException ex) {
            log.warn("El webhook del club {} trajo un id de pago no numerico: {}",
                    club.getSlug(), paymentId);
            return Optional.empty();
        } catch (MPApiException ex) {
            log.warn("MercadoPago no devolvio el pago {} del club {}: {}",
                    paymentId, club.getSlug(), ex.getStatusCode());
            return Optional.empty();
        } catch (MPException ex) {
            log.warn("Fallo la consulta del pago {} del club {}", paymentId, club.getSlug(), ex);
            return Optional.empty();
        }
    }

    /** Datos del pago tal como los devuelve MercadoPago. */
    public record ApprovedPayment(String paymentId, String status, String externalReference,
                                  BigDecimal amount) {

        public boolean isApproved() {
            return "approved".equalsIgnoreCase(status);
        }

        public boolean isRejected() {
            return "rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status);
        }
    }

    public String webhookUrl(Tenant club) {
        // Lleva el slug para que el webhook sepa de que club es antes de poder
        // validar la firma, que se calcula con el secreto de ese club.
        return properties.getBaseUrl() + "/api/webhooks/mercadopago/" + club.getSlug();
    }

    private String returnUrl(Booking booking) {
        return properties.getBaseUrl() + "/manage/" + booking.getManagementToken();
    }

    private MPRequestOptions optionsFor(Tenant club) {
        return MPRequestOptions.builder()
                .accessToken(club.getMpAccessToken())
                .build();
    }
}
