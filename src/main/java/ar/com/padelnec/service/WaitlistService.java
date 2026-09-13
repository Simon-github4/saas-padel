package ar.com.padelnec.service;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        SlotGenerator.ResolvedSlot slot = slotGenerator.resolve(club, startTime)
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese horario no forma parte de la grilla del club"));

        if (!slot.slot().startsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("Ese horario ya pasó");
        }
        if (availabilityService.anyCourtFree(slot.slot().startsAt(), slot.slot().endsAt())) {
            throw new BusinessRuleException(
                    "Todavía hay canchas libres en ese horario: reservalo directo");
        }

        Customer customer = customerService.findOrCreate(phone, fullName, account.getId());

        WaitlistEntry entry = new WaitlistEntry();
        entry.setCustomer(customer);
        entry.setStartsAt(slot.slot().startsAt());
        entry.setEndsAt(slot.slot().endsAt());
        entry.setEmail(account.getEmail());

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
    public List<SlotWaitlist> upcomingBySlot() {
        Map<Instant, List<WaitlistEntry>> bySlot = waitlistEntryRepository.findUpcoming(clock.instant())
                .stream()
                .collect(Collectors.groupingBy(WaitlistEntry::getStartsAt, LinkedHashMap::new,
                        Collectors.toList()));
        return bySlot.values().stream()
                .map(entries -> {
                    WaitlistEntry first = entries.getFirst();
                    return new SlotWaitlist(first.getStartsAt(), first.getEndsAt(),
                            availabilityService.anyCourtFree(first.getStartsAt(), first.getEndsAt()),
                            entries);
                })
                .toList();
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
