package ar.com.padelnec.domain.enums;

/** Diseño de la portada del club en la app del jugador. */
public enum HeroVariant {
    /** La portada original: foto de fondo, título centrado y datos en columnas. */
    CLASSIC,
    /** Foto del club en badge circular sobre fondo oscuro con grilla de cancha. */
    SCOREBOARD,
    /** Foto de cancha partida con el texto al lado: vertical en mobile, horizontal en desktop. */
    COURT_SPLIT,
    /**
     * El logo del club en una placa chica, nunca mas grande que su tamaño real, sobre
     * una version desenfocada de si mismo. Para clubes que solo tienen el logo de
     * Instagram (unos 640 px): en las otras portadas se estira a pantalla completa y
     * se pixela.
     */
    LOGO
}
