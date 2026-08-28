package com.example.demo;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.User;
import com.example.demo.repository.MatchRequestRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 채팅방 참여 자격 검사. 남의 방 주소를 알아도 들어갈 수 없어야 한다.
 */
class StompAuthChannelInterceptorTest {

    private static final String ROOM_ID = "8f0b1c2d-0000-0000-0000-000000000001";

    private MatchRequestRepository matchRequestRepository;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        interceptor = new StompAuthChannelInterceptor(
                mock(JwtUtils.class), mock(JwtRedisService.class), matchRequestRepository);
        channel = mock(MessageChannel.class);

        // 방에는 1번, 2번 사용자만 있다
        when(matchRequestRepository.findByGroupId(ROOM_ID))
                .thenReturn(List.of(memberOf(1L), memberOf(2L)));
    }

    private MatchRequest memberOf(Long userId) {
        return MatchRequest.builder()
                .user(User.builder().id(userId).build())
                .build();
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
    @DisplayName("엉뚱한 목적지로는 구독할 수 없다")
    void wrongDestinationIsRejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/sub/something/else", 1L);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("올바르지 않습니다");
    }
}
