package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un navegador de un usuario del panel que pidio los avisos de reservas nuevas.
 *
 * <p>Es lo que entrega el navegador al suscribirse ({@code PushSubscription} de Web
 * Push): la URL del servicio de push y las claves para cifrarle el contenido. No
 * depende de la sesion: el aviso tiene que llegar con el panel cerrado.
 */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
public class PushSubscription extends TenantScopedEntity {

    @Column(name = "club_user_id", nullable = false, updatable = false)
    private UUID clubUserId;

    /** URL del servicio de push del navegador (FCM, Mozilla, Apple...). Unica. */
    @Column(nullable = false, length = 1024, updatable = false)
    private String endpoint;

    /** Clave publica P-256 del navegador, en Base64 URL. */
    @Column(nullable = false, length = 120)
    private String p256dh;

    /** Secreto de autenticacion del navegador, en Base64 URL. */
    @Column(nullable = false, length = 40)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;
}
