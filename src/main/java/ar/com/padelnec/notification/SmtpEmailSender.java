package ar.com.padelnec.notification;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.support.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP, via el {@link JavaMailSender} que Spring Boot arma
 * solo con las propiedades {@code spring.mail.*}.
 *
 * <p>Sin plantillas: el proyecto no tiene motor de templates y son solo dos
 * mensajes de texto plano (verificar cuenta, resetear contrasena).
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Override
    public String providerName() {
        return "smtp";
    }

    @Override
    public SendResult send(String toAddress, String subject, String plainBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getMail().getFrom());
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(plainBody);
            mailSender.send(message);
            return SendResult.ok();
        } catch (MailException ex) {
            log.warn("Fallo el envio de email a {}: {}", Masking.email(toAddress), ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }
}
