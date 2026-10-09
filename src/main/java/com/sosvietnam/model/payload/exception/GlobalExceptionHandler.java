package com.sosvietnam.model.payload.exception;

import com.sosvietnam.util.DateUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {
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
                                .message("Tệp tải lên quá lớn (ảnh tối đa 10MB, video tối đa 15MB)")
                                .build()
                );
    }
}