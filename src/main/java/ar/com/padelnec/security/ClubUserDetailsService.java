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
 * <p>La busqueda no pasa por el filtro de tenant a proposito: el login ocurre
 * antes de que exista un club en contexto, y el club se deduce del usuario que se
 * autentico, no al reves.
 */
@Service
@RequiredArgsConstructor
public class ClubUserDetailsService implements UserDetailsService {

    private final ClubUserRepository clubUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return clubUserRepository.findByEmailIgnoreCase(email)
                .map(ClubUserPrincipal::of)
                // Mismo mensaje para usuario inexistente y clave equivocada: decir
                // cual de las dos fallo es contarle a un atacante que emails existen.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));
    }
}
