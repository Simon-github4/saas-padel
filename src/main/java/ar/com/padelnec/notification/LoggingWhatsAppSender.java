package ar.com.padelnec.notification;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de desarrollo: escribe el mensaje en el log en vez de mandarlo.
 *
 * <p>Sirve para trabajar todo el flujo de reserva sin numero habilitado ni costo
 * por conversacion. Los links de confirmacion y de gestion salen completos en la
 * consola, listos para pegar en el navegador.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LoggingWhatsAppSender implements WhatsAppSender {

    @Override
    public String providerName() {
        return "log";
    }

    @Override
    public SendResult send(String toE164, NotificationTemplate template,
                           List<String> variables, String plainBody) {
        log.info("""

                +--- WhatsApp simulado -------------------------------------
                | Para:      {}
                | Plantilla: {}
                | Mensaje:   {}
                +-----------------------------------------------------------""",
                toE164, template, plainBody);
        return SendResult.ok("log-" + System.nanoTime());
    }
}
