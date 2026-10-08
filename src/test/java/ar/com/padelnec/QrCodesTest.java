package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.QrCodes;
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

class QrCodesTest {

    @Test
    @DisplayName("Un lector de QR lee del dibujo exactamente el link de WhatsApp, con el mensaje entero")
    void theDrawnQrDecodesBackToTheLink() throws Exception {
        String link = "https://api.whatsapp.com/send?phone=17017145042&text=%C2%A1Hola!%20Quiero%20confirmar"
                + "%20mi%20n%C3%BAmero%20para%20reservar%20en%20P%C3%A1del%20Necochea.%20Mi%20c%C3%B3digo%20es%20483920";

        String dataUri = QrCodes.svgDataUri(link);

        assertThat(dataUri).startsWith("data:image/svg+xml;base64,");
        String svg = new String(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        assertThat(decodeQr(svg)).isEqualTo(link);
    }

    /** Vuelve a pintar los modulos del SVG en una grilla de pixeles y se la da a un lector de QR de verdad. */
    private static String decodeQr(String svg) throws Exception {
        Matcher viewBox = Pattern.compile("viewBox=\"0 0 (\\d+) \\d+\"").matcher(svg);
        assertThat(viewBox.find()).isTrue();
        int size = Integer.parseInt(viewBox.group(1));
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
}
