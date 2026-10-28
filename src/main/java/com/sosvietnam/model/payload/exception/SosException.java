package com.sosvietnam.model.payload.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class SosException extends RuntimeException {
    private HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
    /** Thêm vào body lỗi (`data`) cho FE đọc, vd { code: OPEN_CASE_EXISTS, open_count: 1 }; null = không có */
    private Map<String, String> data;

    public SosException(String message) {
        super(message);
    }

    public SosException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public SosException(HttpStatus httpStatus, String message, Map<String, String> data) {
        this(httpStatus, message);
        this.data = data;
    }
}
