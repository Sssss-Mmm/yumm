package com.example.demo.config;

import com.example.demo.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 경로는 이미 붙어 있는 콘솔 클라이언트(console-client/chatClient.js)에 맞춘 것이다.
 *   연결   ws://localhost:8080/ws
 *   구독   /sub/chat/room/{roomId}
 *   발행   /pub/chatroom.{roomId}
 *
 * ponytail: 내장 심플 브로커를 쓴다. 메시지를 서버 메모리에 들고 있어서 서버가 여러 대가 되면
 * 다른 인스턴스에 붙은 사람에게 전달되지 않는다. 확장할 때 Redis pub/sub이나 외부 브로커로 바꾼다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
