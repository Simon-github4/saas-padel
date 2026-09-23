package ar.com.padelnec.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    @DisplayName("Detras de Cloudflare manda CF-Connecting-IP, aunque el cliente mande Forwarded o X-Forwarded-For")
    void cloudflareHeaderWinsOverClientSuppliedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.226.90.65");
        request.addHeader("Forwarded", "for=6.6.6.6");
        request.addHeader("X-Forwarded-For", "7.7.7.7, 81.97.145.24, 172.71.195.123");
        request.addHeader("CF-Connecting-IP", " 81.97.145.24 ");

        assertThat(ClientIp.of(request)).isEqualTo("81.97.145.24");
    }

    @Test
    @DisplayName("Sin Cloudflare adelante (local, tests) queda la direccion del pedido")
    void withoutCloudflareFallsBackToTheRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThat(ClientIp.of(request)).isEqualTo("127.0.0.1");

        request.addHeader("CF-Connecting-IP", "  ");
        assertThat(ClientIp.of(request)).isEqualTo("127.0.0.1");
    }
}
