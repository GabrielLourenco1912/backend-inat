package dev.lourencogabriel.inatead.web.exception;

import dev.lourencogabriel.inatead.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.inatead.auth.exception.PasswordResetUnavailableException;
import dev.lourencogabriel.inatead.document.storage.FileScanUnavailableException;
import dev.lourencogabriel.inatead.document.storage.FileStorageException;
import dev.lourencogabriel.inatead.document.storage.FileTooLargeException;
import dev.lourencogabriel.inatead.mail.EmailDeliveryException;
import dev.lourencogabriel.inatead.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.inatead.user.exception.PersonAlreadyLinkedToUserException;
import dev.lourencogabriel.inatead.user.exception.PersonNotFoundForRegistrationException;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.UnexpectedTypeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessRule(BusinessRuleException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(ResourceConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PersonNotFoundForRegistrationException.class)
    public ResponseEntity<ApiResponse<Object>> handleRegistrationPersonNotFound(
            PersonNotFoundForRegistrationException ex
    ) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(PersonAlreadyLinkedToUserException.class)
    public ResponseEntity<ApiResponse<Object>> handlePersonAlreadyLinked(
            PersonAlreadyLinkedToUserException ex
    ) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({FileTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiResponse<Object>> handleFileTooLarge(Exception ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds maximum allowed size");
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Object>> handleFileStorage(FileStorageException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "File storage operation failed");
    }

    @ExceptionHandler(FileScanUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleFileScanUnavailable(
            FileScanUnavailableException ex
    ) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailDelivery(EmailDeliveryException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingMultipartPart(
            MissingServletRequestPartException ex
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Required multipart part is missing: " + ex.getRequestPartName()
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Multipart request is invalid");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex
    ) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {

        var errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage()
                ))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleJsonError(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is invalid");
    }

    @ExceptionHandler(UnexpectedTypeException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationConfigError(UnexpectedTypeException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid validation annotation usage", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {

        var errors = ex.getConstraintViolations()
                .stream()
                .map(err -> Map.of(
                        "field", err.getPropertyPath().toString(),
                        "message", err.getMessage()
                ))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Constraint Violation", errors);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidAuthChallengeException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidAuthChallenge(
            InvalidAuthChallengeException ex
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handlePasswordResetUnavailable(
            PasswordResetUnavailableException ex
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwt(JwtException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();

        return buildResponse(status, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Request violates a database constraint");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Throwable ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(HttpStatus status, String message) {
        return buildResponse(status, message, null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(new ApiResponse<>(data, message, status));
    }
}
