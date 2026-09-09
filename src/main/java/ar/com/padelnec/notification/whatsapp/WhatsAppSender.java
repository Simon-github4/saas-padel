package ar.com.padelnec.notification.whatsapp;

import java.util.List;

/**
 * Salida hacia WhatsApp.
 *
 * <p>Es una interfaz para que la logica de reservas no dependa de un proveedor: en
 * desarrollo los mensajes van al log y en produccion a Twilio, sin que el flujo de
 * reserva se entere.
 */
public interface WhatsAppSender {

    /** Nombre del adaptador, para dejarlo asentado en el log de notificaciones. */
    String providerName();

    /**
     * @param toE164     destinatario en formato E.164
     * @param template   plantilla aprobada a usar
     * @param variables  valores de la plantilla, en el orden en que se aprobo
     * @param plainBody  el mismo mensaje como texto, para los canales que lo admiten
     */
    SendResult send(String toE164, NotificationTemplate template, List<String> variables, String plainBody);

    /** Resultado del intento de envio. Nunca lanza: un WhatsApp caido no cancela un turno. */
    record SendResult(boolean delivered, boolean skipped, String providerMessageId, String error) {

        public static SendResult ok(String providerMessageId) {
            return new SendResult(true, false, providerMessageId, null);
        }

        public static SendResult failed(String error) {
            return new SendResult(false, false, null, error);
        }

        /** No se intento mandar nada -- el canal esta apagado, no caido. */
        public static SendResult skipped(String reason) {
            return new SendResult(false, true, null, reason);
        }
    }
}
