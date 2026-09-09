package ar.com.padelnec.security;

import ar.com.padelnec.config.AppProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Valida un ID token de Google contra las claves publicas de Google
 * (JWKS), sin pasar por el intercambio de codigo de autorizacion: el
 * frontend ya obtuvo el token con el SDK de Identity Services, aca solo se
 * confirma que la firma, el emisor y la audiencia sean legitimos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> VALID_ISSUERS = Set.of(
            "https://accounts.google.com", "accounts.google.com");

    private final AppProperties properties;

    private ConfigurableJWTProcessor<SecurityContext> processor;

    @PostConstruct
    void init() {
        try {
            // JWKSourceBuilder.create(URL) trae su propio timeout por default (500ms
            // conexion/lectura, via javap) pero es demasiado ajustado para una llamada
            // de red real - un retriever explicito deja margen sin dejar de tener limite.
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(3000, 3000);
            JWKSource<SecurityContext> keySource = JWKSourceBuilder
                    .create(URI.create(JWKS_URL).toURL(), retriever)
                    .build();
            JWSVerificationKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
            DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
            this.processor = jwtProcessor;
        } catch (java.net.MalformedURLException ex) {
            // La URL de Google es una constante fija: si esto llega a fallar, es un
            // error de programacion, no una condicion de runtime a manejar.
            throw new IllegalStateException("URL de JWKS de Google invalida", ex);
        }
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        String clientId = properties.getGoogle().getClientId();
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processor.process(idToken, null);
            if (!VALID_ISSUERS.contains(claims.getIssuer())) {
                return Optional.empty();
            }
            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(clientId)) {
                return Optional.empty();
            }
            boolean emailVerified = Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"));
            return Optional.of(new GoogleIdentity(
                    claims.getSubject(),
                    claims.getStringClaim("email"),
                    emailVerified,
                    claims.getStringClaim("name")));
        } catch (RuntimeException | java.text.ParseException | com.nimbusds.jose.JOSEException
                | com.nimbusds.jose.proc.BadJOSEException ex) {
            log.warn("No se pudo verificar el ID token de Google: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
