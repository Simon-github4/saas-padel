package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Rastro de cada WhatsApp que sale del sistema.
 *
 * <p>Cuando un jugador dice que nunca le llego el link, esta tabla es la unica
 * forma de saber si el mensaje salio y que respondio el proveedor.
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
public class NotificationLog extends TenantScopedEntity {

    /** Se guarda el id suelto y no la relacion: el log sobrevive al borrado del turno. */
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "phone_number", nullable = false, length = 25)
    private String phoneNumber;

    @Column(nullable = false, length = 60)
    private String template;

    @Column(columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(columnDefinition = "text")
    private String error;
}
