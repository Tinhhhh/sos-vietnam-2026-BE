package com.sosvietnam.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giữ các WebSocket đang mở (khoá = id phiên, trùng simpSessionId của STOMP) để đóng được phiên
 * của một người, vd đổi mật khẩu thì máy cũ đang mở màn hình trực ban phải mất kết nối ngay,
 * không đợi lần CONNECT sau. Chỉ đúng khi 1 instance BE (như onlineEmails).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompSessionRegistry implements WebSocketHandlerDecoratorFactory {

    /** lấy muộn: SimpUserRegistry do chính cấu hình WebSocket tạo ra (inject thẳng thì vòng phụ thuộc) */
    private final ObjectProvider<SimpUserRegistry> userRegistry;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** Đóng mọi phiên STOMP của người dùng (tên Principal = email cán bộ). Trả số phiên đã đóng. */
    public int closeAll(String username) {
        SimpUser user = userRegistry.getObject().getUser(username);
        if (user == null) {
            return 0;
        }
        List<String> ids = user.getSessions().stream().map(SimpSession::getId).toList();
        int closed = 0;
        for (String id : ids) {
            WebSocketSession session = sessions.get(id);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Phiên đăng nhập đã kết thúc"));
                closed++;
            } catch (IOException e) {
                log.warn("[STOMP] không đóng được phiên {} của {}: {}", id, username, e.getMessage());
            }
        }
        return closed;
    }
}
