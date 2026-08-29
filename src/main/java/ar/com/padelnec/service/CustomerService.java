package ar.com.padelnec.service;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PhoneNumbers phoneNumbers;

    /**
     * Busca al jugador por telefono dentro del club, o lo da de alta.
     *
     * <p>El nombre se refresca en cada reserva: si el jugador lo escribio distinto,
     * la version mas reciente es la que el club va a reconocer en el mostrador.
     */
    @Transactional
    public Customer findOrCreate(String rawPhone, String fullName) {
        String phone = phoneNumbers.normalize(rawPhone);
        String name = requireName(fullName);

        Customer customer = customerRepository.findByPhoneNumber(phone)
                .orElseGet(() -> {
                    Customer created = new Customer();
                    created.setPhoneNumber(phone);
                    return created;
                });

        if (customer.isBlocked()) {
            throw new BusinessRuleException(
                    "No podemos tomar la reserva online. Comunicate con el club.");
        }
        customer.setFullName(name);
        return customerRepository.save(customer);
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

    private String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Necesitamos tu nombre para reservar el turno");
        }
        String trimmed = fullName.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
