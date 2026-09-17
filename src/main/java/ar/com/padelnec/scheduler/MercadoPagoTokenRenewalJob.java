package ar.com.padelnec.scheduler;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido diario de tokens de MercadoPago por vencer.
 *
 * <p>El access token OAuth (y el refresh token que lo renueva) duran 180 dias
 * cada uno; este job corre bastante mas seguido que eso -y con margen de sobra
 * antes del vencimiento, ver {@code MercadoPagoOAuthService.RENEWAL_WINDOW}-
 * para que una falla transitoria de MercadoPago, o un despliegue que se salta
 * una corrida, no dejen a ningun club sin renovar antes de la fecha de corte.
 *
 * <p>Sin worker aparte: a diferencia de {@code BookingExpiryJob} o
 * {@code WaitlistNotificationJob}, la transaccion ya vive en
 * {@code MercadoPagoOAuthService#refresh}, un bean distinto -la llamada pasa
 * por el proxy de Spring igual, no hace falta separar la clase para eso.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoTokenRenewalJob {

    private final MercadoPagoOAuthService oauthService;

    @Scheduled(cron = "0 50 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void renewExpiringTokens() {
        for (Tenant club : oauthService.dueForRenewal()) {
            try {
                oauthService.refresh(club);
            } catch (RuntimeException ex) {
                // No hay alerta activa (mail, WhatsApp): el panel ya muestra la
                // conexion vencida via Tenant.mpConnectionExpired una vez que
                // pase la fecha, y hasta entonces quedan varios reintentos
                // diarios antes de que eso llegue a pasar.
                log.error("No se pudo renovar el token de MercadoPago del club {}",
                        club.getSlug(), ex);
            }
        }
    }
}
