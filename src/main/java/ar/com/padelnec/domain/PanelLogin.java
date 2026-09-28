package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un ingreso (o intento de ingreso) al panel del club.
 *
 * <p>Bitacora como {@link PageEvent}: se escribe una vez y no se toca. No
 * extiende {@link TenantScopedEntity} por lo mismo que {@link ClubUser}: el
 * login ocurre antes de que haya un club en contexto.
 */
@Entity
@Table(name = "panel_login")
@Getter
@Setter
public class PanelLogin extends BaseEntity {

    public static final String OK = "OK";
    public static final String WRONG_PASSWORD = "CLAVE_INCORRECTA";
    public static final String LOCKED = "BLOQUEADO";
    public static final String DISABLED = "DESHABILITADO";

    @Column(name = "club_id", updatable = false)
    private UUID clubId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    /** El nombre del usuario al momento del ingreso, por si despues se borra. */
    @Column(name = "user_name", nullable = false, updatable = false, length = 120)
    private String userName;

    @Column(nullable = false, updatable = false, length = 20)
    private String result;

    @Column(updatable = false, length = 45)
    private String ip;

    /** {@code mobile} o {@code desktop}, deducido del User-Agent. */
    @Column(updatable = false, length = 10)
    private String device;

    @Column(name = "user_agent", updatable = false, length = 300)
    private String userAgent;
}
