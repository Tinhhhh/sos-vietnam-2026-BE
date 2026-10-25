package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.service.DispatchTrackingService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Traccar đẩy từng điểm GPS sang đây (position forwarding). Chỉ gọi được trong mạng Docker trên VPS
 * (nginx không chuyển /internal ra Internet) và phải có header bí mật X-Traccar-Secret
 * (TRACCAR_FORWARD_SECRET; chưa cấu hình thì luôn 401). Không ghi log nội dung điểm.
 */
@Hidden
@RestController
@RequestMapping("/internal/traccar")
@RequiredArgsConstructor
public class TraccarController {

    private final DispatchTrackingService dispatchTrackingService;

    @Value("${app.traccar.forward-secret:}")
    private String forwardSecret;

    @PostMapping("/positions")
    public ResponseEntity<Object> receivePosition(
            @RequestHeader(value = "X-Traccar-Secret", required = false) String secret,
            @RequestBody TraccarPositionRequest request) {
        if (!secretMatches(secret)) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Sai mã bí mật Traccar");
        }
        boolean used = dispatchTrackingService.accept(request);
        return ResponseBuilder.returnData(HttpStatus.OK, used ? "Position used" : "Position ignored",
                Map.of("used", used));
    }

    private boolean secretMatches(String secret) {
        if (forwardSecret == null || forwardSecret.isBlank() || secret == null) return false;
        return MessageDigest.isEqual(forwardSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }
}
