package ar.com.padelnec.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de desarrollo: escribe el email en el log en vez de mandarlo.
 *
 * <p>Sirve para probar el alta de cuenta, la verificacion de email y el reset
 * de contrasena sin credenciales SMTP. Los links salen completos en la
 * consola, listos para pegar en el navegador.
 *
 * <p>Sin {@code matchIfMissing}, a proposito: estos links son credenciales de
 * verdad (alcanzan para tomar una cuenta), asi que "log" hay que pedirlo
 * explicito. Si {@code app.mail.provider} queda sin definir, no se registra
 * ningun {@link EmailSender} y la aplicacion no arranca, en vez de imprimir
 * en el log de produccion un link que alcanza para tomar una cuenta.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log")
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public String providerName() {
        return "log";
    }

    @Override
    public SendResult send(String toAddress, String subject, String plainBody) {
        log.info("""

                +--- Email simulado ----------------------------------------
                | Para:    {}
                | Asunto:  {}
                | Mensaje: {}
                +-----------------------------------------------------------""",
                toAddress, subject, plainBody);
        return SendResult.ok();
    }
}
