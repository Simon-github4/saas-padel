package ar.com.padelnec.gym.domain;

/**
 * Cuanto dura una cuota. La mensual sigue el ciclo del socio (su dia de corte); el pase
 * de 15 dias vale 15 dias calendario desde que arranca y vence sin generar deuda.
 */
public enum PlanLength {
    MONTH("Mensual"),
    FIFTEEN_DAYS("15 días");

    /** Los dias calendario de un pase, contando el primero y el ultimo. */
    public static final int PASS_DAYS = 15;

    private final String label;

    PlanLength(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
