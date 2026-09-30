package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.notification.whatsapp.NotificationTemplate;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender.SendResult;
import ar.com.padelnec.repository.PhoneVerificationRepository;
import ar.com.padelnec.support.Masking;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.BusinessRuleException.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verificacion del telefono por WhatsApp antes de la primera reserva.
 *
 * <p>Reservar "de palabra" con un numero inventado dejaba al club con una cancha
 * tomada y nadie a quien llamar. Ahora un numero que nunca reservo en ningun club
 * recibe un codigo por WhatsApp, y sin ese codigo no hay turno: si el codigo llega,
 * el numero existe y es de quien esta reservando. Queda en la lista y no se le
 * vuelve a pedir. Los que ya reservaron alguna vez tampoco pasan por esto.
 *
 * <p>Sin {@code @Transactional} a proposito, igual que el alta de cuentas: un
 * codigo mal escrito tiene que dejar guardado el intento y despues lanzar el
 * error, y una transaccion envolviendo todo revertiria ese guardado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    /** Pedirlo de nuevo antes de esto no manda otro: el anterior puede estar llegando. */
    static final Duration RESEND_AFTER = Duration.ofMinutes(1);
    static final int MAX_CODES_PER_DAY = 5;
    static final int MAX_CODE_ATTEMPTS = 5;

    private final PhoneVerificationRepository repository;
    private final PhoneNumbers phoneNumbers;
    private final WhatsAppSender whatsAppSender;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Prendida por configuracion y con un canal que de verdad mande el codigo. Con
     * WhatsApp en {@code off}, pedirla dejaria a todo jugador nuevo sin poder reservar.
     */
    public boolean isActive() {
        AppProperties.Whatsapp config = properties.getWhatsapp();
        return config.isVerifyPhones() && !"off".equalsIgnoreCase(config.getProvider());
    }

    /**
     * Manda el codigo si este numero lo necesita.
     *
     * @return si hace falta el codigo para reservar con este numero
     */
    public boolean requestCode(String rawPhone) {
        if (!isActive()) {
            return false;
        }
        String phone = phoneNumbers.normalize(rawPhone);
        if (repository.isRegistered(phone)) {
            return false;
        }

        Instant now = clock.instant();
        repository.deleteAllByExpiresAtBefore(now.minus(Duration.ofDays(1)));

        // Volvio atras y apreto reservar de nuevo: el codigo de hace un momento sigue
        // en camino o ya le llego, mandar otro solo confunde cual vale.
        Optional<PhoneVerification> latest = repository.findFirstByPhoneNumberOrderByExpiresAtDesc(phone);
        if (latest.isPresent() && sentAt(latest.get()).isAfter(now.minus(RESEND_AFTER))) {
            return true;
        }
        // Cada envio le cuesta a la plataforma, y sin tope un script puede pedir
        // codigos sin parar para el numero de un tercero.
        if (repository.countByPhoneNumberAndExpiresAtAfter(phone, now.minus(Duration.ofDays(1)).plus(CODE_TTL))
                >= MAX_CODES_PER_DAY) {
            throw new BusinessRuleException(Reason.VERIFICATION_LIMIT,
                    "Pediste muchos códigos para este número. Probá de nuevo mañana o escribile al club.");
        }

        String code = Tokens.sixDigitCode();
        PhoneVerification verification = new PhoneVerification();
        verification.setPhoneNumber(phone);
        verification.setCodeHash(passwordEncoder.encode(code));
        verification.setExpiresAt(now.plus(CODE_TTL));
        repository.save(verification);

        SendResult result = whatsAppSender.send(phone, NotificationTemplate.PHONE_VERIFICATION_CODE, List.of(code),
                "Tu código de TurnosPadel es %s. Vence en %d minutos.".formatted(code, CODE_TTL.toMinutes()));
        if (!result.delivered()) {
            // Un envio que no salio no cuenta para el tope, y el jugador puede reintentar ya.
            repository.delete(verification);
            log.warn("No salio el codigo de verificacion para {}: {}", Masking.phone(phone), result.error());
            throw new BusinessRuleException(Reason.VERIFICATION_CODE_NOT_SENT,
                    "No pudimos mandarte el WhatsApp con el código. Revisá el número o probá de nuevo en un momento.");
        }
        return true;
    }

    /**
     * Frena la reserva de un numero que no esta en la lista y no trae el codigo
     * correcto. Con el codigo bien, el numero entra a la lista antes de reservar:
     * si despues la reserva falla por otra cosa (le ganaron el turno), no tiene que
     * volver a verificarlo.
     */
    public void requireVerified(String rawPhone, String code) {
        if (!isActive()) {
            return;
        }
        String phone = phoneNumbers.normalize(rawPhone);
        if (repository.isRegistered(phone)) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException(Reason.VERIFICATION_REQUIRED,
                    "Para tu primera reserva te mandamos un código por WhatsApp. Escribilo para confirmar.");
        }

        PhoneVerification verification = repository.findFirstByPhoneNumberOrderByExpiresAtDesc(phone)
                .filter(latest -> latest.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new BusinessRuleException(Reason.VERIFICATION_CODE_EXPIRED,
                        "Ese código venció. Pedí uno nuevo."));

        if (!passwordEncoder.matches(code.trim(), verification.getCodeHash())) {
            verification.setAttempts(verification.getAttempts() + 1);
            if (verification.getAttempts() >= MAX_CODE_ATTEMPTS) {
                // No se borra: sigue contando para el tope diario. Vencerlo alcanza.
                verification.setExpiresAt(clock.instant());
                repository.save(verification);
                throw new BusinessRuleException(Reason.VERIFICATION_CODE_EXPIRED,
                        "Escribiste mal el código muchas veces. Pedí uno nuevo.");
            }
            repository.save(verification);
            throw new BusinessRuleException(Reason.VERIFICATION_CODE_INVALID, "Ese código no es correcto.");
        }

        repository.markVerified(phone);
        repository.deleteByPhoneNumber(phone);
    }

    private static Instant sentAt(PhoneVerification verification) {
        return verification.getExpiresAt().minus(CODE_TTL);
    }
}
