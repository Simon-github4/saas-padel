package ar.com.padelnec.service;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantHeroImageRepository;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Adapta la portada seleccionada del club a un PNG cuadrado instalable. */
@Service
@RequiredArgsConstructor
public class TenantIconService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000;
    private final TenantHeroImageRepository images;

    public byte[] icon(Tenant club, int size) throws IOException {
        if (!Set.of(180, 192, 512).contains(size)) {
            throw new IllegalArgumentException("Unsupported icon size");
        }
        return squarePng(cover(club), size);
    }

    private byte[] cover(Tenant club) throws IOException {
        String url = club.getHeroImageUrl();
        if (url == null || url.isBlank()) {
            return new ClassPathResource("static/gym-app/icon-512.png").getContentAsByteArray();
        }
        // Solo la portada actualmente seleccionada; una subida anterior puede seguir en la base.
        if (url.equals("/api/public/" + club.getSlug() + "/hero-image")) {
            return images.findById(club.getId())
                    .orElseThrow(() -> new IOException("Club cover is missing"))
                    .getData();
        }
        try {
            return download(URI.create(url));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid cover URL", e);
        }
    }

    static byte[] squarePng(byte[] bytes, int size) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Cover exceeds size limit");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported cover image");
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Cover dimensions exceed limit");
                }
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.min(width, height) / size);
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, params);
                BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                var graphics = result.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, size, size);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side = Math.min(source.getWidth(), source.getHeight());
                    int x = (source.getWidth() - side) / 2;
                    int y = (source.getHeight() - side) / 2;
                    graphics.drawImage(source, 0, 0, size, size, x, y, x + side, y + side, null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(result, "png", output);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] download(URI uri) throws IOException {
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
                throw new IOException("Cover URL must be public HTTP(S)");
            }
            InetAddress[] addresses = publicAddresses(uri.getHost());
            // Conectar a las IP ya validadas evita una segunda resolucion DNS y DNS rebinding.
            try (var client = HttpClients.custom()
                    .setDnsResolver(host -> addresses)
                    .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(3000).setSocketTimeout(5000).build())
                    .build();
                 var response = client.execute(new HttpGet(uri))) {
                int status = response.getStatusLine().getStatusCode();
                if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                    var location = response.getFirstHeader("Location");
                    if (location == null) {
                        throw new IOException("Cover redirect without location");
                    }
                    uri = uri.resolve(location.getValue());
                    continue;
                }
                if (status != 200 || response.getEntity() == null) {
                    throw new IOException("Cover download failed");
                }
                try (var input = response.getEntity().getContent()) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) {
                        throw new IOException("Cover exceeds size limit");
                    }
                    return bytes;
                }
            }
        }
        throw new IOException("Too many cover redirects");
    }

    static InetAddress[] publicAddresses(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            byte[] raw = address.getAddress();
            int first = Byte.toUnsignedInt(raw[0]);
            int second = Byte.toUnsignedInt(raw[1]);
            boolean reserved = raw.length == 16
                    ? (first & 0xe0) != 0x20
                    : first == 0 || first >= 224
                        || (first == 100 && second >= 64 && second <= 127)
                        || (first == 198 && (second == 18 || second == 19));
            if (reserved || address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new UnknownHostException("Cover host is not public");
            }
        }
        return addresses;
    }
}
