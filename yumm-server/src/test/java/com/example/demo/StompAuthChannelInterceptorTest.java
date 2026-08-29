package com.example.demo;

import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.StompAuthChannelInterceptor;
import com.example.demo.service.JwtRedisService;
import com.example.demo.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 채팅방 참여 자격 검사. 남의 방 주소를 알아도 들어갈 수 없어야 하고,
 * 탈퇴한 계정은 이미 열어둔 소켓으로도 읽고 쓸 수 없어야 한다(FR-A-08).
 */
class StompAuthChannelInterceptorTest {

    private static final String ROOM_ID = "8f0b1c2d-0000-0000-0000-000000000001";
    private static final String TOKEN = "header.payload.signature";

    private MatchRequestRepository matchRequestRepository;
    private UserRepository userRepository;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        userRepository = mock(UserRepository.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        when(jwtUtils.getUserIdFromToken(TOKEN)).thenReturn(1L);
        when(jwtUtils.getEmailFromToken(TOKEN)).thenReturn("u1@test.com");
        when(jwtUtils.getRolesFromToken(TOKEN)).thenReturn(List.of("ROLE_USER"));
        interceptor = new StompAuthChannelInterceptor(
                jwtUtils, mock(JwtRedisService.class), matchRequestRepository, userRepository);
        channel = mock(MessageChannel.class);

        // 방에는 1번, 2번 사용자만 있다
        when(matchRequestRepository.existsByGroupIdAndUser_Id(eq(ROOM_ID), anyLong()))
                .thenAnswer(invocation -> List.of(1L, 2L).contains(invocation.getArgument(1)));
    }

    private Message<byte[]> frame(StompCommand command, String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (userId != null) {
            CustomUserDetails details = new CustomUserDetails(userId, "u" + userId + "@test.com", List.of("ROLE_USER"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("그룹 구성원은 채팅방을 구독할 수 있다")
    void memberCanSubscribe() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID, 1L);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 방 주소를 알아도 구독할 수 없다")
    void nonMemberCannotSubscribe() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID, 99L);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("권한이 없습니다");
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 메시지도 보낼 수 없다")
    void nonMemberCannotSend() {
        Message<byte[]> message = frame(StompCommand.SEND, "/pub/chatroom." + ROOM_ID, 99L);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("권한이 없습니다");
    }

    @Test
    @DisplayName("인증되지 않은 연결은 구독할 수 없다")
    void unauthenticatedCannotSubscribe() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("인증되지 않은");
    }

    @Test
    @DisplayName("CONNECT에 토큰이 없으면 연결을 거부한다")
    void connectWithoutTokenIsRejected() {
        Message<byte[]> message = frame(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("인증 토큰이 없습니다");
    }

    @Test
    @DisplayName("탈퇴한 계정은 CONNECT 단계에서 연결이 거부된다")
    void withdrawnAccountCannotConnect() {
        when(userRepository.existsByIdAndWithdrawnAtIsNotNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(connectFrame(), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("탈퇴한 계정");
    }

    @Test
    @DisplayName("연결한 뒤 탈퇴하면 이미 열려 있는 소켓으로도 구독/발신할 수 없다")
    void withdrawnAccountCannotUseOpenSocket() {
        // 연결 시점에는 멀쩡한 계정이었다
        assertThat(interceptor.preSend(connectFrame(), channel)).isNotNull();

        // 그 뒤 탈퇴. 소켓은 살아 있고 방 구성원 판정(existsByGroupIdAndUser_Id)도 아직 true다.
        when(userRepository.existsByIdAndWithdrawnAtIsNotNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID, 1L), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("탈퇴한 계정");

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/pub/chatroom." + ROOM_ID, 1L), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("탈퇴한 계정");
    }

    /** 토큰이 실린 CONNECT 프레임. 서명 검증은 목이 통과시키므로 여기서 보는 건 탈퇴 검사뿐이다. */
    private Message<byte[]> connectFrame() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("엉뚱한 목적지로는 구독할 수 없다")
    void wrongDestinationIsRejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/sub/something/else", 1L);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("올바르지 않습니다");
    }
}
