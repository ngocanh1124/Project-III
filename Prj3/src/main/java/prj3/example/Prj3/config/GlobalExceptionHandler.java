package prj3.example.Prj3.config;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors.toString());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION",
                "Ràng buộc dữ liệu bị vi phạm", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Tham số sai kiểu dữ liệu: " + ex.getName(), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Thiếu tham số bắt buộc: " + ex.getParameterName(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(
            MaxUploadSizeExceededException ex) {
        return error(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                "Kích thước file vượt giới hạn cho phép (tối đa 10MB)", null);
    }

    

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(
            AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Xác thực thất bại: " + ex.getMessage(), null);
    }

    

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Bạn không có quyền thực hiện hành động này", null);
    }

    

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return error(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", ex.getMessage(), null);
    }

    

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", null);
    }

    

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        if (detail != null) body.put("detail", detail);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }
}
