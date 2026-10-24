package com.sosvietnam.config;

import com.sosvietnam.security.StompAuthChannelInterceptor;
import com.sosvietnam.security.StompSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP cho màn cán bộ và màn chờ của người báo tin. Server chỉ gửi tin riêng theo người
 * (/user/queue/incidents, /user/queue/incident, /user/queue/calls), không có topic chung:
 * ai kết nối cũng phải qua StompAuthChannelInterceptor (JWT hoặc citizen token trong frame CONNECT).
 * Client chỉ gửi được tín hiệu video call (/app/calls).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompSessionRegistry stompSessionRegistry;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }

    /** SDP của WebRTC (offer/answer) có thể vài chục KB; mặc định 64 KB là sát nút. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(256 * 1024);
        registration.addDecoratorFactory(stompSessionRegistry);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
