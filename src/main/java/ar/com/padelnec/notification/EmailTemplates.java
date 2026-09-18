package ar.com.padelnec.notification;

import org.springframework.web.util.HtmlUtils;

/**
 * Los mails que manda el sistema, cada uno en HTML y en texto.
 *
 * <p>HTML de mail y no de pagina: tablas y estilos en linea, porque Gmail y
 * Outlook ignoran las hojas de estilo y buena parte del CSS moderno. Sin
 * imagenes: muchos clientes las bloquean hasta que el usuario las acepta, y la
 * marca, el codigo y el boton tienen que verse igual. Fondo claro fijo: el
 * modo oscuro de cada cliente invierte colores a su manera, y un diseno claro
 * sobrevive mejor a esa inversion que uno oscuro.
 *
 * <p>Los textos planos son los de siempre, palabra por palabra: los tests sacan
 * de ahi el codigo y el link, como lo haria el jugador.
 *
 * <p>Todo lo que viene de afuera (nombres, links) pasa por {@link #esc}: el
 * nombre de un club o de un jugador no puede meter HTML en el mail.
 */
public final class EmailTemplates {

    /** El nombre del producto, como lo escribe la landing (dos tonos: Turnos + Padel). */
    public static final String BRAND = "TurnosPadel";

    private static final String INK = "#17140f";
    private static final String SOFT = "#57524a";
    private static final String MUTE = "#8a8377";
    private static final String ACCENT = "#ea580c";
    private static final String ACCENT_TEXT = "#c2410c";
    private static final String PAPER = "#f4f1ec";
    private static final String SAND = "#f6f3ee";
    private static final String LINE = "#e8e2d8";
    private static final String FONT = "Arial, Helvetica, sans-serif";

    private EmailTemplates() {
    }

    /** El alta: el codigo de 6 digitos y, como alternativa, el link. */
    public static EmailMessage signupCode(String code, String link) {
        String text = ("Tu código para confirmar la cuenta es %s (vale por 10 minutos).\n"
                + "También podés tocar este link: %s").formatted(code, link);
        String html = layout(
                "Tu código es " + code + ". Vale por 10 minutos.",
                "Confirmá tu cuenta",
                paragraph("Escribí este código en la pantalla donde te estás registrando. Vale por 10 minutos.")
                        + codeBox(code)
                        + paragraph("O confirmala directo desde acá:")
                        + button("Confirmar mi cuenta", link)
                        + fallbackLink(link),
                "Si no te estabas creando una cuenta en " + BRAND
                        + ", ignorá este mail: sin el código no se crea nada.");
        return new EmailMessage("Confirmá tu cuenta", text, html);
    }

    /** El link para elegir una contrasena nueva. */
    public static EmailMessage passwordReset(String link) {
        String text = ("Para elegir una contraseña nueva, tocá este link: %s\nVale por 1 hora. "
                + "Si no lo pediste vos, ignorá este mensaje.").formatted(link);
        String html = layout(
                "Tocá el botón para elegir una contraseña nueva. Vale por 1 hora.",
                "Elegí una contraseña nueva",
                paragraph("Nos pediste cambiar la contraseña de tu cuenta. Tocá el botón para elegir una nueva.")
                        + button("Elegir contraseña nueva", link)
                        + note("El link vale por 1 hora y sirve una sola vez.")
                        + fallbackLink(link),
                "Si no lo pediste vos, ignorá este mail: tu contraseña sigue siendo la misma.");
        return new EmailMessage("Recuperar contraseña", text, html);
    }

    /**
     * El aviso de la lista de espera: se libero el horario en el que se anoto.
     *
     * @param date y {@code time} ya formateados en la zona del club.
     */
    public static EmailMessage waitlistSlotFreed(String firstName, String clubName, String date, String time,
                                                 String link) {
        String text = """
                Hola %s, se liberó un turno en %s el %s a las %s hs.
                Reservalo antes de que se lo lleve otro: %s"""
                .formatted(firstName, clubName, date, time, link);
        String greeting = firstName == null || firstName.isBlank() ? "Hola," : "Hola " + firstName + ",";
        String html = layout(
                "El " + date + " a las " + time + " hs en " + clubName + ". Reservalo antes que otro.",
                "Se liberó un turno",
                paragraph(greeting + " se liberó una cancha en el horario en el que te anotaste en la lista de espera.")
                        + details(new String[][] {
                                {"Club", clubName},
                                {"Día", date},
                                {"Hora", time + " hs"},
                        })
                        + button("Reservar ahora", link)
                        + note("Le avisamos a todos los anotados en ese horario: se lo queda el primero que reserva.")
                        + fallbackLink(link),
                "Te llegó este mail porque te anotaste en la lista de espera de " + clubName + " en " + BRAND + ".");
        return new EmailMessage("Se liberó un turno en " + clubName, text, html);
    }

    // --------------------------------------------------------------- piezas

    /**
     * Escapa lo que el HTML interpreta ({@code < > & " '}) y nada mas: en UTF-8
     * las tildes y la enie viajan tal cual, sin volverse entidades.
     */
    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    /**
     * El marco comun: la marca arriba, la tarjeta blanca con el contenido y la
     * letra chica abajo. El preheader es el texto que el cliente muestra al lado
     * del asunto en la bandeja; va escondido adentro del mail.
     */
    private static String layout(String preheader, String title, String content, String footer) {
        return """
                <!doctype html>
                <html lang="es">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:%s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:%s;">
                <tr><td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;">
                <tr><td style="padding:0 6px 18px;font-family:%s;font-size:24px;font-weight:800;letter-spacing:-0.5px;color:%s;">Turnos<span style="color:%s;">Padel</span></td></tr>
                <tr><td style="background:#ffffff;border:1px solid %s;border-radius:20px;padding:36px 32px;font-family:%s;color:%s;">
                <h1 style="margin:0 0 14px;font-family:%s;font-size:24px;line-height:1.25;font-weight:800;color:%s;">%s</h1>
                %s
                </td></tr>
                <tr><td style="padding:20px 6px 0;font-family:%s;font-size:12px;line-height:1.6;color:%s;">%s<br>%s · Reservas de pádel online</td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                esc(title), PAPER, esc(preheader), PAPER,
                FONT, INK, ACCENT,
                LINE, FONT, INK,
                FONT, INK, esc(title),
                content,
                FONT, MUTE, esc(footer), BRAND);
    }

    private static String paragraph(String text) {
        return "<p style=\"margin:0 0 16px;font-family:%s;font-size:15px;line-height:1.6;color:%s;\">%s</p>"
                .formatted(FONT, SOFT, esc(text));
    }

    private static String note(String text) {
        return "<p style=\"margin:0 0 8px;font-family:%s;font-size:13px;line-height:1.5;color:%s;\">%s</p>"
                .formatted(FONT, MUTE, esc(text));
    }

    /** El codigo grande y espaciado, para copiarlo de un vistazo. */
    private static String codeBox(String code) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px;">
                <tr><td align="center" style="background:%s;border-radius:14px;padding:18px 12px;font-family:'Courier New', Courier, monospace;font-size:34px;font-weight:700;letter-spacing:10px;color:%s;">%s</td></tr>
                </table>
                """.formatted(SAND, INK, esc(code));
    }

    /**
     * Boton "a prueba de balas": una celda con fondo y el link adentro. Un
     * {@code <a>} con padding solo no se ve como boton en Outlook.
     */
    private static String button(String label, String link) {
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 20px;">
                <tr><td align="center" bgcolor="%s" style="border-radius:999px;background:%s;">
                <a href="%s" target="_blank" style="display:inline-block;padding:14px 30px;font-family:%s;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:999px;">%s</a>
                </td></tr>
                </table>
                """.formatted(ACCENT, ACCENT, esc(link), FONT, esc(label));
    }

    /** El link escrito, para cuando el boton no anda (clientes viejos, copiar y pegar). */
    private static String fallbackLink(String link) {
        return """
                <p style="margin:20px 0 0;padding-top:18px;border-top:1px solid %s;font-family:%s;font-size:12px;line-height:1.5;color:%s;">Si el botón no funciona, copiá este link en el navegador:<br><a href="%s" target="_blank" style="color:%s;word-break:break-all;">%s</a></p>
                """.formatted(LINE, FONT, MUTE, esc(link), ACCENT_TEXT, esc(link));
    }

    /** Filas rotulo / valor, para los datos de un turno. */
    private static String details(String[][] rows) {
        StringBuilder html = new StringBuilder(
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                        + "style=\"margin:4px 0 24px;background:" + SAND + ";border-radius:14px;\">");
        for (String[] row : rows) {
            html.append("""
                    <tr><td style="padding:12px 18px;font-family:%s;font-size:12px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:%s;">%s</td>\
                    <td align="right" style="padding:12px 18px;font-family:%s;font-size:15px;font-weight:700;color:%s;">%s</td></tr>
                    """.formatted(FONT, MUTE, esc(row[0]), FONT, INK, esc(row[1])));
        }
        return html.append("</table>").toString();
    }
}
