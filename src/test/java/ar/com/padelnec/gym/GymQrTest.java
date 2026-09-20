package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.gym.service.GymQr;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GymQrTest {

    @Test
    @DisplayName("El QR lleva la URL del club con el token de la sede, sin barras de mas")
    void checkInUrlPointsAtTheClubApp() {
        assertThat(new GymQr("https://turnospadel.com.ar/").checkInUrl("los-troncos", "abc123"))
                .isEqualTo("https://turnospadel.com.ar/gym/los-troncos/in/abc123");
    }

    @Test
    @DisplayName("El dibujo es un SVG en un data URI, listo para un <img>")
    void drawsAnSvgDataUri() {
        GymQr qr = new GymQr("http://localhost:8080");

        String svg = decodeSvg(qr.svgDataUri(qr.checkInUrl("los-troncos", "abc123")));

        assertThat(svg).startsWith("<svg").contains("viewBox=").contains("<path d=\"M");
    }

    @Test
    @DisplayName("Un lector de QR lee del dibujo exactamente la URL que se codifico")
    void theDrawnQrDecodesBackToTheUrl() throws Exception {
        GymQr qr = new GymQr("https://turnospadel.com.ar");
        String url = qr.checkInUrl("los-troncos", "K6vILkjsfBNJC8Q6227ws3OcIYpIo3t61kmjFnE-8i8");

        String svg = decodeSvg(qr.svgDataUri(url));

        assertThat(decodeQr(svg)).isEqualTo(url);
    }

    private static String decodeSvg(String dataUri) {
        return new String(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
    }

    /** Vuelve a pintar los modulos del SVG en una grilla de pixeles y se la da a un lector de QR de verdad. */
    private static String decodeQr(String svg) throws Exception {
        int size = Integer.parseInt(find(svg, "viewBox=\"0 0 (\\d+) \\d+\""));
        int scale = 8;
        int width = size * scale;
        int[] pixels = new int[width * width];
        Arrays.fill(pixels, 0xFFFFFFFF);

        Matcher module = Pattern.compile("M(\\d+),(\\d+)h1v1h-1z").matcher(svg);
        while (module.find()) {
            int x0 = Integer.parseInt(module.group(1)) * scale;
            int y0 = Integer.parseInt(module.group(2)) * scale;
            for (int y = y0; y < y0 + scale; y++) {
                for (int x = x0; x < x0 + scale; x++) {
                    pixels[y * width + x] = 0xFF000000;
                }
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, width, pixels)));
        return new QRCodeReader().decode(bitmap).getText();
    }

    private static String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }
}
