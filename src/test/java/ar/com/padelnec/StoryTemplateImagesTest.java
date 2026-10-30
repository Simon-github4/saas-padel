package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.StoryTemplateImages;
import ar.com.padelnec.support.StoryTemplateImages.Image;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La plantilla de la historia se guarda del tamaño que se usa, no del que se sube. */
class StoryTemplateImagesTest {

    @Test
    @DisplayName("Una foto grande queda en JPG cubriendo justo 1080 x 1920, sin recortar")
    void shrinksBigPhoto() throws IOException {
        byte[] png = encode(noise(3000, 4000), "png");

        Image result = StoryTemplateImages.shrink(png, "image/png");

        BufferedImage read = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        // Escala por el lado que mas aprieta (el alto): el ancho sobra y lo recorta el dibujo.
        assertThat(read.getHeight()).isEqualTo(1920);
        assertThat(read.getWidth()).isEqualTo(1440);
        assertThat(result.data().length).isLessThan(png.length);
    }

    @Test
    @DisplayName("Una foto apaisada mantiene el alto de la historia")
    void landscapeKeepsHeight() throws IOException {
        Image result = StoryTemplateImages.shrink(encode(noise(4000, 2000), "png"), "image/png");

        BufferedImage read = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertThat(read.getHeight()).isEqualTo(1920);
        assertThat(read.getWidth()).isEqualTo(3840);
    }

    @Test
    @DisplayName("Un JPG que ya tiene el tamaño de la historia se guarda tal cual")
    void keepsJpegThatAlreadyFits() throws IOException {
        byte[] jpeg = encode(noise(1080, 1920), "jpg");

        Image result = StoryTemplateImages.shrink(jpeg, "image/jpeg");

        assertThat(result.data()).isSameAs(jpeg);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Lo que Java no sabe leer (como WEBP) queda como se subio")
    void keepsWhatItCannotRead() {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        Image result = StoryTemplateImages.shrink(webp, "image/webp");

        assertThat(result.data()).isSameAs(webp);
        assertThat(result.contentType()).isEqualTo("image/webp");
    }

    /** Ruido y no un color liso: un liso comprime a casi nada y no prueba el achique. */
    private static BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < height; y += 40) {
            for (int x = 0; x < width; x += 40) {
                g.setColor(new Color(random.nextInt(0xFFFFFF)));
                g.fillRect(x, y, 40, 40);
            }
        }
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
