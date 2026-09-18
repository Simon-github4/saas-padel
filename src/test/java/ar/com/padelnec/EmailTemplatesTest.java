package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.notification.EmailMessage;
import ar.com.padelnec.notification.EmailTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailTemplatesTest {

    private static final String LINK = "https://turnospadel.com.ar/reset-password/abc_DEF-123";

    @Test
    @DisplayName("El alta lleva el codigo y el link en el texto y en el HTML")
    void signupCarriesCodeAndLinkInBothBodies() {
        EmailMessage mail = EmailTemplates.signupCode("482913", LINK);

        assertThat(mail.subject()).isEqualTo("Confirmá tu cuenta");
        assertThat(mail.text()).contains("482913", LINK);
        assertThat(mail.html()).contains("482913", "href=\"" + LINK + "\"", "Confirmar mi cuenta");
    }

    @Test
    @DisplayName("El reset lleva el boton y el link escrito para copiar")
    void resetCarriesButtonAndFallbackLink() {
        EmailMessage mail = EmailTemplates.passwordReset(LINK);

        assertThat(mail.subject()).isEqualTo("Recuperar contraseña");
        assertThat(mail.text()).contains(LINK, "Vale por 1 hora");
        assertThat(mail.html()).contains("Elegir contraseña nueva");
        // Una vez en el boton y otra escrito, para cuando el boton no anda.
        assertThat(mail.html().split("href=\"" + LINK + "\"", -1)).hasSize(3);
    }

    @Test
    @DisplayName("Un nombre con HTML no se mete en el mail: se escapa")
    void escapesNamesComingFromOutside() {
        EmailMessage mail = EmailTemplates.waitlistSlotFreed(
                "<i>Ana</i>", "Club <b>Norte</b> & Co", "sábado 20 de septiembre", "21:00", LINK);

        assertThat(mail.html())
                .contains("Club &lt;b&gt;Norte&lt;/b&gt; &amp; Co", "&lt;i&gt;Ana&lt;/i&gt;")
                .doesNotContain("<b>Norte</b>", "<i>Ana</i>");
        // El asunto y el texto no son HTML: van tal cual.
        assertThat(mail.subject()).isEqualTo("Se liberó un turno en Club <b>Norte</b> & Co");
    }

    @Test
    @DisplayName("Las tildes viajan como letras, no como entidades")
    void keepsAccentsAsText() {
        EmailMessage mail = EmailTemplates.waitlistSlotFreed("Íñigo", "Pádel Norte", "sábado 20", "21:00", LINK);

        assertThat(mail.html()).contains("Íñigo", "Pádel Norte", "sábado 20").doesNotContain("&aacute;");
    }
}
