package ar.com.padelnec.service;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anota jugadores para avisarles si se libera una cancha en un horario lleno.
 *
 * <p>Un {@link WaitlistNotificationWorker} corre cada minuto y revisa contra
 * {@link AvailabilityService} si algun horario anotado ya tiene cancha libre, en
 * vez de que este servicio dispare el aviso en el momento de la cancelacion: hay
 * demasiados caminos por los que un turno se libera (el jugador cancela, el club lo
 * da de baja, un turno fijo salta una fecha, vence sin pago) como para instrumentar
 * cada uno sin riesgo de olvidarse alguno.
 *
 * <p>Pide sesion de jugador ({@link PlayerAccount}) y no solo nombre y telefono
 * como antes: el aviso necesita un mail al que caer cuando el WhatsApp del club
 * esta apagado o el envio real falla (ver {@link WaitlistNotificationWorker}), y
 * un invitado sin cuenta no tiene uno confiable que ofrecer.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final CustomerService customerService;
    private final AvailabilityService availabilityService;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    @Transactional
    public WaitlistEntry join(Tenant club, Instant startTime, String phone, String fullName,
                              PlayerAccount account) {
        SlotGenerator.ResolvedPlan resolved = slotGenerator.resolveWithPlan(club, startTime)
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese horario no forma parte de la grilla del club"));
        SlotGenerator.ResolvedSlot slot = resolved.resolved();

        if (!slot.slot().startsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("Ese horario ya pasó");
        }
        if (availabilityService.anyCourtFree(resolved)) {
            throw new BusinessRuleException(
                    "Todavía hay canchas libres en ese horario: reservalo directo");
        }

        Customer customer = customerService.findOrCreate(phone, fullName, account.getId());

        Optional<WaitlistEntry> previous =
                waitlistEntryRepository.findByCustomerAndStartsAt(customer, slot.slot().startsAt());
        if (previous.isPresent()) {
            if (!previous.get().isNotified()) {
                throw new BusinessRuleException("Ya estás anotado para ese horario");
            }
            // Ya le avisaron y no llego a reservar: antes esto chocaba con "ya
            // estas anotado" y se quedaba sin el proximo aviso. Se borra la vieja
            // y se anota de nuevo, al final de la fila: el lugar que tenia ya lo
            // uso cuando le avisaron.
            waitlistEntryRepository.delete(previous.get());
            waitlistEntryRepository.flush();
        }

        WaitlistEntry entry = new WaitlistEntry();
        entry.setCustomer(customer);
        entry.setStartsAt(slot.slot().startsAt());
        entry.setEndsAt(slot.slot().endsAt());
        entry.setEmail(account.getEmail());
        entry.setPlayerAccountId(account.getId());

        try {
            return waitlistEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessRuleException("Ya estás anotado para ese horario");
            }
            throw ex;
        }
    }

    /**
     * Los anotados de un horario, tal como los revisa el club en el panel.
     *
     * @param courtFree si ya hay alguna cancha libre, o sea si tiene sentido
     *                  escribirles que se libero
     * @param entries   por orden de llegada: el primero que se anoto va primero
     */
    public record SlotWaitlist(Instant startsAt, Instant endsAt, boolean courtFree,
                               List<WaitlistEntry> entries) {
    }

    /** Horarios que todavia no empezaron y tienen gente anotada, el mas proximo primero. */
    @Transactional(readOnly = true)
    public List<SlotWaitlist> upcomingBySlot(Tenant club) {
        Map<Instant, List<WaitlistEntry>> bySlot = waitlistEntryRepository.findUpcoming(clock.instant())
                .stream()
                .collect(Collectors.groupingBy(WaitlistEntry::getStartsAt, LinkedHashMap::new,
                        Collectors.toList()));

        // Una sola consulta de turnos y bloqueos para todos los horarios de la
        // pantalla, en vez de una por horario: ver AvailabilityService#anyCourtFreeForSlots.
        List<AvailabilityService.SlotWindow> windows = bySlot.values().stream()
                .map(entries -> new AvailabilityService.SlotWindow(
                        entries.getFirst().getStartsAt(), entries.getFirst().getEndsAt()))
                .toList();
        Map<AvailabilityService.SlotWindow, Boolean> freeBySlot =
                availabilityService.anyCourtFreeForSlots(club, windows);

        return bySlot.values().stream()
                .map(entries -> {
                    WaitlistEntry first = entries.getFirst();
                    AvailabilityService.SlotWindow window =
                            new AvailabilityService.SlotWindow(first.getStartsAt(), first.getEndsAt());
                    return new SlotWaitlist(first.getStartsAt(), first.getEndsAt(),
                            freeBySlot.get(window), entries);
                })
                .toList();
    }

    /** Las anotaciones del jugador en todos los clubes, de horarios que todavia no empezaron. */
    @Transactional(readOnly = true)
    public List<WaitlistEntryRepository.PlayerWaitlistRow> forAccount(UUID accountId) {
        return waitlistEntryRepository.findUpcomingForAccount(accountId, clock.instant());
    }

    /** El jugador se baja de la lista desde "Mis turnos". */
    @Transactional
    public void leave(UUID accountId, UUID entryId) {
        if (waitlistEntryRepository.deleteForAccount(entryId, accountId) == 0) {
            throw new ResourceNotFoundException("Esa anotación ya no está en la lista de espera");
        }
    }

    private boolean isDuplicate(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("ux_waitlist_slot")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
