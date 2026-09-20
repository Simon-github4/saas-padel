package ar.com.padelnec.gym.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El QR de la puerta: la URL que abre la app y hace el ingreso, y su dibujo.
 *
 * <p>Se dibuja como SVG y no como imagen: escala sin perder nitidez al imprimirlo
 * grande, y no hace falta {@code java.awt}.
 */
@Component
public class GymQr {

    /** Borde blanco obligatorio para que los lectores encuentren el codigo (4 modulos). */
    private static final int QUIET_ZONE = 4;

    private final String baseUrl;

    public GymQr(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Lo que lleva el QR: abre la app del club y le pasa el token de la sede. */
    public String checkInUrl(String clubSlug, String sedeToken) {
        return baseUrl + "/gym/" + clubSlug + "/in/" + sedeToken;
    }

    /** El QR como {@code data:} URI de un SVG, listo para un {@code <img src>}. */
    public String svgDataUri(String text) {
        String svg = svg(text);
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    String svg(String text) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 0,
                    // Nivel M: aguanta un cartel manchado o un poco gastado.
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException ex) {
            throw new IllegalStateException("No se pudo generar el QR", ex);
        }

        int size = matrix.getWidth() + QUIET_ZONE * 2;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x + QUIET_ZONE).append(',').append(y + QUIET_ZONE).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"" + size + "\" height=\"" + size + "\" fill=\"#fff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000\"/></svg>";
    }
}
