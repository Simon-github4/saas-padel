package ar.com.padelnec.web.webhook;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoSignature;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Recibe las notificaciones de pago de MercadoPago.
 *
 * <p>Un unico endpoint para todos los clubes: con OAuth, todos cuelgan de la
 * misma aplicacion de MercadoPago (Tus integraciones), que tiene un solo
 * webhook configurado y firma todas sus notificaciones con la misma clave
 * ({@code AppProperties.Mercadopago.webhookSecret}). Ya no hay slug en la URL
 * -no hay forma de que MercadoPago lo mande- asi que el club se identifica
 * por el {@code user_id} que trae el cuerpo de la notificacion, cruzado contra
 * {@code Tenant.mpUserId}.
 *
 * <p>Se responde 200 incluso ante notificaciones que no se pueden procesar. Un
 * error devuelto hace que MercadoPago reintente durante horas, y reintentar algo
 * que esta mal formado no lo arregla; lo que si importa es que quede en el log.
 */
@RestController
@RequestMapping("/api/webhooks/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final TenantRepository tenantRepository;
    private final PaymentService paymentService;
    private final MercadoPagoSignature signature;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    /** Lo unico que hace falta del cuerpo de la notificacion: el resto se pregunta a la API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebhookPayload(@JsonProperty("user_id") String userId) {
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-signature", required = false) String signatureHeader,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) String rawBody) {

        if (!signature.isValid(signatureHeader, requestId, dataId,
                properties.getMercadopago().getWebhookSecret())) {
            // Sin esta barrera, cualquiera podria confirmar turnos que nadie pago
            // simplemente posteando a esta URL.
            log.warn("Firma invalida en un webhook de MercadoPago");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // MercadoPago manda varios topicos sobre el mismo endpoint.
        if (!"order".equalsIgnoreCase(type) || dataId == null || dataId.isBlank()) {
            return ResponseEntity.ok().build();
        }

        String userId = extractUserId(rawBody);
        if (userId == null || userId.isBlank()) {
            log.warn("Llego una notificacion de order sin user_id legible en el cuerpo");
            return ResponseEntity.ok().build();
        }

        Optional<Tenant> club = tenantRepository.findByMpUserId(userId);
        if (club.isEmpty()) {
            log.warn("Llego un webhook de MercadoPago de una cuenta sin club conectado (user_id {})", userId);
            return ResponseEntity.ok().build();
        }

        TenantContext.set(club.get().getId());
        try {
            paymentService.applyWebhook(club.get(), dataId);
        } catch (RuntimeException ex) {
            log.error("Fallo el procesamiento de la order {} del club {}",
                    dataId, club.get().getSlug(), ex);
        }
        return ResponseEntity.ok().build();
    }

    private String extractUserId(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, WebhookPayload.class).userId();
        } catch (JacksonException ex) {
            log.warn("Cuerpo de webhook de MercadoPago ilegible", ex);
            return null;
        }
    }
}
