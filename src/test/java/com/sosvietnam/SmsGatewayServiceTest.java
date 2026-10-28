package com.sosvietnam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.service.SmsService;
import com.sosvietnam.service.implement.SmsGatewayServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Cổng SMS (app SMS Gateway for Android, Cloud): số VN -> +84, đúng body / Basic auth, lỗi không ném ra ngoài. */
class SmsGatewayServiceTest {

    final ObjectMapper json = new ObjectMapper();
    final SmsGatewayServiceImpl service = new SmsGatewayServiceImpl(json);
    HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    /** Máy chủ giả trả {@code status}, ghi lại header Authorization + body nhận được. */
    void fakeGateway(int status, AtomicReference<String> auth, AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3rdparty/v1/message", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"id\":\"abc\",\"state\":\"Pending\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        ReflectionTestUtils.setField(service, "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/3rdparty/v1/");
        ReflectionTestUtils.setField(service, "username", "user1");
        ReflectionTestUtils.setField(service, "password", "pass1");
        ReflectionTestUtils.setField(service, "ttlSeconds", 900L);
    }

    @Test
    void phoneNumbers_normalisedToE164() {
        assertThat(SmsGatewayServiceImpl.toE164("0912 345 678")).isEqualTo("+84912345678");
        assertThat(SmsGatewayServiceImpl.toE164("84912345678")).isEqualTo("+84912345678");
        assertThat(SmsGatewayServiceImpl.toE164("+84 912.345.678")).isEqualTo("+84912345678");
        assertThat(SmsGatewayServiceImpl.toE164("02923812345")).isNull(); // máy bàn
        assertThat(SmsGatewayServiceImpl.toE164("12345")).isNull();
        assertThat(SmsGatewayServiceImpl.toE164(null)).isNull();
    }

    @Test
    void notConfigured_sendsNothing() {
        SmsService.Result r = service.send("0912345678", "x");
        assertThat(r.sent()).isFalse();
        assertThat(r.error()).contains("Chưa cấu hình");
    }

    @Test
    void sends_basicAuth_e164_simAndTtl() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        fakeGateway(202, auth, body);
        ReflectionTestUtils.setField(service, "sim", "2");

        SmsService.Result r = service.send("0912345678", "SOS VN: test");

        assertThat(r.sent()).isTrue();
        assertThat(auth.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("user1:pass1".getBytes()));
        JsonNode b = json.readTree(body.get());
        assertThat(b.at("/textMessage/text").asText()).isEqualTo("SOS VN: test");
        assertThat(b.at("/phoneNumbers/0").asText()).isEqualTo("+84912345678");
        assertThat(b.at("/simNumber").asInt()).isEqualTo(2);
        assertThat(b.at("/ttl").asLong()).isEqualTo(900);
        assertThat(b.at("/priority").asInt()).isEqualTo(100); // tin khẩn cấp: app gửi ngay
    }

    @Test
    void wrongCredentials_orDown_reportedNotThrown() throws Exception {
        fakeGateway(401, new AtomicReference<>(), new AtomicReference<>());
        assertThat(service.send("0912345678", "x").error()).contains("Sai tài khoản");
        server.stop(0);
        server = null;
        SmsService.Result down = service.send("0912345678", "x");
        assertThat(down.sent()).isFalse();
        assertThat(down.error()).contains("Không kết nối được");
    }
}
