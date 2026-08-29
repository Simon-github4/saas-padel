package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.SlotGenerator.ResolvedSlot;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.SlotUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de reserva: los dos caminos del checkout, la confirmacion por link y la
 * cancelacion.
 *
 * <p>La disponibilidad se valida dos veces a proposito. La comprobacion en memoria
 * existe para darle al jugador un mensaje entendible; la garantia de verdad es la
 * restriccion de exclusion de la base, que es lo unico que sobrevive a dos
 * jugadores tocando el mismo horario en el mismo segundo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final Set<BookingStatus> ACTIVE = EnumSet.of(
            BookingStatus.DRAFT, BookingStatus.AWAITING_CONFIRMATION, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final TenantRepository tenantRepository;
    private final CustomerService customerService;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final SlotGenerator slotGenerator;
    private final AlertService alertService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Como quiere pagar el jugador. */
    public enum PaymentChoice {
        /** Sena online por MercadoPago. La reserva nace en DRAFT. */
        DEPOSIT_ONLINE,
        /** De palabra, confirmando por WhatsApp. La reserva nace en AWAITING_CONFIRMATION. */
        PAY_AT_CLUB
    }

    public record NewBooking(UUID courtId, Instant startTime, String fullName,
                             String phoneNumber, PaymentChoice paymentChoice) {
    }

    /** Reserva junto con el club al que pertenece, para los flujos que llegan por token. */
    public record ManagedBooking(Tenant club, Booking booking) {
    }

    // ------------------------------------------------------ alta de reserva

    @Transactional
    public Booking create(Tenant club, NewBooking request) {
        Court court = courtRepository.findById(request.courtId())
                .filter(Court::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("La cancha no existe o no está activa"));

        ResolvedSlot slot = slotGenerator.resolve(club, request.startTime())
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese horario no forma parte de la grilla del club"));

        Instant now = clock.instant();
        validateWindow(club, slot, now);

        BigDecimal price = pricingService
                .resolve(pricingService.rulesFor(slot.operatingDate().getDayOfWeek()),
                        court, slot.operatingDate().getDayOfWeek(), slot.slot().startTime())
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese horario todavía no tiene tarifa publicada. Consultá con el club."));

        Customer customer = customerService.findOrCreate(request.phoneNumber(), request.fullName());
        validateQuota(club, customer, now);

        boolean payAtClub = resolvePaymentMode(club, customer, request.paymentChoice());

        if (!availabilityService.isCourtFree(court, slot.slot().startsAt(), slot.slot().endsAt())) {
            throw new SlotUnavailableException("Justo tomaron ese turno. Elegí otro horario.");
        }

        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(slot.slot().startsAt());
        booking.setEndTime(slot.slot().endsAt());
        booking.setTotalPrice(price);
        booking.setSource(BookingSource.WEB);
        booking.setManagementToken(Tokens.generate());

        if (payAtClub) {
            booking.setStatus(BookingStatus.AWAITING_CONFIRMATION);
            booking.setDepositAmount(BigDecimal.ZERO);
            booking.setConfirmationToken(Tokens.generate());
            booking.setConfirmationExpiresAt(
                    now.plus(Duration.ofMinutes(club.getConfirmationTtlMinutes())));
        } else {
            booking.setStatus(BookingStatus.DRAFT);
            booking.setDepositAmount(
                    pricingService.depositFor(price, club.getDepositPercentage()));
            booking.setDraftExpiresAt(now.plus(Duration.ofMinutes(club.getDraftTtlMinutes())));
        }

        Booking saved = persist(booking);

        if (payAtClub) {
            events.publishEvent(BookingEvent.of(club.getId(), saved.getId(),
                    BookingEvent.Kind.CONFIRMATION_REQUEST));
        }
        return saved;
    }

    /** Alta desde el panel: el club carga un turno que entro por telefono o mostrador. */
    @Transactional
    public Booking createManual(Tenant club, UUID courtId, Instant startTime, String fullName,
                                String phoneNumber, BigDecimal priceOverride, String notes) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("La cancha no existe"));
        ResolvedSlot slot = slotGenerator.resolve(club, startTime)
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese horario no forma parte de la grilla del club"));

        BigDecimal price = priceOverride != null ? priceOverride : pricingService
                .resolve(pricingService.rulesFor(slot.operatingDate().getDayOfWeek()),
                        court, slot.operatingDate().getDayOfWeek(), slot.slot().startTime())
                .orElse(BigDecimal.ZERO);

        Customer customer = customerService.findOrCreate(phoneNumber, fullName);

        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(slot.slot().startsAt());
        booking.setEndTime(slot.slot().endsAt());
        booking.setTotalPrice(price);
        // Lo carga el club, asi que nace firme: no hay nada que confirmar.
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.ADMIN);
        booking.setManagementToken(Tokens.generate());
        booking.setAdminNotes(notes);

        return persist(booking);
    }

    // ------------------------------------------------- confirmacion y baja

    /** Confirma una reserva de palabra a partir del link que llego por WhatsApp. */
    @Transactional
    public ManagedBooking confirmByToken(String token) {
        ManagedBooking managed = resolveByToken(token);
        Booking booking = managed.booking();

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // El jugador toco el link dos veces: no es un error, ya esta confirmado.
            return managed;
        }
        if (booking.getStatus() != BookingStatus.AWAITING_CONFIRMATION) {
            throw new BusinessRuleException("Este turno ya no se puede confirmar");
        }
        if (booking.getConfirmationExpiresAt() != null
                && booking.getConfirmationExpiresAt().isBefore(clock.instant())) {
            throw new BusinessRuleException(
                    "Se venció el plazo para confirmar y la cancha volvió a quedar disponible");
        }

        booking.markConfirmed();
        bookingRepository.save(booking);
        events.publishEvent(BookingEvent.of(managed.club().getId(), booking.getId(),
                BookingEvent.Kind.CONFIRMED_UNPAID));
        return managed;
    }

    /** Lo que ve el jugador al entrar a su portal de gestion. */
    @Transactional(readOnly = true)
    public ManagedBooking findByManagementToken(String token) {
        return resolveByToken(token);
    }

    public record CancellationResult(Booking booking, boolean refundNeeded, String clubWhatsapp) {
    }

    /**
     * Cancelacion iniciada por el jugador desde su portal.
     *
     * <p>Por debajo del limite de horas la API rechaza: a esa altura el club ya no
     * llega a revender el turno, y esa conversacion la tiene que tener una persona.
     */
    @Transactional
    public CancellationResult cancelByManagementToken(String token) {
        ManagedBooking managed = resolveByToken(token);
        Tenant club = managed.club();
        Booking booking = managed.booking();

        if (!booking.getStatus().isCancellable()) {
            throw new BusinessRuleException("Este turno ya no está activo");
        }
        if (!booking.isWithinCancellationWindow(club.getCancellationLimitHours(), clock.instant())) {
            throw new BusinessRuleException(
                    ("Faltan menos de %d horas para tu turno, así que la cancelación la tiene que "
                            + "hacer el club. Escribinos a %s.")
                            .formatted(club.getCancellationLimitHours(), club.getWhatsappNumber()));
        }

        booking.markCancelled(CancellationReason.CUSTOMER, clock.instant());
        bookingRepository.save(booking);

        // La devolucion de la sena es manual: el sistema no mueve plata para atras,
        // solo se asegura de que el club se entere de que tiene que hacerlo.
        boolean refundNeeded = booking.hasMoneyIn();
        if (refundNeeded) {
            alertService.refundRequired(booking);
        }
        return new CancellationResult(booking, refundNeeded, club.getWhatsappNumber());
    }

    /** Baja desde el panel del club. */
    @Transactional
    public Booking cancelByClub(Tenant club, UUID bookingId, String reason) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getStatus().isCancellable()) {
            throw new BusinessRuleException("Este turno ya no esta activo");
        }
        booking.markCancelled(CancellationReason.CLUB, clock.instant());
        booking.setAdminNotes(reason);
        bookingRepository.save(booking);

        if (booking.hasMoneyIn()) {
            alertService.refundRequired(booking);
        }
        events.publishEvent(BookingEvent.of(club.getId(), booking.getId(),
                BookingEvent.Kind.CANCELLED_BY_CLUB));
        return booking;
    }

    // ------------------------------------------------------- panel del club

    /** El turno se jugo. */
    @Transactional
    public Booking markCompleted(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Solo se puede cerrar un turno confirmado");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        return bookingRepository.save(booking);
    }

    /** El jugador no aparecio. Libera la franja y le suma un ausente a su historial. */
    @Transactional
    public Booking markNoShow(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Solo se puede marcar ausente un turno confirmado");
        }
        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);
        customerService.recordNoShow(booking.getCustomer());
        return booking;
    }

    @Transactional(readOnly = true)
    public java.util.List<Booking> agendaFor(Tenant club, LocalDate date) {
        return bookingRepository.findAgenda(
                slotGenerator.dayStart(club, date), slotGenerator.dayEnd(club, date));
    }

    // ------------------------------------------------------------ internos

    /**
     * Guarda traduciendo el choque contra la restriccion de exclusion.
     *
     * <p>Es el desenlace de la carrera entre dos jugadores: uno gana, y al otro hay
     * que decirle que refresque la grilla, no mostrarle un error de base de datos.
     */
    private Booking persist(Booking booking) {
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                log.info("Choque de reservas en la cancha {} a las {}",
                        booking.getCourt().getId(), booking.getStartTime());
                throw new SlotUnavailableException("Justo tomaron ese turno. Elegí otro horario.");
            }
            throw ex;
        }
    }

    private boolean isOverlapViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("ex_booking_no_overlap")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Carga la reserva a la que apunta un token de WhatsApp.
     *
     * <p>Da por hecho que el club ya esta en contexto: lo resuelve
     * {@code TenantContextFilter} antes de que arranque la transaccion. Tiene que ser
     * asi porque Hibernate fija el tenant al abrir la sesion, y establecerlo aca
     * adentro llegaria tarde: las consultas seguirian filtrando por el club anterior.
     */
    private ManagedBooking resolveByToken(String token) {
        UUID clubId = TenantContext.get();
        if (TenantContext.UNSCOPED.equals(clubId)) {
            throw new ResourceNotFoundException("Este link no corresponde a ningún turno");
        }

        Tenant club = tenantRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("El club ya no está disponible"));
        Booking booking = bookingRepository.findByManagementToken(token)
                .or(() -> bookingRepository.findByConfirmationToken(token))
                .orElseThrow(() -> new ResourceNotFoundException("Este link no corresponde a ningun turno"));

        return new ManagedBooking(club, booking);
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("El turno no existe"));
    }

    private void validateWindow(Tenant club, ResolvedSlot slot, Instant now) {
        if (!slot.slot().startsAt().isAfter(now)) {
            throw new BusinessRuleException("Ese horario ya pasó");
        }
        LocalDate today = now.atZone(club.zoneId()).toLocalDate();
        if (slot.operatingDate().isAfter(today.plusDays(club.getBookingHorizonDays()))) {
            throw new BusinessRuleException(
                    "Todavía no se pueden reservar turnos para esa fecha");
        }
    }

    private void validateQuota(Tenant club, Customer customer, Instant now) {
        if (customer.getId() == null) {
            return;
        }
        long active = bookingRepository.countActiveUpcoming(customer.getId(), now, ACTIVE);
        if (active >= club.getMaxActiveBookings()) {
            throw new BusinessRuleException(
                    ("Ya tenés %d turnos reservados. Cancelá alguno o escribinos a %s.")
                            .formatted(active, club.getWhatsappNumber()));
        }
    }

    /**
     * @return true si la reserva va por el camino sin pago anticipado
     */
    private boolean resolvePaymentMode(Tenant club, Customer customer, PaymentChoice choice) {
        boolean canPayAtClub = club.isAllowUnpaidBooking() || customer.isTrusted();

        if (choice == PaymentChoice.PAY_AT_CLUB) {
            if (!canPayAtClub) {
                throw new BusinessRuleException(
                        "Este club pide seña para reservar online");
            }
            return true;
        }
        if (!club.acceptsOnlinePayments()) {
            // El club no tiene MercadoPago cargado: si acepta reservas de palabra se
            // sigue por ahi, y si no, no hay forma de reservar online.
            if (!canPayAtClub) {
                throw new BusinessRuleException(
                        "El club todavía no tiene habilitado el pago online. Escribinos por WhatsApp.");
            }
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findById(UUID bookingId) {
        return bookingRepository.findById(bookingId);
    }
}
