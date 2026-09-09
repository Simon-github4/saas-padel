package ar.com.padelnec.notification;

/**
 * Salida hacia email.
 *
 * <p>Misma idea que {@link ar.com.padelnec.notification.whatsapp.WhatsAppSender}: la logica de autenticacion del
 * jugador no depende de un proveedor concreto. En desarrollo el mensaje va al
 * log y en produccion sale por SMTP, sin que el llamador se entere.
 */
public interface EmailSender {

    /** Nombre del adaptador, para dejarlo asentado en el log. */
    String providerName();

    SendResult send(String toAddress, String subject, String plainBody);

    /** Resultado del intento de envio. Nunca lanza: un mail caido no puede tumbar un registro. */
    record SendResult(boolean delivered, String error) {

        public static SendResult ok() {
            return new SendResult(true, null);
        }

        public static SendResult failed(String error) {
            return new SendResult(false, error);
        }
    }
}
