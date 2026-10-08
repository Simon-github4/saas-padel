package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.PhoneVerificationRepository;
import ar.com.padelnec.support.Masking;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.BusinessRuleException.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Verificacion del telefono por WhatsApp antes de la primera reserva sin sena.
 *
 * <p>Reservar "de palabra" con un numero inventado dejaba al club con una cancha
 * tomada y nadie a quien llamar. Ahora un numero que nunca se verifico tiene que
 * mandarnos un WhatsApp: la pagina le arma el mensaje con un codigo, el jugador lo
 * manda, y Twilio nos avisa desde que numero llego ({@link #handleInbound}). Si
 * llega desde el numero que escribio en la reserva, el numero existe y es suyo.
 * Queda en la lista y no se le vuelve a pedir.
 *
 * <p>Es al reves de lo habitual -- que nosotros le mandemos el codigo -- porque un
 * mensaje que inicia el negocio necesita una plantilla aprobada por Meta, y la de
 * autenticacion no la aprueban para un numero nuevo. Un mensaje que inicia el
 * jugador no necesita ninguna, y a nosotros no nos cuesta mandarlo.
 *
 * <p>Con sena no se pide: el pago ya compromete a quien reserva.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneVerificationService {

    /** Lo que tiene para mandar el mensaje. Alcanza para abrir WhatsApp, mandarlo y volver. */
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_REQUESTS_PER_DAY = 5;

    /** El codigo es el unico grupo de 6 digitos del mensaje, aunque el jugador le cambie el resto. */
    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private final PhoneVerificationRepository repository;
    private final PhoneNumbers phoneNumbers;
    private final AppProperties properties;
    private final Clock clock;

    /** Lo que la pagina necesita para pedirle el mensaje al jugador. */
    public record Challenge(UUID id, String whatsappLink) {
    }

    public enum Status {
        /** Todavia no llego el mensaje. */
        PENDING,
        VERIFIED,
        /** Paso el plazo sin que llegara: hay que pedir otro. */
        EXPIRED
    }

    /** Que paso con un mensaje que llego, para contestarle al jugador. */
    public enum InboundOutcome {
        VERIFIED,
        /** El numero ya estaba verificado: mando el mensaje dos veces, o uno viejo. */
        ALREADY_VERIFIED,
        /** El codigo existe pero se pidio para otro numero. */
        WRONG_NUMBER,
        /** El codigo vencio o nunca existio. */
        UNKNOWN_CODE,
        /** No trae ningun codigo: alguien escribio por otra cosa. */
        NO_CODE
    }

    /**
     * Prendida por configuracion, con WhatsApp andando y un numero al que escribir.
     * Sin alguna de las tres, pedirla dejaria a todo jugador nuevo sin poder
     * reservar sin sena.
     */
    public boolean isActive() {
        AppProperties.Whatsapp config = properties.getWhatsapp();
        return config.isVerifyPhones()
                && !"off".equalsIgnoreCase(config.getProvider())
                && config.getFromNumber() != null && !config.getFromNumber().isBlank();
    }

    /**
     * Si el numero ya probo ser real: lo verifico, estaba en la lista de algun club
     * al lanzar la verificacion, o un club le cargo un turno.
     *
     * @param phone en E.164
     */
    public boolean isVerified(String phone) {
        return repository.isRegistered(phone);
    }

    /**
     * El mensaje que tiene que mandar el jugador, si este numero lo necesita.
     *
     * <p>Mientras el pedido anterior siga vigente se devuelve el mismo: el jugador
     * que vuelve atras y toca reservar de nuevo puede tener ese mensaje ya escrito
     * en WhatsApp, y un codigo nuevo lo dejaria sin efecto.
     *
     * @param club solo para el texto del mensaje: la verificacion vale para todos
     */
    public Optional<Challenge> requestVerification(String rawPhone, Tenant club) {
        if (!isActive()) {
            return Optional.empty();
        }
        String phone = phoneNumbers.normalize(rawPhone);
        if (repository.isRegistered(phone)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        repository.deleteAllByExpiresAtBefore(now.minus(Duration.ofDays(1)));

        Optional<PhoneVerification> pending = repository.findFirstByPhoneNumberOrderByExpiresAtDesc(phone)
                .filter(latest -> !latest.isConfirmed() && latest.getExpiresAt().isAfter(now));
        if (pending.isPresent()) {
            return Optional.of(challenge(pending.get(), club));
        }
        // Sin tope, un script llena la tabla de pedidos para el numero de un tercero.
        if (repository.countByPhoneNumberAndExpiresAtAfter(phone, now.minus(Duration.ofDays(1)).plus(CODE_TTL))
                >= MAX_REQUESTS_PER_DAY) {
            throw new BusinessRuleException(Reason.VERIFICATION_LIMIT,
                    "Pediste confirmar este número muchas veces. Probá de nuevo mañana o escribile al club.");
        }

        PhoneVerification verification = new PhoneVerification();
        verification.setPhoneNumber(phone);
        verification.setCode(unusedCode(now));
        verification.setExpiresAt(now.plus(CODE_TTL));
        return Optional.of(challenge(repository.save(verification), club));
    }

    /** En que anda un pedido. Uno que ya no existe se limpio por viejo: vencido. */
    public Status status(UUID verificationId) {
        Optional<PhoneVerification> found = repository.findById(verificationId);
        if (found.isEmpty()) {
            return Status.EXPIRED;
        }
        PhoneVerification verification = found.get();
        // Tambien si se verifico por otro lado: otra pestana, otro pedido.
        if (verification.isConfirmed() || repository.isRegistered(verification.getPhoneNumber())) {
            return Status.VERIFIED;
        }
        return verification.getExpiresAt().isAfter(clock.instant()) ? Status.PENDING : Status.EXPIRED;
    }

    /**
     * Un WhatsApp que nos llego. Si trae el codigo de un pedido vigente y viene
     * desde el numero de ese pedido, el numero queda verificado.
     *
     * <p>Desde otro numero no alcanza aunque el codigo sea bueno: el codigo lo ve
     * cualquiera que tenga la pagina abierta, y lo unico que prueba que el numero es
     * de quien reserva es que el mensaje salga de ahi.
     *
     * @param rawFrom el remitente como lo manda Twilio ({@code whatsapp:+549...})
     */
    public InboundOutcome handleInbound(String rawFrom, String body) {
        List<String> codes = codesIn(body);
        if (codes.isEmpty()) {
            return InboundOutcome.NO_CODE;
        }
        Optional<String> from = sender(rawFrom);
        if (from.isEmpty()) {
            return InboundOutcome.UNKNOWN_CODE;
        }
        String phone = from.get();

        Instant now = clock.instant();
        boolean forAnotherNumber = false;
        for (String code : codes) {
            List<PhoneVerification> candidates = repository.findByCodeAndExpiresAtAfter(code, now);
            Optional<PhoneVerification> mine = candidates.stream()
                    .filter(candidate -> candidate.getPhoneNumber().equals(phone))
                    .findFirst();
            if (mine.isPresent()) {
                return confirm(mine.get(), now);
            }
            forAnotherNumber |= !candidates.isEmpty();
        }
        if (forAnotherNumber) {
            log.info("Llego un codigo de verificacion desde otro numero ({})", Masking.phone(phone));
            return InboundOutcome.WRONG_NUMBER;
        }
        return repository.isRegistered(phone) ? InboundOutcome.ALREADY_VERIFIED : InboundOutcome.UNKNOWN_CODE;
    }

    /**
     * Frena la reserva sin sena de un numero que no esta verificado. Se verifica
     * mandando el mensaje ({@link #requestVerification}); la reserva no trae nada.
     */
    public void requireVerified(String rawPhone) {
        if (!isActive()) {
            return;
        }
        if (!repository.isRegistered(phoneNumbers.normalize(rawPhone))) {
            throw new BusinessRuleException(Reason.VERIFICATION_REQUIRED,
                    "Para tu primera reserva sin seña confirmamos tu número por WhatsApp.");
        }
    }

    private InboundOutcome confirm(PhoneVerification verification, Instant now) {
        if (verification.isConfirmed()) {
            return InboundOutcome.ALREADY_VERIFIED;
        }
        verification.setConfirmedAt(now);
        repository.save(verification);
        repository.markVerified(verification.getPhoneNumber());
        log.info("Telefono verificado por WhatsApp: {}", Masking.phone(verification.getPhoneNumber()));
        return InboundOutcome.VERIFIED;
    }

    private Challenge challenge(PhoneVerification verification, Tenant club) {
        String message = "¡Hola! Quiero confirmar mi número para reservar en %s. Mi código es %s"
                .formatted(club.getName(), verification.getCode());
        return new Challenge(verification.getId(),
                phoneNumbers.whatsappLink(properties.getWhatsapp().getFromNumber(), message));
    }

    /**
     * Un codigo que ningun otro pedido en curso este usando. Con un millon de
     * codigos y pedidos de 10 minutos, repetir es rarisimo; si pasa, se sortea otro.
     * Y si aun asi coincidiera, el numero que manda el mensaje los desempata.
     */
    private String unusedCode(Instant now) {
        String code = Tokens.sixDigitCode();
        for (int attempt = 0; attempt < 5 && repository.existsByCodeAndExpiresAtAfterAndConfirmedAtIsNull(code, now);
             attempt++) {
            code = Tokens.sixDigitCode();
        }
        return code;
    }

    private static List<String> codesIn(String body) {
        List<String> codes = new ArrayList<>();
        if (body == null) {
            return codes;
        }
        Matcher matcher = CODE.matcher(body);
        while (matcher.find()) {
            codes.add(matcher.group());
        }
        return codes;
    }

    /** El numero que mando el mensaje, en E.164. Vacio si no se entiende. */
    private Optional<String> sender(String rawFrom) {
        if (rawFrom == null || rawFrom.isBlank()) {
            return Optional.empty();
        }
        String number = rawFrom.trim();
        if (number.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            number = number.substring("whatsapp:".length());
        }
        try {
            return Optional.of(phoneNumbers.normalize(number));
        } catch (RuntimeException ex) {
            log.warn("Llego un WhatsApp de un remitente que no se entiende: {}", Masking.phone(number));
            return Optional.empty();
        }
    }
}
