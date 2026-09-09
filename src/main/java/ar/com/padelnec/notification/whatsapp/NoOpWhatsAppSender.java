package ar.com.padelnec.notification.whatsapp;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WhatsApp en stand by: no manda nada, ni siquiera al log.
 *
 * <p>Es el default de la aplicacion (produccion, desarrollo y test por igual)
 * hasta que se retome esta funcionalidad. Cada intento queda asentado en
 * {@code NotificationLog} como {@code SKIPPED}, no como {@code FAILED}: no es
 * que el envio se haya caido, es que no se intento.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "off", matchIfMissing = true)
@Slf4j
public class NoOpWhatsAppSender implements WhatsAppSender {

    @Override
    public String providerName() {
        return "off";
    }

    @Override
    public SendResult send(String toE164, NotificationTemplate template,
                           List<String> variables, String plainBody) {
        return SendResult.skipped("WhatsApp en stand by");
    }
}
