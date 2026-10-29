package com.moveandtransportsa.booking.exception;

import com.moveandtransportsa.booking.dto.response.ErrorEnvelope;
import com.moveandtransportsa.booking.exception.distance.ApiKeyNotConfiguredException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler that maps all application exceptions to a consistent ErrorEnvelope response.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(BookingNotFoundException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity
    // -------------------------------------------------------------------------

    @ExceptionHandler(SuburbNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleSuburbNotFound(SuburbNotFoundException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null);
    }

    @ExceptionHandler(VehicleTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleVehicleTypeMismatch(VehicleTypeMismatchException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null);
    }

    @ExceptionHandler(InactiveEntityException.class)
    public ResponseEntity<ErrorEnvelope> handleInactiveEntity(InactiveEntityException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null);
    }

    // -------------------------------------------------------------------------
    // 503 Service Unavailable
    // -------------------------------------------------------------------------

    @ExceptionHandler(PricingUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handlePricingUnavailable(PricingUnavailableException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorEnvelope.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorEnvelope.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return buildResponse(req, HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.BAD_REQUEST, "Request body is malformed or missing", null);
    }

    @ExceptionHandler(InvalidAddressException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidAddress(InvalidAddressException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.BAD_REQUEST, "Invalid address provided", null);
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.FORBIDDEN, "Access denied", null);
    }

    // -------------------------------------------------------------------------
    // 401 Unauthorized
    // -------------------------------------------------------------------------

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<ErrorEnvelope> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return buildResponse(req, HttpStatus.UNAUTHORIZED, "Authentication required", null);
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error (catch-all)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ApiKeyNotConfiguredException.class)
    public ResponseEntity<ErrorEnvelope> handleApiKeyNotConfigured(ApiKeyNotConfiguredException ex, HttpServletRequest req) {
        log.error("API configuration error at {}: Google Maps API key not configured", req.getRequestURI());
        return buildResponse(req, HttpStatus.INTERNAL_SERVER_ERROR, "Service configuration error", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(req, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorEnvelope> buildResponse(HttpServletRequest req, HttpStatus status,
                                                          String message, List<ErrorEnvelope.FieldError> errors) {
        ErrorEnvelope envelope = new ErrorEnvelope(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                errors
        );

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(envelope);
    }
}
