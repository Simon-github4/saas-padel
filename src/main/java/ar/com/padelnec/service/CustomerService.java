package ar.com.padelnec.service;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PlayerAccountRepository playerAccountRepository;
    private final PhoneNumbers phoneNumbers;

    /**
     * Busca al jugador por telefono dentro del club, o lo da de alta con este nombre.
     *
     * <p>El nombre de un jugador que ya existe no lo cambia cualquiera que use su
     * telefono. Antes se refrescaba en cada reserva, y alcanzaba con equivocarse
     * un numero para pisarle el nombre a otro jugador en la agenda y en todo su
     * historial, tuviera cuenta o no. Ahora solo lo actualiza quien reserva con
     * sesion iniciada en la cuenta cuyo telefono es ese; cualquier otra
     * correccion la hace el club en Jugadores ({@link #rename}). Lo que escribio
     * cada uno queda igual en su turno ({@code Booking#bookedName}).
     *
     * @param accountId cuenta con sesion iniciada de quien reserva, o nulo si es
     *                  un invitado o lo carga el club
     */
    @Transactional
    public Customer findOrCreate(String rawPhone, String fullName, UUID accountId) {
        String phone = phoneNumbers.normalize(rawPhone);
        String name = cleanName(fullName);

        Optional<Customer> existing = customerRepository.findByPhoneNumber(phone);
        if (existing.isEmpty()) {
            Customer created = new Customer();
            created.setPhoneNumber(phone);
            created.setFullName(name);
            return customerRepository.save(created);
        }

        Customer customer = existing.get();
        if (customer.isBlocked()) {
            throw new BusinessRuleException(
                    "No podemos tomar la reserva online. Comunicate con el club.");
        }
        if (!customer.getFullName().equals(name) && ownsPhone(accountId, phone)) {
            customer.setFullName(name);
            customerRepository.save(customer);
        }
        return customer;
    }

    /**
     * La cuenta tiene cargado este telefono. No prueba que el numero sea suyo
     * -el telefono de la cuenta no se verifica-, pero es lo mas cerca de un
     * dueño que hay, y es mucho mas que un invitado que lo escribio.
     */
    private boolean ownsPhone(UUID accountId, String phone) {
        return accountId != null && playerAccountRepository.findById(accountId)
                .map(account -> phone.equals(account.getPhoneNumber()))
                .orElse(false);
    }

    /** El nombre tal como se guarda: sin espacios en los bordes y dentro del largo de la columna. */
    public String cleanName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Necesitamos tu nombre para reservar el turno");
        }
        String trimmed = fullName.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    /** El club corrige a mano el nombre de un jugador, ej. uno que quedo pisado antes de esta regla. */
    @Transactional
    public Customer rename(Customer customer, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("El nombre no puede quedar vacío");
        }
        customer.setFullName(cleanName(fullName));
        return customerRepository.save(customer);
    }

    /**
     * De estos telefonos, cuales tienen una cuenta de jugador.
     *
     * <p>Solo el si o el no: el mail de la cuenta es de la persona, no del club,
     * y cualquiera puede haber reservado alguna vez en este club con ese telefono.
     */
    @Transactional(readOnly = true)
    public Set<String> phonesWithAccount(Collection<String> phones) {
        if (phones.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(playerAccountRepository.findPhoneNumbersIn(phones));
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String rawPhone) {
        return customerRepository.findByPhoneNumber(phoneNumbers.normalize(rawPhone));
    }

    @Transactional(readOnly = true)
    public List<Customer> all() {
        return customerRepository.findAllByOrderByFullNameAsc();
    }

    /** Marca de confianza: habilita a reservar sin sena aunque el club exija pago. */
    @Transactional
    public Customer setTrusted(Customer customer, boolean trusted) {
        customer.setTrusted(trusted);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer setBlocked(Customer customer, boolean blocked) {
        customer.setBlocked(blocked);
        return customerRepository.save(customer);
    }

    @Transactional
    public void recordNoShow(Customer customer) {
        customer.setNoShowCount(customer.getNoShowCount() + 1);
        customerRepository.save(customer);
    }
}
