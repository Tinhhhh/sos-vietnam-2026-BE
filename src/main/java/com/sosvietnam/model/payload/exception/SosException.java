package com.sosvietnam.model.payload.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SosException extends RuntimeException {
    private HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    public SosException(String message) {
        super(message);
    }

    public SosException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }
}