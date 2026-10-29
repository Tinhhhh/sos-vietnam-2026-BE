package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SMS qua app SMS Gateway for Android (github.com/capcom6/android-sms-gateway), chế độ Cloud:
 * POST {url}/message, Basic auth = Username / Password hiện trong app, body
 * { textMessage: { text }, phoneNumbers: ["+84..."], simNumber?, ttl }. Máy chủ trả 202 + id, điện thoại tự lấy lệnh
 * về rồi gửi bằng SIM của nó. Chưa điền tài khoản thì chỉ ghi log (local / máy chưa cài app).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsGatewayServiceImpl implements SmsService {

    private final ObjectMapper objectMapper;

    @Value("${app.sms.gateway.url:https://api.sms-gate.app/3rdparty/v1}")
    private String url;
    @Value("${app.sms.gateway.username:}")
    private String username;
    @Value("${app.sms.gateway.password:}")
    private String password;
    // máy 2 SIM (SIM + eSIM): khe gửi 1-3; trống = SIM mặc định của máy
    @Value("${app.sms.gateway.sim:}")
    private String sim;
    // điện thoại tắt / mất mạng lâu hơn thế thì bỏ tin, không gửi muộn một link đã cũ
    @Value("${app.sms.gateway.ttl-seconds:900}")
    private long ttlSeconds;
    // >= 100 = app gửi ngay, bỏ qua khoảng nghỉ / giới hạn tốc độ cài trong app (tin khẩn cấp); 0 = không gửi trường này
    @Value("${app.sms.gateway.priority:100}")
    private int priority = 100;

    private volatile HttpClient httpClient;

    @Override
    public boolean configured() {
        return StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    @Override
    public Result send(String phone, String text) {
        String to = toE164(phone);
        if (to == null) return new Result(false, "Số điện thoại không hợp lệ");
        if (!configured()) {
            // không ghi nội dung: tin chứa mã link (ai đọc log cũng mở được trang người phụ trách)
            log.info("📵 [SMS] chưa cấu hình cổng SMS, bỏ qua tin {} ký tự tới {}", text.length(), mask(to));
            return new Result(false, "Chưa cấu hình cổng SMS");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("textMessage", Map.of("text", text));
            body.put("phoneNumbers", List.of(to));
            body.put("ttl", ttlSeconds);
            // "Sent" chỉ là tổng đài nhà mạng đã nhận; có báo cáo thì app ghi được lúc máy người nhận có tin (Delivered)
            body.put("withDeliveryReport", true);
            if (StringUtils.hasText(sim)) body.put("simNumber", Integer.parseInt(sim.trim()));
            if (priority != 0) body.put("priority", priority);
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + "/message"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            long started = System.nanoTime();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            long tookMs = (System.nanoTime() - started) / 1_000_000;
            if (response.statusCode() / 100 != 2) {
                log.warn("📵 [SMS] cổng SMS trả HTTP {} cho {}: {}", response.statusCode(), mask(to), response.body());
                return new Result(false, response.statusCode() == 401
                        ? "Sai tài khoản cổng SMS" : "Cổng SMS lỗi (HTTP " + response.statusCode() + ")");
            }
            // chỉ là lúc máy chủ nhận lệnh; chậm sau đó nằm ở chặng đẩy xuống điện thoại + app gửi bằng SIM
            // id tin: tra trạng thái (Sent / Delivered và giờ) trong app hoặc GET {url}/message/{id} để biết chậm ở đâu
            log.info("✉️ [SMS] cổng SMS nhận lệnh cho {} sau {} ms, id {}", mask(to), tookMs, messageId(response.body()));
            return new Result(true, null);
        } catch (Exception e) {
            log.warn("📵 [SMS] không gọi được cổng SMS: {}", e.getMessage());
            return new Result(false, "Không kết nối được cổng SMS");
        }
    }

    private String messageId(String body) {
        try {
            return objectMapper.readTree(body).path("id").asText("?");
        } catch (Exception e) {
            return "?";
        }
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                }
            }
        }
        return httpClient;
    }

    /** 0912345678 / 84912345678 / +84 912 345 678 -> +84912345678; số lạ -> null. */
    public static String toE164(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("+84")) digits = digits.substring(3);
        else if (digits.startsWith("84") && digits.length() == 11) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = digits.substring(1);
        else return null;
        return digits.matches("[35789]\\d{8}") ? "+84" + digits : null;
    }

    private static String mask(String e164) {
        return e164.length() < 6 ? "***" : e164.substring(0, e164.length() - 6) + "***" + e164.substring(e164.length() - 3);
    }
}
