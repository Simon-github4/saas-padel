package ar.com.padelnec.payment;

/** MercadoPago no respondio o rechazo la operacion. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
