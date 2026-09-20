package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Un local del gimnasio. Un club con un solo gimnasio tiene una sede; uno con
 * dos gimnasios que comparten membresia tiene dos, y cada ingreso queda
 * registrado con la suya.
 */
@Entity
@Table(name = "gym_sede")
@Getter
@Setter
public class GymSede extends TenantScopedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 200)
    private String address;

    /**
     * Lo que codifica el QR impreso en la puerta. Es un secreto estatico -- quien
     * fotografie el cartel lo tiene -- y por eso se puede regenerar. Va en claro
     * y no como huella porque el panel tiene que poder volver a dibujar el QR.
     */
    @Column(name = "qr_token", nullable = false, length = 64)
    private String qrToken;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Donde queda la sede. Sin coordenadas, no se verifica la ubicacion del socio. */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** A cuantos metros de la sede vale registrar el ingreso; holgado porque el GPS falla adentro. */
    @Column(name = "radius_meters", nullable = false)
    private int radiusMeters = DEFAULT_RADIUS_METERS;

    public static final int DEFAULT_RADIUS_METERS = 200;

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** Porcentaje de lo atribuido a esta sede que le toca a un socio externo (0 = toda del club). */
    @Column(name = "partner_share_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal partnerSharePct = BigDecimal.ZERO;
}
