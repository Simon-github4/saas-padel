package ar.com.padelnec.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    private final Google google = new Google();
    private final Mail mail = new Mail();

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
        /**
         * {@code off} (default): no manda nada, WhatsApp esta en stand by.
         * {@code log}: lo imprime en la consola, para desarrollo.
         * {@code twilio}: lo manda de verdad.
         */
        private String provider = "off";

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

    @Getter
    @Setter
    public static class Google {
        /**
         * Client ID de OAuth de Google Sign-In. Publico, no secreto -- tambien
         * viaja al frontend. Sin el, el login con Google simplemente no se ofrece.
         */
        private String clientId;
    }

    @Getter
    @Setter
    public static class Mail {
        /**
         * {@code smtp} en produccion, {@code log} para desarrollo local. Sin
         * default: si queda vacio, la app no arranca en vez de imprimir en el
         * log links que alcanzan para tomar una cuenta (ver {@code LoggingEmailSender}).
         *
         * <p>Validado con {@code @Pattern} y no solo dejado a que falte el bean de
         * {@code EmailSender}: sin esto, el arranque fallaba con un
         * {@code NoSuchBeanDefinitionException} dificil de interpretar para quien
         * despliega por primera vez.
         */
        @NotBlank(message = "app.mail.provider es obligatoria: 'log' o 'smtp'")
        @Pattern(regexp = "log|smtp", message = "app.mail.provider debe ser 'log' o 'smtp'")
        private String provider = "";

        /** Direccion que figura como remitente. */
        private String from = "no-responder@padelnec.com.ar";
    }
}
