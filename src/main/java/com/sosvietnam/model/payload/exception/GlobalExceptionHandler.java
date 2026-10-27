package com.sosvietnam.model.payload.exception;

import com.sosvietnam.util.DateUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Gom mọi lỗi về 1 format chung (ExceptionResponse), giống HIV-TMSS.
 * FE đọc field "message" (hoặc "data" với lỗi validate) để hiển thị.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {
        // Lỗi không lường trước: ghi đủ stack trace để còn tìm nguyên nhân
        log.error("Unhandled exception", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(500)
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Internal Server Error. Please contact administrator.")
                                .error(exception.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(SosException.class)
    public ResponseEntity<ExceptionResponse> handleSosException(SosException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus().value())
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(exception.getHttpStatus().value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message(exception.getMessage())
                                .data(exception.getData())
                                .build()
                );
    }

    // Gọi sai đường dẫn API -> 404 thay vì 500
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.NOT_FOUND.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Không tìm thấy API: /" + exception.getResourcePath())
                                .build()
                );
    }

    // Sai email / mật khẩu khi đăng nhập
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleBadCredentialsException(BadCredentialsException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED.value())
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.UNAUTHORIZED.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Email hoặc mật khẩu không chính xác")
                                .build()
                );
    }

    // Body JSON sai cú pháp / sai kiểu / có field lạ (ObjectMapper đang bật FAIL_ON_UNKNOWN_PROPERTIES) -> 400 thay vì 500
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.BAD_REQUEST.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Dữ liệu gửi lên không hợp lệ")
                                .error(exception.getMostSpecificCause().getMessage())
                                .build()
                );
    }

    // Thiếu / sai kiểu query param (vd: /geo/resolve thiếu lat) -> 400 thay vì 500
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ExceptionResponse> handleBadRequestParameter(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.BAD_REQUEST.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Tham số không hợp lệ")
                                .error(exception.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ExceptionResponse> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED.value())
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.UNAUTHORIZED.value())
                                .timestamp(DateUtil.formatTimestamp(new Date(), DateUtil.DATE_TIME_FORMAT))
                                .message("Authentication failed. Please check your credentials.")
                                .error(exception.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.BAD_REQUEST.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                // Gộp các lỗi thành 1 câu để FE hiện thẳng; "data" vẫn giữ lỗi theo từng field
                                .message(String.join("; ", errors.values()))
                                .data(errors)
                                .build()
                );
    }

    /** Lỗi validate ở entity lúc lưu DB (vd Incident.phone): dữ liệu sai là lỗi 400 của người gửi, không phải 500. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolationException(ConstraintViolationException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.BAD_REQUEST.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message(String.join("; ", errors.values()))
                                .data(errors)
                                .build()
                );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ExceptionResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        ExceptionResponse.builder()
                                .httpStatus(HttpStatus.PAYLOAD_TOO_LARGE.value())
                                .timestamp(DateUtil.formatTimestamp(DateUtil.convertUtcToIctDate(Instant.now()), DateUtil.DATE_TIME_FORMAT))
                                .message("Tệp tải lên quá lớn (ảnh tối đa 10MB, video tối đa 20MB)")
                                .build()
                );
    }
}