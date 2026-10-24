package ar.com.padelnec.gym.domain;

/**
 * Como cuenta el club el tope de ingresos de los dias por semana. Por semana: de lunes a
 * domingo, tantos ingresos como dias. Por mes: en el periodo de la cuota del socio, los
 * dias por semana proporcionales a lo que dura el periodo (ver {@code GymAllowance}).
 */
public enum EntryLimit {
    WEEK("Por semana"),
    MONTH("Por mes");

    private final String label;

    EntryLimit(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
