package ar.com.padelnec.security;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.enums.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado del panel.
 *
 * <p>Carga el club al que pertenece, que es lo que despues instala el
 * {@code TenantContext}: en el panel, el club sale siempre de la sesion y nunca de
 * un parametro de la pantalla, para que nadie pueda mirar la agenda de otro club
 * cambiando un id en la URL.
 */
public record ClubUserPrincipal(UUID userId, UUID clubId, String email, String fullName,
                                UserRole role, String passwordHash, boolean enabled)
        implements UserDetails {

    public static ClubUserPrincipal of(ClubUser user) {
        return new ClubUserPrincipal(
                user.getId(), user.getClubId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getPasswordHash(), user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** El dueno configura precios, horarios y usuarios; el mostrador solo opera la agenda. */
    public boolean canManageSettings() {
        return role == UserRole.OWNER || role == UserRole.SUPER_ADMIN;
    }
}
