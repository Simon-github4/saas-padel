package ar.com.padelnec.gym.web;

import ar.com.padelnec.gym.service.LocationRequiredException;
import ar.com.padelnec.gym.service.PasswordChangeRequiredException;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.UnauthorizedSessionException;
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
 * Los errores de la API del gimnasio, con la misma forma que los de padel
 * ({@code timestamp, status, code, message}) para que la app los lea igual.
 *
 * <p>Es una copia acotada a este paquete, no una ampliacion del handler de
 * padel: asi el modulo no obliga a tocar codigo que no es suyo. Los mensajes de
 * {@link BusinessRuleException} estan escritos para leerse tal cual en pantalla.
 */
@RestControllerAdvice(basePackages = "ar.com.padelnec.gym.web")
@Slf4j
public class GymExceptionHandler {

    /** Mas especifica que la de abajo: es una regla de negocio, pero la app la trata distinto (permiso de ubicacion). */
    @ExceptionHandler(LocationRequiredException.class)
    public ResponseEntity<Map<String, Object>> onLocationRequired(LocationRequiredException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "LOCATION_REQUIRED");
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

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<Map<String, Object>> onPasswordChangeRequired(PasswordChangeRequiredException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage(), "PASSWORD_CHANGE_REQUIRED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Revisá los datos del formulario");
        return body(HttpStatus.BAD_REQUEST, message, "INVALID_REQUEST");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> onBadParameter(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Revisá los datos del pedido.", "INVALID_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onUnexpected(Exception ex) {
        log.error("Error no contemplado en la API del gimnasio", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Tuvimos un problema procesando tu pedido. Probá de nuevo en un momento.", "INTERNAL_ERROR");
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
