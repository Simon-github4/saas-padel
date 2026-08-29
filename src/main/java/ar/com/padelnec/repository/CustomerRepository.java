package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    List<Customer> findAllByOrderByFullNameAsc();
}
