package ar.com.padelnec.config;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuracion propia de la aplicacion, bajo el prefijo {@code app}. */
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    /**
     * URL publica desde la que el jugador accede a la app. Se usa para armar los
     * links de confirmacion y de gestion que viajan por WhatsApp, y como destino
     * de retorno del checkout de MercadoPago.
     */
    @NotBlank
    private String baseUrl = "http://localhost:8080";

    private final Security security = new Security();
    private final Whatsapp whatsapp = new Whatsapp();
    private final Jobs jobs = new Jobs();

    @Getter
    @Setter
    public static class Security {
        /**
         * Clave AES en Base64 con la que se cifran los secretos guardados en la base.
         * Se genera una sola vez con {@code openssl rand -base64 32}. Si se pierde,
         * los access token de MercadoPago de todos los clubes quedan ilegibles.
         */
        @NotBlank
        private String encryptionKey;
    }

    @Getter
    @Setter
    public static class Whatsapp {
        /** {@code twilio} en produccion, {@code log} para desarrollo local. */
        private String provider = "log";

        /** Numero emisor habilitado en el proveedor, en E.164. */
        private String fromNumber;

        private String accountSid;
        private String authToken;

        /**
         * Content SID de cada plantilla aprobada, indexado por el nombre del valor de
         * {@code NotificationTemplate}. Sin esto, los mensajes que inicia el sistema
         * no llegan fuera de la ventana de 24 horas.
         */
        private Map<String, String> templates = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Jobs {
        /** Permite apagar todos los jobs programados, util en entornos de prueba. */
        private boolean enabled = true;

        /** Semanas hacia adelante que se materializan de cada turno fijo. */
        private int recurringHorizonWeeks = 8;
    }
}
