package ar.com.padelnec.payment;

import ar.com.padelnec.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Llamadas crudas al endpoint {@code /oauth/token} de MercadoPago.
 *
 * <p>HTTP a mano y no el SDK oficial: el SDK de MercadoPago (ver {@code MercadoPagoGateway})
 * no expone el flujo {@code authorization_code} con PKCE, solo Preferences y Payments.
 * Mismo cliente para el intercambio inicial y para la renovacion: los dos POSTean al
 * mismo endpoint, solo cambia el {@code grant_type} y que parametros manda.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoOAuthClient {

    private static final URI TOKEN_URI = URI.create("https://api.mercadopago.com/oauth/token");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    /** Lo que importa de la respuesta de {@code /oauth/token}. Ignora el resto (public_key, scope, etc). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("user_id") long userId,
            @JsonProperty("expires_in") long expiresInSeconds) {
    }

    /**
     * Intercambia el {@code code} de la redireccion por el primer access token del club.
     *
     * @param codeVerifier el mismo que genero {@code MercadoPagoOAuthService} al armar
     *                     la URL de autorizacion (RFC 7636): sin el, el code no sirve.
     */
    public TokenResponse exchangeCode(String code, String codeVerifier, String redirectUri) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", properties.getMercadopago().getClientId());
        body.put("client_secret", properties.getMercadopago().getClientSecret());
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        body.put("code_verifier", codeVerifier);
        return post(body);
    }

    /** Pide un access token y refresh token nuevos antes de que venzan los actuales. */
    public TokenResponse refresh(String refreshToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", properties.getMercadopago().getClientId());
        body.put("client_secret", properties.getMercadopago().getClientSecret());
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        return post(body);
    }

    private TokenResponse post(Map<String, String> body) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new PaymentGatewayException("No se pudo comunicar con MercadoPago");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("No se pudo comunicar con MercadoPago");
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("MercadoPago rechazo el intercambio OAuth: {} - {}",
                    response.statusCode(), response.body());
            throw new PaymentGatewayException(
                    "MercadoPago rechazo la conexion. Probá de nuevo desde Configuración.");
        }
        try {
            return objectMapper.readValue(response.body(), TokenResponse.class);
        } catch (JacksonException ex) {
            log.error("MercadoPago devolvio un cuerpo inesperado en /oauth/token: {}", response.body(), ex);
            throw new PaymentGatewayException("MercadoPago devolvio una respuesta inesperada");
        }
    }
}
