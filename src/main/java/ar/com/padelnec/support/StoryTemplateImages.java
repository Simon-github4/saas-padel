package ar.com.padelnec.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Deja la plantilla de la historia del tamaño que de verdad se usa.
 *
 * <p>La historia mide 1080 x 1920, pero lo que suben los clubes suele ser una foto
 * del celular o un export de Canva de varios MB, y el panel la baja entera cada vez
 * que arma una historia, muchas veces desde el celular con datos. Achicada para
 * cubrir justo ese tamaño (sin recortar: el recorte lo hace el dibujo) y pasada a
 * JPG queda en unos cientos de KB y se ve igual.
 *
 * <p>Si no se puede leer (WEBP, que Java no trae, o un JPG en CMYK) se guarda tal
 * cual: el navegador si la sabe dibujar, solo que pesa mas.
 */
public final class StoryTemplateImages {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;
    private static final float QUALITY = 0.9f;

    public record Image(byte[] data, String contentType) {
    }

    public static Image shrink(byte[] data, String contentType) {
        Image original = new Image(data, contentType);
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return original;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                double scale = Math.min(1, Math.max((double) WIDTH / width, (double) HEIGHT / height));
                if (scale == 1 && "image/jpeg".equals(contentType)) {
                    return original;
                }
                int targetWidth = (int) Math.round(width * scale);
                int targetHeight = (int) Math.round(height * scale);

                // Leyendo de a un pixel cada tantos: una foto de 12 MP ocupa unos 50 MB
                // descomprimida, y el servidor tiene poca memoria. Lo salteado nunca baja
                // del tamaño final; lo que falta lo achica el dibujo de abajo, suavizado.
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.min(width / targetWidth, height / targetHeight));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage source = reader.read(0, param);

                BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = target.createGraphics();
                try {
                    // Lo transparente queda negro, igual que en el JPG que arma el panel.
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, targetWidth, targetHeight);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    g.dispose();
                }
                byte[] jpeg = toJpeg(target);
                // Achicada va siempre, aunque el JPG pese mas que un PNG de colores lisos: el
                // navegador igual tendria que descomprimir la grande entera. Sin achicar, solo
                // si pasarla a JPG la aliviana.
                return scale < 1 || jpeg.length < data.length ? new Image(jpeg, "image/jpeg") : original;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return original;
        }
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private StoryTemplateImages() {
    }
}
