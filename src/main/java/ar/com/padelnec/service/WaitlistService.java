package ar.com.padelnec.service;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
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

        Customer customer = customerService.findOrCreate(phone, fullName);

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
