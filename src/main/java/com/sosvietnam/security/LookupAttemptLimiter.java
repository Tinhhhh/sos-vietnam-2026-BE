package com.sosvietnam.security;

import com.sosvietnam.model.payload.exception.SosException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chống dò 4 số cuối SĐT khi đã biết mã ca (Tra cứu, đọc / ký văn bản bằng mã): 5 lần sai / 10 phút / mã ca thì mã đó
 * bị khoá tra cứu tới khi lần sai cũ nhất trong 5 lần ra khỏi cửa sổ 10 phút (429). Giữ trong RAM, chỉ đúng khi 1
 * instance BE; khởi động lại thì xoá sạch (chấp nhận được).
 */
@Component
public class LookupAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** Gọi trước khi kiểm mã + SĐT: đang bị khoá thì 429. */
    public void check(String code) {
        Deque<Instant> recent = recent(code);
        if (recent == null) return;
        synchronized (recent) {
            if (recent.size() >= MAX_FAILURES) {
                long minutes = Math.max(1, (long) Math.ceil(
                        Duration.between(Instant.now(), recent.peekFirst().plus(WINDOW)).toSeconds() / 60.0));
                throw new SosException(HttpStatus.TOO_MANY_REQUESTS,
                        "Nhập sai quá nhiều lần với mã này, vui lòng thử lại sau " + minutes + " phút");
            }
        }
    }

    /** Ghi 1 lần sai (sai mã / sai SĐT). */
    public void failed(String code) {
        Deque<Instant> q = failures.computeIfAbsent(key(code), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(Instant.now());
            while (q.size() > MAX_FAILURES) q.pollFirst();
        }
    }

    /** Đúng mã + SĐT: xoá các lần sai trước của mã này. */
    public void succeeded(String code) {
        failures.remove(key(code));
    }

    private Deque<Instant> recent(String code) {
        Deque<Instant> q = failures.get(key(code));
        if (q == null) return null;
        synchronized (q) {
            Instant from = Instant.now().minus(WINDOW);
            while (!q.isEmpty() && q.peekFirst().isBefore(from)) q.pollFirst();
            if (q.isEmpty()) {
                failures.remove(key(code), q);
                return null;
            }
        }
        return q;
    }

    /** Cùng mã dù gõ có / không gạch, hoa / thường. */
    private static String key(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
