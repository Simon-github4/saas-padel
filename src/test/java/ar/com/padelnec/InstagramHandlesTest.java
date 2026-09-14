package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.support.InstagramHandles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El usuario de Instagram del club, cargado como le salga al dueño. */
class InstagramHandlesTest {

    @Test
    @DisplayName("Con @, sin @ o con mayusculas, queda el mismo usuario")
    void acceptsTheHandleHoweverItIsTyped() {
        assertThat(InstagramHandles.normalize("@ClubPadel")).contains("clubpadel");
        assertThat(InstagramHandles.normalize("  club.padel_necochea ")).contains("club.padel_necochea");
    }

    @Test
    @DisplayName("El link de Compartir perfil, con su ?igsh= atras, da el usuario")
    void extractsTheHandleFromAProfileLink() {
        assertThat(InstagramHandles.normalize("https://www.instagram.com/clubpadel?igsh=MWx0dW9n"))
                .contains("clubpadel");
        assertThat(InstagramHandles.normalize("instagram.com/ClubPadel/")).contains("clubpadel");
        assertThat(InstagramHandles.normalize("https://m.instagram.com/clubpadel/#")).contains("clubpadel");
    }

    @Test
    @DisplayName("Un posteo, un reel, otro sitio o un texto con espacios no son un usuario")
    void rejectsWhatIsNotAProfile() {
        assertThat(InstagramHandles.normalize("https://www.instagram.com/p/C8abc123/")).isEmpty();
        assertThat(InstagramHandles.normalize("https://www.instagram.com/reel/C8abc123/")).isEmpty();
        assertThat(InstagramHandles.normalize("https://www.facebook.com/clubpadel")).isEmpty();
        assertThat(InstagramHandles.normalize("club padel")).isEmpty();
        assertThat(InstagramHandles.normalize("@" + "a".repeat(31))).isEmpty();
        assertThat(InstagramHandles.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("El club arma siempre el mismo link al perfil, o ninguno si no cargo usuario")
    void tenantBuildsTheProfileLink() {
        Tenant club = new Tenant();
        assertThat(club.instagramUrl()).isNull();

        club.setInstagramHandle("clubpadel");
        assertThat(club.instagramUrl()).isEqualTo("https://www.instagram.com/clubpadel/");
    }
}
