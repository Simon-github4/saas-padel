package ar.com.padelnec.domain;

import ar.com.padelnec.support.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;

/** Club. Raiz del multi-tenant: no lleva club_id porque es el tenant. */
@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** Identificador legible usado en la URL publica, ej. "necochea-padel". */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    /** Telefono oficial del club, en E.164. Se usa para derivar consultas y reembolsos. */
    @Column(name = "whatsapp_number", nullable = false, length = 25)
    private String whatsappNumber;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "America/Argentina/Buenos_Aires";

    /** Token de produccion de MercadoPago. Cifrado en reposo. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mp_access_token", columnDefinition = "text")
    private String mpAccessToken;

    /** Clave secreta con la que MercadoPago firma los webhooks. Cifrada en reposo. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mp_webhook_secret", columnDefinition = "text")
    private String mpWebhookSecret;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(8, 0);

    /**
     * Hora de cierre. Si es menor o igual a openTime se interpreta que el club
     * cierra pasada la medianoche (ej. abre 08:00 y cierra 01:00).
     */
    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(23, 59);

    /** Duracion del turno en minutos. Define el tamano de los bloques de la grilla. */
    @Column(name = "default_slot_duration", nullable = false)
    private int defaultSlotDuration = 90;

    /** Antelacion minima, en horas, para que el jugador pueda cancelar desde la web. */
    @Column(name = "cancellation_limit_hours", nullable = false)
    private int cancellationLimitHours = 12;

    @Column(name = "deposit_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal depositPercentage = new BigDecimal("50.00");

    /** Si el club acepta reservas "de palabra" confirmadas por WhatsApp. */
    @Column(name = "allow_unpaid_booking", nullable = false)
    private boolean allowUnpaidBooking = true;

    /** Cuantos dias hacia adelante puede reservar el jugador. */
    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays = 21;

    /** Minutos que se sostiene un DRAFT esperando que acredite MercadoPago. */
    @Column(name = "draft_ttl_minutes", nullable = false)
    private int draftTtlMinutes = 10;

    /** Minutos que se sostiene un AWAITING_CONFIRMATION esperando el click de WhatsApp. */
    @Column(name = "confirmation_ttl_minutes", nullable = false)
    private int confirmationTtlMinutes = 15;

    /** Cuantos turnos futuros puede tener tomados un mismo telefono a la vez. */
    @Column(name = "max_active_bookings", nullable = false)
    private int maxActiveBookings = 3;

    @Column(nullable = false)
    private boolean active = true;

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    /** Verdadero cuando el horario de atencion cruza la medianoche. */
    public boolean closesAfterMidnight() {
        return !closeTime.isAfter(openTime);
    }

    public boolean acceptsOnlinePayments() {
        return mpAccessToken != null && !mpAccessToken.isBlank();
    }
}
