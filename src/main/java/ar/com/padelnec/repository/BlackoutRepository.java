package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Blackout;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlackoutRepository extends JpaRepository<Blackout, UUID> {

    @Query("""
            SELECT b FROM Blackout b
            LEFT JOIN FETCH b.court
            WHERE b.startTime < :until AND b.endTime > :from
            """)
    List<Blackout> findOverlapping(@Param("from") Instant from, @Param("until") Instant until);
}
