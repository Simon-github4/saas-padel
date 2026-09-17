package ar.com.padelnec.web;

import ar.com.padelnec.payment.MercadoPagoOAuthService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe la redireccion de MercadoPago despues de que el dueno autoriza (o
 * rechaza) la conexion de su club.
 *
 * <p>Llega sin sesion del panel: el navegador vino y volvio de
 * {@code auth.mercadopago.com}, asi que esta bajo {@code /api/**} y no bajo
 * {@code /admin/**} (ver {@code SecurityConfig#publicApiChain}). Lo unico que
 * autoriza el intercambio es el {@code state} firmado que ata al club, no una
 * sesion activa.
 */
@RestController
@RequestMapping("/api/mercadopago/oauth")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoOAuthController {

    private final MercadoPagoOAuthService oauthService;

    /**
     * Siempre redirige de vuelta al panel, nunca devuelve un error crudo: quien
     * llega aca es el navegador del dueno, no un cliente de API.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        boolean ok;
        if (error != null) {
            log.warn("El dueno no completo la autorizacion de MercadoPago: {}", error);
            ok = false;
        } else if (code == null || code.isBlank() || state == null || state.isBlank()) {
            ok = false;
        } else {
            try {
                ok = oauthService.completeAuthorization(code, state);
            } catch (RuntimeException ex) {
                log.error("Fallo el intercambio OAuth de MercadoPago", ex);
                ok = false;
            }
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/admin/configuracion?mp=" + (ok ? "ok" : "error")))
                .build();
    }
}
