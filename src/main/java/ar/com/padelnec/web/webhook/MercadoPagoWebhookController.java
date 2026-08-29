package ar.com.padelnec.web.webhook;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoSignature;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe las notificaciones de pago de MercadoPago.
 *
 * <p>La URL lleva el slug del club porque la firma se valida con el secreto de ese
 * club: sin saber de quien es la notificacion no hay con que verificarla.
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

    private final TenantService tenantService;
    private final PaymentService paymentService;
    private final MercadoPagoSignature signature;

    @PostMapping("/{slug}")
    public ResponseEntity<Void> receive(
            @PathVariable String slug,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-signature", required = false) String signatureHeader,
            @RequestHeader(name = "x-request-id", required = false) String requestId) {

        Tenant club;
        try {
            club = tenantService.activate(slug);
        } catch (ResourceNotFoundException ex) {
            log.warn("Llego un webhook de MercadoPago para un club inexistente: {}", slug);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!signature.isValid(signatureHeader, requestId, dataId, club.getMpWebhookSecret())) {
            // Sin esta barrera, cualquiera podria confirmar turnos que nadie pago
            // simplemente posteando a esta URL.
            log.warn("Firma invalida en un webhook para el club {}", slug);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // MercadoPago manda varios tipos de evento sobre el mismo endpoint.
        if (!"payment".equalsIgnoreCase(type) || dataId == null || dataId.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            paymentService.applyWebhook(club, dataId);
        } catch (RuntimeException ex) {
            log.error("Fallo el procesamiento del pago {} del club {}", dataId, slug, ex);
        }
        return ResponseEntity.ok().build();
    }
}
