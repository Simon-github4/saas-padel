package ar.com.padelnec.repository;

import ar.com.padelnec.domain.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findTop50ByOrderByCreatedAtDesc();

    List<NotificationLog> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
