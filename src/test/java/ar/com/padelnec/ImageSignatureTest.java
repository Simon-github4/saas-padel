package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.ImageSignature;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Firma de archivo real contra content-type declarado: la app confia en esto, no en lo que dijo el navegador. */
class ImageSignatureTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
    private static final byte[] PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    private static final byte[] GIF87 = "GIF87a resto".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89 = "GIF89a resto".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = build("RIFF", "----", "WEBP");

    @Test
    @DisplayName("Cada firma matchea su propio content-type")
    void matchesItsOwnType() {
        assertThat(ImageSignature.matches(JPEG, "image/jpeg")).isTrue();
        assertThat(ImageSignature.matches(PNG, "image/png")).isTrue();
        assertThat(ImageSignature.matches(GIF87, "image/gif")).isTrue();
        assertThat(ImageSignature.matches(GIF89, "image/gif")).isTrue();
        assertThat(ImageSignature.matches(WEBP, "image/webp")).isTrue();
    }

    @Test
    @DisplayName("Una imagen real no matchea el content-type de otra")
    void rejectsAMismatchedType() {
        assertThat(ImageSignature.matches(JPEG, "image/png")).isFalse();
        assertThat(ImageSignature.matches(PNG, "image/webp")).isFalse();
        assertThat(ImageSignature.matches(WEBP, "image/gif")).isFalse();
    }

    @Test
    @DisplayName("Bytes que no son de ninguna imagen se rechazan aunque el navegador diga otra cosa")
    void rejectsNonImageBytesRegardlessOfClaimedType() {
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageSignature.matches(html, "image/png")).isFalse();
        assertThat(ImageSignature.matches(html, "image/jpeg")).isFalse();
        assertThat(ImageSignature.matches(html, "image/gif")).isFalse();
        assertThat(ImageSignature.matches(html, "image/webp")).isFalse();
    }

    @Test
    @DisplayName("Un archivo vacio o muy corto no revienta, solo no matchea")
    void handlesShortOrEmptyDataWithoutThrowing() {
        assertThat(ImageSignature.matches(new byte[0], "image/png")).isFalse();
        assertThat(ImageSignature.matches(new byte[] {1, 2}, "image/webp")).isFalse();
    }

    @Test
    @DisplayName("Un content-type que no esta en la lista permitida nunca matchea")
    void rejectsAnUnknownContentType() {
        assertThat(ImageSignature.matches(PNG, "image/svg+xml")).isFalse();
        assertThat(ImageSignature.matches(PNG, "text/html")).isFalse();
    }

    private static byte[] build(String... asciiChunks) {
        StringBuilder text = new StringBuilder();
        for (String chunk : asciiChunks) {
            text.append(chunk);
        }
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
