package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.PendingPlayerSignup;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.PlayerSession;
import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.PendingPlayerSignupRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.PlayerSessionRepository;
import ar.com.padelnec.security.GoogleIdTokenVerifier;
import ar.com.padelnec.security.GoogleIdTokenVerifier.GoogleIdentity;
import ar.com.padelnec.support.Masking;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login del jugador por email y contrasena (o Google), y su cuenta global.
 *
 * <p>No usa {@link NotificationService}: ese servicio exige un club y una reserva
 * para dejar el registro en {@code NotificationLog} (que es por club), y una cuenta
 * de jugador no tiene ninguno de los dos. Por eso este servicio manda los emails
 * llamando directo al {@link EmailSender}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerAuthService {

    private static final Duration SESSION_TTL = Duration.ofDays(90);
    private static final Duration SIGNUP_CONFIRM_TTL = Duration.ofMinutes(10);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final PlayerAccountRepository playerAccountRepository;
    private final PendingPlayerSignupRepository pendingPlayerSignupRepository;
    private final PlayerSessionRepository playerSessionRepository;
    private final BookingRepository bookingRepository;
    private final PhoneNumbers phoneNumbers;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final AppProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------- alta y login

    /**
     * Arranca el alta: no crea la cuenta todavia. Manda un mail con un codigo de
     * 6 digitos y un link, cualquiera de los dos confirma
     * ({@link #confirmSignup} / {@link #confirmSignupByToken}). Si no se
     * confirma dentro de {@link #SIGNUP_CONFIRM_TTL}, no queda ninguna cuenta.
     */
    @Transactional
    public void register(String rawEmail, String rawPassword, String displayName, String rawPhone) {
        // Oportunista: aprovecha esta escritura en la tabla para descartar
        // intentos abandonados de cualquier email, sin necesitar un job aparte.
        pendingPlayerSignupRepository.deleteAllByExpiresAtBefore(clock.instant());

        String email = normalizeEmail(rawEmail);
        if (playerAccountRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("Ese email ya tiene una cuenta");
        }

        // Un segundo intento con el mismo mail reemplaza al anterior en vez de
        // chocar contra la unique constraint: es como se implementa "reenviar
        // codigo", nada mas que volver a mandar el formulario. El flush
        // explicito importa: Hibernate procesa inserts antes que deletes en un
        // mismo flush, asi que sin esto el insert de mas abajo puede chocar
        // contra esta misma fila que todavia no se borro de la base.
        pendingPlayerSignupRepository.deleteByEmail(email);
        pendingPlayerSignupRepository.flush();

        String code = Tokens.sixDigitCode();
        String confirmToken = Tokens.generate();

        PendingPlayerSignup pending = new PendingPlayerSignup();
        pending.setEmail(email);
        pending.setPasswordHash(passwordEncoder.encode(rawPassword));
        if (displayName != null && !displayName.isBlank()) {
            pending.setDisplayName(displayName.trim());
        }
        if (rawPhone != null && !rawPhone.isBlank()) {
            pending.setPhoneNumber(phoneNumbers.normalize(rawPhone));
        }
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setConfirmToken(confirmToken);
        pending.setExpiresAt(clock.instant().plus(SIGNUP_CONFIRM_TTL));

        try {
            pendingPlayerSignupRepository.saveAndFlush(pending);
        } catch (DataIntegrityViolationException ex) {
            // Dos registros concurrentes con el mismo email: la unique constraint
            // es la garantia de verdad, esto solo la traduce a un mensaje entendible.
            throw new BusinessRuleException("Ese email ya tiene una cuenta");
        }

        String link = properties.getBaseUrl() + "/api/public/player/verify-email?token=" + confirmToken;
        String body = ("Tu código para confirmar la cuenta es %s (vale por 10 minutos).\n"
                + "También podés tocar este link: %s").formatted(code, link);
        sendBestEffort(email, "Confirmá tu cuenta", body);
    }

    /**
     * Confirma el alta con el codigo de 6 digitos y recien ahi crea la cuenta y
     * abre sesion.
     *
     * <p>A proposito sin {@code @Transactional} en el metodo: cuando el codigo
     * es incorrecto, hay que guardar el intento fallido y despues lanzar un
     * error -- con una transaccion propia envolviendo todo, tirar la excepcion
     * revierte tambien ese guardado (Spring hace rollback en cualquier
     * RuntimeException), y el contador de intentos nunca avanzaria. Cada
     * llamada al repositorio ya es transaccional por si sola.
     */
    public PlayerSession confirmSignup(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        PendingPlayerSignup pending = pendingPlayerSignupRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Ese código venció o no es válido. Registrate de nuevo."));

        if (pending.getExpiresAt().isBefore(clock.instant())) {
            pendingPlayerSignupRepository.delete(pending);
            throw new BusinessRuleException("Ese código venció. Registrate de nuevo.");
        }
        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            if (pending.getAttempts() >= MAX_CODE_ATTEMPTS) {
                pendingPlayerSignupRepository.delete(pending);
                throw new BusinessRuleException("Escribiste mal el código muchas veces. Registrate de nuevo.");
            }
            pendingPlayerSignupRepository.save(pending);
            throw new BusinessRuleException("Ese código no es correcto.");
        }

        PlayerAccount account = createAccountFrom(pending);
        return createSession(account);
    }

    /** Mismo alta que {@link #confirmSignup}, por el link en vez del codigo. No abre sesion: es una pagina HTML. */
    @Transactional
    public boolean confirmSignupByToken(String token) {
        Optional<PendingPlayerSignup> found = pendingPlayerSignupRepository.findByConfirmToken(token);
        if (found.isEmpty()) {
            return false;
        }
        PendingPlayerSignup pending = found.get();
        if (pending.getExpiresAt().isBefore(clock.instant())) {
            pendingPlayerSignupRepository.delete(pending);
            return false;
        }
        createAccountFrom(pending);
        return true;
    }

    private PlayerAccount createAccountFrom(PendingPlayerSignup pending) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(pending.getEmail());
        account.setPasswordHash(pending.getPasswordHash());
        account.setDisplayName(pending.getDisplayName());
        account.setPhoneNumber(pending.getPhoneNumber());
        account.setEmailVerified(true);
        account.setLastLoginAt(clock.instant());

        PlayerAccount saved;
        try {
            saved = playerAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Ese email ya tiene una cuenta");
        }
        pendingPlayerSignupRepository.delete(pending);
        return saved;
    }

    /** Mismo mensaje generico para email inexistente y para contrasena incorrecta: no filtra cual de los dos fallo. */
    @Transactional
    public PlayerSession login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        PlayerAccount account = playerAccountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Email o contraseña incorrectos"));

        if (account.getPasswordHash() == null) {
            throw new BusinessRuleException("Esta cuenta se creó con Google. Iniciá sesión con Google.");
        }
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new BusinessRuleException("Email o contraseña incorrectos");
        }

        account.setLastLoginAt(clock.instant());
        return createSession(account);
    }

    /** Verifica el ID token de Google y linkea o crea la cuenta segun corresponda. */
    @Transactional
    public PlayerSession loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(idToken)
                .orElseThrow(() -> new BusinessRuleException("No pudimos verificar tu cuenta de Google"));

        PlayerAccount account = playerAccountRepository.findByGoogleSubject(identity.subject())
                .or(() -> identity.emailVerified()
                        ? playerAccountRepository.findByEmail(normalizeEmail(identity.email()))
                        : Optional.empty())
                .orElseGet(() -> {
                    PlayerAccount created = new PlayerAccount();
                    created.setEmail(normalizeEmail(identity.email()));
                    created.setEmailVerified(identity.emailVerified());
                    created.setDisplayName(identity.name());
                    return created;
                });

        account.setGoogleSubject(identity.subject());
        if (identity.emailVerified()) {
            account.setEmailVerified(true);
        }
        account.setLastLoginAt(clock.instant());

        PlayerAccount saved;
        try {
            saved = playerAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            // Google dice que el email no esta verificado, asi que arriba no se
            // linkeo con la cuenta existente -- pero el email es el mismo, y la
            // unique constraint es quien realmente lo nota.
            throw new BusinessRuleException(
                    "Ese email ya tiene una cuenta. Iniciá sesión con tu contraseña.");
        }
        return createSession(saved);
    }

    private PlayerSession createSession(PlayerAccount account) {
        PlayerSession session = new PlayerSession();
        session.setPlayer(account);
        session.setToken(Tokens.generate());
        session.setExpiresAt(clock.instant().plus(SESSION_TTL));
        return playerSessionRepository.save(session);
    }

    // ------------------------------------------------------ email y contrasena

    /** No revela si el email existe: responde igual en los dos casos. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Optional<PlayerAccount> found = playerAccountRepository.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }
        PlayerAccount account = found.get();
        String token = Tokens.generate();
        account.setPasswordResetToken(token);
        account.setPasswordResetTokenExpiresAt(clock.instant().plus(PASSWORD_RESET_TTL));
        playerAccountRepository.save(account);

        String link = properties.getBaseUrl() + "/reset-password/" + token;
        String body = ("Para elegir una contraseña nueva, tocá este link: %s\nVale por 1 hora. "
                + "Si no lo pediste vos, ignorá este mensaje.")
                .formatted(link);
        sendBestEffort(account.getEmail(), "Recuperar contraseña", body);
    }

    /** Cambia la contrasena y cierra toda otra sesion activa de la cuenta. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PlayerAccount account = playerAccountRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new BusinessRuleException("Ese link venció o no es válido. Pedí uno nuevo"));
        if (account.getPasswordResetTokenExpiresAt() == null
                || account.getPasswordResetTokenExpiresAt().isBefore(clock.instant())) {
            throw new BusinessRuleException("Ese link venció o no es válido. Pedí uno nuevo");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordResetToken(null);
        account.setPasswordResetTokenExpiresAt(null);
        playerAccountRepository.save(account);

        playerSessionRepository.revokeAllForPlayer(account.getId(), clock.instant());
    }

    private void sendBestEffort(String toAddress, String subject, String body) {
        try {
            emailSender.send(toAddress, subject, body);
        } catch (RuntimeException ex) {
            // Un proveedor caido no puede tumbar el registro ni el pedido de reset: el
            // token ya quedo guardado, y el jugador puede pedirlo de nuevo.
            log.warn("Fallo el envio de email a {}", Masking.email(toAddress), ex);
        }
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BusinessRuleException("Necesitamos tu email");
        }
        return rawEmail.trim().toLowerCase();
    }

    // ------------------------------------------------------------ sesion vigente

    /** Resuelve la cuenta duena de una sesion vigente. */
    @Transactional(readOnly = true)
    public PlayerAccount resolveSession(String token) {
        PlayerSession session = playerSessionRepository.findByTokenAndRevokedAtIsNull(token)
                .orElseThrow(() -> new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión."));
        if (session.getExpiresAt().isBefore(clock.instant())) {
            throw new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión.");
        }
        // La cuenta es un proxy LAZY: se la inicializa aca, dentro de la
        // transaccion, para que quien la reciba (p. ej. /me, sin transaccion
        // propia) pueda leerla sin tocar la sesion de Hibernate.
        PlayerAccount player = session.getPlayer();
        Hibernate.initialize(player);
        return player;
    }

    @Transactional
    public void logout(String token) {
        playerSessionRepository.findByTokenAndRevokedAtIsNull(token)
                .ifPresent(session -> {
                    session.setRevokedAt(clock.instant());
                    playerSessionRepository.save(session);
                });
    }

    /** Nombre y telefono de contacto. El telefono solo se completa si la cuenta todavia no tenia uno. */
    @Transactional
    public void updateProfile(String token, String name, String phoneNumber) {
        // resolveSession devuelve la cuenta como proxy lazy; para escribirle un
        // campo conviene la entidad real cargada en esta misma transaccion.
        PlayerAccount owner = playerAccountRepository.findById(resolveSession(token).getId())
                .orElseThrow(() -> new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión."));
        owner.setDisplayName(name.trim());
        if (owner.getPhoneNumber() == null && phoneNumber != null && !phoneNumber.isBlank()) {
            owner.setPhoneNumber(phoneNumbers.normalize(phoneNumber));
        }
    }

    /**
     * Turnos del jugador en todos los clubes de la plataforma, mas recientes primero.
     *
     * <p>Reservar nunca requirio esta cuenta -- se puede seguir reservando como
     * invitado, con solo telefono y nombre. Lo unico que la cuenta expone es este
     * historial cruzando clubes por telefono, y ahi si importa saber que quien lo
     * mira es dueno de ese contacto -- por eso pide el email confirmado antes de
     * mostrar nada. Toda cuenta por contrasena ya nace confirmada
     * ({@link #confirmSignup}); esto solo puede frenar a una cuenta de Google
     * cuyo propio proveedor todavia no verifico ese mail.
     */
    @Transactional(readOnly = true)
    public List<BookingRepository.PlayerBookingHistoryRow> history(String token) {
        PlayerAccount account = resolveSession(token);
        if (!account.isEmailVerified()) {
            throw new BusinessRuleException("Confirmá tu email para ver tus turnos. Revisá tu casilla de entrada.");
        }
        if (account.getPhoneNumber() == null) {
            return List.of();
        }
        return bookingRepository.findHistoryByPhone(account.getPhoneNumber());
    }
}
