package ar.com.padelnec.web.webhook;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.service.WhatsAppInboxService;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los WhatsApp que nos escriben los jugadores, reenviados por Twilio.
 *
 * <p>Hoy solo sirven para verificar telefonos: el jugador manda el mensaje que le
 * armo la pagina, aca se confirma y sale el turno que estaba reservando
 * ({@link WhatsAppInboxService}).
 * La respuesta va en la misma respuesta HTTP, como TwiML: Twilio la manda como
 * mensaje, sin otra llamada a su API. Es texto libre, que WhatsApp deja mandar
 * porque el jugador acaba de escribir.
 *
 * <p>Se configura en Twilio, en el numero de WhatsApp, como "Webhook URL for
 * incoming messages". Se contesta 200 aunque el mensaje no sirva: un error hace que
 * Twilio lo marque como fallido y no arregla nada.
 */
@RestController
@RequestMapping(TwilioWhatsAppWebhookController.PATH)
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppWebhookController {

    static final String PATH = "/api/webhooks/twilio/whatsapp";

    private final WhatsAppInboxService inbox;
    private final AppProperties properties;

    @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> receive(
            @RequestHeader(name = "X-Twilio-Signature", required = false) String signature,
            HttpServletRequest request) {

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> params.put(name, values.length > 0 ? values[0] : ""));

        // Sin esta barrera, cualquiera podria verificar el numero de otro posteando
        // aca un "From" inventado.
        if (!isSignedByTwilio(signature, params)) {
            log.warn("Firma invalida en un webhook de WhatsApp de Twilio");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(twiml(inbox.receive(params.get("From"), params.get("Body"))));
    }

    private boolean isSignedByTwilio(String signature, Map<String, String> params) {
        AppProperties.Whatsapp config = properties.getWhatsapp();
        String authToken = config.getAuthToken();
        if (signature == null || authToken == null || authToken.isBlank()) {
            return false;
        }
        return new RequestValidator(authToken).validate(webhookUrl(), params, signature);
    }

    /** La URL tal cual la tiene cargada Twilio, que es con la que firma. */
    private String webhookUrl() {
        String configured = properties.getWhatsapp().getWebhookUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return properties.getBaseUrl() + PATH;
    }

    /** Sin mensaje, una respuesta vacia: Twilio la recibe y no le manda nada al jugador. */
    private static String twiml(String message) {
        if (message == null) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>"
                + escape(message) + "</Message></Response>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
