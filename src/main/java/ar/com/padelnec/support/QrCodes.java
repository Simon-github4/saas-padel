package ar.com.padelnec.support;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Dibuja un QR como SVG, para mostrarlo en la compu y escanearlo con el celular.
 *
 * <p>Es el mismo dibujo que {@code GymQr}, repetido a proposito: el modulo de
 * gimnasio no comparte nada con padel fuera de su lista ({@code GymArchitectureTest}),
 * y veinte lineas no justifican agrandarla.
 */
public final class QrCodes {

    /** Borde blanco obligatorio para que los lectores encuentren el codigo (4 modulos). */
    private static final int QUIET_ZONE = 4;

    private QrCodes() {
    }

    /** El QR como {@code data:} URI de un SVG, listo para un {@code <img src>}. */
    public static String svgDataUri(String text) {
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg(text).getBytes(StandardCharsets.UTF_8));
    }

    static String svg(String text) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 0,
                    // UTF-8 explicito: el link lleva el mensaje con tildes, codificado.
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    // Nivel L: se escanea de una pantalla, y un link largo con mas
                    // correccion sale con modulos tan chicos que cuesta leerlo.
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L));
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
