package ar.com.padelnec.repository;

import ar.com.padelnec.domain.RecurringBooking;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecurringBookingRepository extends JpaRepository<RecurringBooking, UUID> {

    @Query("""
            SELECT DISTINCT r FROM RecurringBooking r
            JOIN FETCH r.court
            JOIN FETCH r.customer
            LEFT JOIN FETCH r.skips
            WHERE r.active = true
            """)
    List<RecurringBooking> findAllActiveWithDetail();

    @Query("""
            SELECT DISTINCT r FROM RecurringBooking r
            JOIN FETCH r.court
            JOIN FETCH r.customer
            LEFT JOIN FETCH r.skips
            ORDER BY r.dayOfWeek ASC, r.startTime ASC
            """)
    List<RecurringBooking> findAllWithDetail();
}
