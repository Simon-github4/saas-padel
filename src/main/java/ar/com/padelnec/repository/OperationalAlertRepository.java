package ar.com.padelnec.repository;

import ar.com.padelnec.domain.OperationalAlert;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperationalAlertRepository extends JpaRepository<OperationalAlert, UUID> {

    @Query("""
            SELECT a FROM OperationalAlert a
            LEFT JOIN FETCH a.booking b
            LEFT JOIN FETCH b.customer
            WHERE a.resolved = false
            ORDER BY a.createdAt DESC
            """)
    List<OperationalAlert> findPending();

    long countByResolvedFalse();
}
