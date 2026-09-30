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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        // El codigo sigue siendo el mismo para todas; el motivo va aparte, para no
        // romper a quien ya compara contra RULE_VIOLATION.
        ResponseEntity<Map<String, Object>> response =
                body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "RULE_VIOLATION");
        if (ex.getReason() != null) {
            response.getBody().put("reason", ex.getReason().name());
        }
        return response;
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
        var firstError = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String message = firstError
                .map(error -> error.getDefaultMessage())
                .orElse("Revisá los datos del formulario");
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.BAD_REQUEST, message, "INVALID_REQUEST");
        // El campo que fallo, para saber en la analitica si falto el nombre o el telefono.
        firstError.ifPresent(error -> response.getBody().put("field", error.getField()));
        return response;
    }

    /**
     * Un parametro de la URL con un valor que no existe ("?wall=madera", una fecha mal
     * escrita). Es un pedido mal armado, no una falla nuestra: 400 y sin ensuciar el
     * log de errores.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> onBadParameter(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Revisá los filtros del pedido.", "INVALID_REQUEST");
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
