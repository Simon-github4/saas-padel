package ar.com.padelnec.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.TenantHeroImage;
import ar.com.padelnec.repository.TenantHeroImageRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class TenantIconServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "gif"})
    void cropsTheCenterWithoutStretchingAndProducesExactPngSizes(String format) throws Exception {
        var source = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 1200, 600);
        graphics.setColor(Color.RED);
        graphics.fillRect(300, 0, 600, 600);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, format, bytes);
        for (int size : new int[] {180, 192, 512}) {
            byte[] png = TenantIconService.squarePng(bytes.toByteArray(), size);
            assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
            var icon = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(icon.getWidth()).isEqualTo(size);
            assertThat(icon.getHeight()).isEqualTo(size);
            Color center = new Color(icon.getRGB(size / 2, size / 2));
            assertThat(center.getRed()).isGreaterThan(240);
            assertThat(center.getBlue()).isLessThan(15);
            assertThat(new Color(icon.getRGB(5, 5)).getRed()).isGreaterThan(240);
        }
    }

    @Test
    void convertsAWebpCoverToPng() throws Exception {
        byte[] webp = new ClassPathResource("static/capturas/cargar-turno.webp").getContentAsByteArray();
        var icon = ImageIO.read(new ByteArrayInputStream(TenantIconService.squarePng(webp, 192)));
        assertThat(icon.getWidth()).isEqualTo(192);
        assertThat(icon.getHeight()).isEqualTo(192);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "0.0.0.0", "100.64.0.1", "198.18.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1"})
    void externalCoversCannotAccessPrivateNetworks(String host) {
        assertThatThrownBy(() -> TenantIconService.publicAddresses(host)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsInvalidImageData() {
        assertThatThrownBy(() -> TenantIconService.squarePng("not an image".getBytes(), 192))
                .isInstanceOf(IOException.class);
    }

    @Test
    void usesOnlyTheSelectedCoverAndDoesNotReadOtherClubs() throws Exception {
        var repository = mock(TenantHeroImageRepository.class);
        var service = new TenantIconService(repository);
        var club = new Tenant();
        club.setId(UUID.randomUUID());
        club.setSlug("los-troncos");
        club.setHeroImageUrl("/api/public/los-troncos/hero-image");
        var cover = new TenantHeroImage();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", bytes);
        cover.setData(bytes.toByteArray());
        when(repository.findById(club.getId())).thenReturn(Optional.of(cover));
        assertThat(ImageIO.read(new ByteArrayInputStream(service.icon(club, 192))).getWidth()).isEqualTo(192);

        club.setHeroImageUrl("/api/public/otro-club/hero-image");
        assertThatThrownBy(() -> service.icon(club, 192)).isInstanceOf(IOException.class);
        club.setHeroImageUrl("http://127.0.0.1/old-cover.png");
        assertThatThrownBy(() -> service.icon(club, 192)).isInstanceOf(IOException.class);
    }
}
