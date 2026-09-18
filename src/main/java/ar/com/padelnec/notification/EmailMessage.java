package ar.com.padelnec.notification;

/**
 * Un mail listo para mandar: el asunto, el texto y la misma cosa en HTML.
 *
 * <p>Viajan los dos cuerpos juntos (multipart/alternative): el cliente de mail
 * muestra el HTML si puede y el texto si no. El texto no es un resto: es lo que
 * ve quien tiene las vistas HTML apagadas, y un mail que llega solo en HTML
 * suma puntos para caer en spam.
 */
public record EmailMessage(String subject, String text, String html) {
}
