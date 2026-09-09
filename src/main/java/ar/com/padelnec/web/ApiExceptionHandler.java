package ar.com.padelnec.web;

import ar.com.padelnec.payment.PaymentGatewayException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores a algo que la app pueda mostrarle al jugador.
 *
 * <p>Los mensajes de {@link BusinessRuleException} estan escritos para leerse tal
 * cual en pantalla. Cualquier otra excepcion se responde en generico: el detalle
 * va al log, no al navegador.
 */
@RestControllerAdvice(basePackages = "ar.com.padelnec.web")
@Slf4j
public class ApiExceptionHandler {

    /** Codigo con el que la app sabe que tiene que refrescar la grilla. */
    private static final String SLOT_TAKEN = "SLOT_TAKEN";

    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<Map<String, Object>> onSlotTaken(SlotUnavailableException ex) {
        // 409: no es culpa del jugador, es que alguien llego primero.
        return body(HttpStatus.CONFLICT, ex.getMessage(), SLOT_TAKEN);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> onBusinessRule(BusinessRuleException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "RULE_VIOLATION");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(UnauthorizedSessionException.class)
    public ResponseEntity<Map<String, Object>> onUnauthorizedSession(UnauthorizedSessionException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), "SESSION_EXPIRED");
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, Object>> onGateway(PaymentGatewayException ex) {
        return body(HttpStatus.BAD_GATEWAY, ex.getMessage(), "PAYMENT_GATEWAY");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Revisá los datos del formulario");
        return body(HttpStatus.BAD_REQUEST, message, "INVALID_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onUnexpected(Exception ex) {
        log.error("Error no contemplado en la API publica", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Tuvimos un problema procesando tu pedido. Probá de nuevo en un momento.",
                "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now());
        payload.put("status", status.value());
        payload.put("code", code);
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
