package ar.com.padelnec.notification;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.support.Masking;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP, via el {@link JavaMailSender} que Spring Boot arma
 * solo con las propiedades {@code spring.mail.*}.
 *
 * <p>Sale multipart/alternative: el HTML de {@link EmailTemplates} y el texto
 * de respaldo en el mismo mail, y el cliente elige cual mostrar. El remitente
 * lleva el nombre de la marca si {@code app.mail.from} no trae uno: en la
 * bandeja se lee "TurnosPadel" y no "no-responder".
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
    public SendResult send(String toAddress, EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(sender());
            helper.setTo(toAddress);
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            return SendResult.ok();
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn("Fallo el envio de email a {}: {}", Masking.email(toAddress), ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    /** {@code app.mail.from}, con el nombre de la marca si viene como direccion sola. */
    private InternetAddress sender() throws MessagingException, UnsupportedEncodingException {
        InternetAddress from = new InternetAddress(properties.getMail().getFrom());
        if (from.getPersonal() == null) {
            from.setPersonal(EmailTemplates.BRAND, StandardCharsets.UTF_8.name());
        }
        return from;
    }
}
