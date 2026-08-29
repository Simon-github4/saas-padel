package ar.com.padelnec.notification;

import ar.com.padelnec.config.AppProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envio real por WhatsApp a traves de Twilio.
 *
 * <p>Twilio expone la API oficial de Meta, con su regla mas importante: un mensaje
 * que inicia el negocio necesita una plantilla aprobada. Por eso, si hay un Content
 * SID configurado para la plantilla, se manda como plantilla con variables; si no,
 * se cae a texto plano, que solo llega dentro de la ventana de 24 horas posterior
 * a un mensaje del jugador o en el sandbox de pruebas.
 *
 * <p>Cargar los Content SID en {@code app.whatsapp.templates} es parte del alta en
 * produccion, no un detalle opcional.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "twilio")
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppSender implements WhatsAppSender {

    private static final String WHATSAPP_PREFIX = "whatsapp:";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        AppProperties.Whatsapp config = properties.getWhatsapp();
        Twilio.init(config.getAccountSid(), config.getAuthToken());

        List<NotificationTemplate> missing = java.util.Arrays.stream(NotificationTemplate.values())
                .filter(template -> !config.getTemplates().containsKey(template.name()))
                .toList();
        if (!missing.isEmpty()) {
            log.warn("Sin Content SID para {}. Esos mensajes van a salir como texto plano y "
                    + "solo llegan dentro de la ventana de 24 horas.", missing);
        }
    }

    @Override
    public String providerName() {
        return "twilio";
    }

    @Override
    public SendResult send(String toE164, NotificationTemplate template,
                           List<String> variables, String plainBody) {
        AppProperties.Whatsapp config = properties.getWhatsapp();
        String contentSid = config.getTemplates().get(template.name());
        try {
            PhoneNumber to = new PhoneNumber(WHATSAPP_PREFIX + toE164);
            PhoneNumber from = new PhoneNumber(WHATSAPP_PREFIX + config.getFromNumber());

            MessageCreator creator = Message.creator(to, from, plainBody);
            if (contentSid != null && !contentSid.isBlank()) {
                // Con Content SID, Twilio manda la plantilla aprobada y el texto plano
                // queda solo como respaldo para los canales que no la soportan.
                creator.setContentSid(contentSid)
                        .setContentVariables(asContentVariables(variables));
            }
            return SendResult.ok(creator.create().getSid());
        } catch (ApiException ex) {
            log.warn("Twilio rechazo el mensaje {} para {}: {}", template, toE164, ex.getMessage());
            return SendResult.failed("Twilio " + ex.getCode() + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Fallo el envio de {} para {}", template, toE164, ex);
            return SendResult.failed(ex.getMessage());
        }
    }

    /**
     * Twilio espera las variables como un JSON con claves "1", "2", "3"...
     *
     * <p>Se serializa con Jackson y no a mano porque los valores traen nombres de
     * jugadores y de canchas: una comilla en "Cancha 1 (techada)" o en un apellido
     * romperia el JSON armado a fuerza de concatenar.
     */
    private String asContentVariables(List<String> variables) {
        Map<String, String> indexed = new LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            indexed.put(String.valueOf(i + 1), variables.get(i));
        }
        try {
            return objectMapper.writeValueAsString(indexed);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudieron serializar las variables de la plantilla", ex);
        }
    }
}
