package ar.com.padelnec.repository;

import ar.com.padelnec.domain.WaitlistEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    /** Anotados que todavia no recibieron el aviso de que se libero su horario. */
    @Query("""
            SELECT w FROM WaitlistEntry w
            JOIN FETCH w.customer
            WHERE w.notified = false
            ORDER BY w.startsAt ASC
            """)
    List<WaitlistEntry> findPending();
}
