package ar.com.padelnec.gym.domain;

/** Como se pago la cuota en el mostrador. */
public enum PayMethod {
    CASH("Efectivo"),
    TRANSFER("Transferencia");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
