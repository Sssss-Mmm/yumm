package com.example.demo.security;

import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
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

/**
 * WebSocket 인증/인가.
 *
 * HTTP 핸드셰이크에는 Authorization 헤더가 실리지 않으므로 /ws는 HTTP 단에서 열어두고,
 * 실제 인증은 STOMP CONNECT 프레임의 Authorization 헤더로 여기서 처리한다.
 *
 * 인증만으로는 부족하다. 로그인한 사용자가 남의 채팅방 주소를 알면 그대로 구독할 수 있으므로
 * SUBSCRIBE/SEND 시점에 그 방(groupId)의 구성원인지 반드시 확인한다.
 *
 * 탈퇴 계정 차단(FR-A-08)은 CONNECT뿐 아니라 SUBSCRIBE/SEND에서도 한다. CONNECT에서만 보면
 * 소켓을 열어둔 채 탈퇴한 계정이 그 소켓이 끊길 때까지 채팅을 계속 읽고 쓴다.
 * HTTP는 JwtAuthenticationFilter가 같은 검사를 요청마다 한다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SUBSCRIBE_PREFIX = "/sub/chat/room/";
    private static final String SEND_PREFIX = "/pub/chatroom.";

    private final JwtUtils jwtUtils;
    private final JwtRedisService jwtRedisService;
    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;

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

            rejectIfWithdrawn(userDetails.getId());

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

        // 연결 이후에 탈퇴했을 수 있다. 프레임마다 다시 본다.
        rejectIfWithdrawn(userId);

        if (!matchRequestRepository.existsByGroupIdAndUser_Id(roomId, userId)) {
            throw new MessageDeliveryException("이 채팅방에 참여할 권한이 없습니다.");
        }
    }

    /**
     * 탈퇴 계정 차단(FR-A-08). JwtAuthenticationFilter와 같은 판정을 같은 쿼리로 한다.
     *
     * ponytail: 프레임마다 PK exists 쿼리 한 번. 구성원 조회와 같은 비용이고 인덱스 조회라 지금은 무시할 수준이다.
     * 부담이 되면 필터 쪽 주석대로 탈퇴 시 Redis에 'withdrawn:{userId}' 플래그를 심고 그걸 읽는다.
     *
     * 한계: 여기서 보는 건 탈퇴 여부뿐이다. 프레임에는 토큰이 없어 만료·블랙리스트는 다시 못 본다.
     * 그것까지 막으려면 CONNECT 때 토큰을 세션 속성에 넣고 프레임마다 재검증한다.
     */
    private void rejectIfWithdrawn(Long userId) {
        if (userRepository.existsByIdAndWithdrawnAtIsNotNull(userId)) {
            throw new MessageDeliveryException("탈퇴한 계정입니다.");
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
