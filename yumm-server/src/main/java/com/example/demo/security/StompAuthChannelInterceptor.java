package com.example.demo.security;

import com.example.demo.domain.MatchRequest;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.service.JwtRedisService;
import com.example.demo.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket 인증/인가.
 *
 * HTTP 핸드셰이크에는 Authorization 헤더가 실리지 않으므로 /ws는 HTTP 단에서 열어두고,
 * 실제 인증은 STOMP CONNECT 프레임의 Authorization 헤더로 여기서 처리한다.
 *
 * 인증만으로는 부족하다. 로그인한 사용자가 남의 채팅방 주소를 알면 그대로 구독할 수 있으므로
 * SUBSCRIBE/SEND 시점에 그 방(groupId)의 구성원인지 반드시 확인한다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SUBSCRIBE_PREFIX = "/sub/chat/room/";
    private static final String SEND_PREFIX = "/pub/chatroom.";

    private final JwtUtils jwtUtils;
    private final JwtRedisService jwtRedisService;
    private final MatchRequestRepository matchRequestRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            checkMembership(accessor, roomIdFrom(accessor.getDestination(), SUBSCRIBE_PREFIX));
        } else if (command == StompCommand.SEND) {
            checkMembership(accessor, roomIdFrom(accessor.getDestination(), SEND_PREFIX));
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException("인증 토큰이 없습니다.");
        }

        try {
            String token = JwtUtils.extractTokenFrom(authHeader);
            jwtUtils.validation(token);
            if (jwtRedisService.isAccessTokenBlacklisted(token)) {
                throw new JwtException("Blacklisted token");
            }

            CustomUserDetails userDetails = new CustomUserDetails(
                    jwtUtils.getUserIdFromToken(token),
                    jwtUtils.getEmailFromToken(token),
                    jwtUtils.getRolesFromToken(token));

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()));

        } catch (JwtException e) {
            throw new MessageDeliveryException("토큰이 유효하지 않습니다.");
        }
    }

    /**
     * ponytail: 구독/발신마다 그룹 구성원을 다시 조회한다. 메시지 한 건당 쿼리 한 번이라
     * 대화가 활발해지면 부담이 되는데, 방 구성원은 거의 안 바뀌므로 캐시가 잘 듣는다.
     * 실제로 느려지면 그때 얹는다.
     */
    private void checkMembership(StompHeaderAccessor accessor, String roomId) {
        if (roomId == null) {
            throw new MessageDeliveryException("채팅방 주소가 올바르지 않습니다.");
        }

        Long userId = currentUserId(accessor);
        List<MatchRequest> members = matchRequestRepository.findByGroupId(roomId);
        boolean isMember = members.stream().anyMatch(m -> m.getUser().getId().equals(userId));

        if (!isMember) {
            throw new MessageDeliveryException("이 채팅방에 참여할 권한이 없습니다.");
        }
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new MessageDeliveryException("인증되지 않은 연결입니다.");
    }

    /** 목적지 경로에서 roomId를 꺼낸다. 본문의 roomId는 위조 가능하므로 쓰지 않는다. */
    private String roomIdFrom(String destination, String prefix) {
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }
        String roomId = destination.substring(prefix.length());
        return roomId.isBlank() ? null : roomId;
    }
}
