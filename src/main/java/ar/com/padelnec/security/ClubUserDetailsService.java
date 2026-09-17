package ar.com.padelnec.security;

import ar.com.padelnec.repository.ClubUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticacion del panel.
 *
 * <p>Se entra con el nombre de usuario o con el mail, lo que sea mas comodo de
 * recordar -ambos son unicos en toda la plataforma, asi que no hay ambiguedad
 * posible entre clubes.
 *
 * <p>La busqueda no pasa por el filtro de tenant a proposito: el login ocurre
 * antes de que exista un club en contexto, y el club se deduce del usuario que se
 * autentico, no al reves.
 *
 * <p>El limite de intentos ({@link RateLimitedAuthenticationProvider}) no va
 * aca a proposito: {@code loadUserByUsername} tambien lo llama directo
 * {@code DevAutoLoginFilter} en cada request sin sesion, no solo un submit
 * real del formulario -- contarlo aca agotaria el cupo en el primer segundo.
 */
@Service
@RequiredArgsConstructor
public class ClubUserDetailsService implements UserDetailsService {

    private final ClubUserRepository clubUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        return clubUserRepository.findByFullNameIgnoreCase(usernameOrEmail)
                .or(() -> clubUserRepository.findByEmailIgnoreCase(usernameOrEmail))
                .map(ClubUserPrincipal::of)
                // Mismo mensaje para usuario inexistente y clave equivocada: decir
                // cual de las dos fallo es contarle a un atacante que usuarios existen.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));
    }
}
