package com.example.demo;

import com.example.demo.security.StompSubscriptionRevoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpSubscriptionMatcher;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이탈자의 열린 구독 강제 해제(FR-T-02).
 *
 * 인터셉터는 프레임이 올 때만 검사해서, 이탈 전에 열어둔 구독은 소켓이 끊길 때까지 계속 수신한다.
 * 여기서 확인하는 것은 "대상자의 그 방 구독만 골라 UNSUBSCRIBE를 흘려보낸다" 하나다.
 */
class StompSubscriptionRevokerTest {

    private static final String GROUP_ID = "8f0b1c2d-0000-0000-0000-000000000001";
    private static final String ROOM = "/sub/chat/room/" + GROUP_ID;

    private MessageChannel clientInboundChannel;
    private StompSubscriptionRevoker revoker;

    @BeforeEach
    void setUp() {
        clientInboundChannel = mock(MessageChannel.class);

        // 방에는 나간 사람(u1)과 남은 사람(u2)이 있고, u1은 다른 방(/sub/chat/room/other)도 구독 중이다
        List<SimpSubscription> all = List.of(
                subscription("sub-1", "sess-1", "u1@test.com", ROOM),
                subscription("sub-2", "sess-2", "u2@test.com", ROOM),
                subscription("sub-3", "sess-1", "u1@test.com", "/sub/chat/room/other"));

        SimpUserRegistry registry = mock(SimpUserRegistry.class);
        when(registry.findSubscriptions(any(SimpSubscriptionMatcher.class))).thenAnswer(invocation -> {
            SimpSubscriptionMatcher matcher = invocation.getArgument(0);
            return all.stream().filter(matcher::match).collect(Collectors.toSet());
        });

        revoker = new StompSubscriptionRevoker(registry, clientInboundChannel);
    }

    private static SimpSubscription subscription(String id, String sessionId, String username, String destination) {
        SimpUser user = mock(SimpUser.class);
        when(user.getName()).thenReturn(username);

        SimpSession session = mock(SimpSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUser()).thenReturn(user);

        SimpSubscription subscription = mock(SimpSubscription.class);
        when(subscription.getId()).thenReturn(id);
        when(subscription.getDestination()).thenReturn(destination);
        when(subscription.getSession()).thenReturn(session);
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private List<StompHeaderAccessor> sentFrames() {
        ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
        verify(clientInboundChannel, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream()
                .map(m -> StompHeaderAccessor.wrap(m))
                .toList();
    }

    @Test
    @DisplayName("이탈자의 그 방 구독만 UNSUBSCRIBE로 끊는다")
    void revokesOnlyTargetSubscription() {
        revoker.revoke(GROUP_ID, Set.of("u1@test.com"));

        List<StompHeaderAccessor> frames = sentFrames();
        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).getCommand()).isEqualTo(StompCommand.UNSUBSCRIBE);
        // 브로커는 세션ID + 구독ID로 구독을 지운다. 둘 중 하나라도 비면 아무것도 안 끊긴다
        assertThat(frames.get(0).getSessionId()).isEqualTo("sess-1");
        assertThat(frames.get(0).getSubscriptionId()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("남은 사람과 다른 방 구독은 건드리지 않는다")
    void keepsOtherSubscriptions() {
        revoker.revoke(GROUP_ID, Set.of("u1@test.com"));

        assertThat(sentFrames())
                .noneMatch(frame -> "sub-2".equals(frame.getSubscriptionId())
                        || "sub-3".equals(frame.getSubscriptionId()));
    }

    @Test
    @DisplayName("해체된 그룹은 구성원 전원의 구독을 끊는다")
    void revokesEveryoneOnDisband() {
        revoker.revoke(GROUP_ID, List.of("u1@test.com", "u2@test.com"));

        assertThat(sentFrames()).extracting(StompHeaderAccessor::getSubscriptionId)
                .containsExactlyInAnyOrder("sub-1", "sub-2");
    }

    @Test
    @DisplayName("접속 중이 아니거나 방이 없으면 아무 프레임도 보내지 않는다")
    void noFrameWhenNothingToRevoke() {
        revoker.revoke(GROUP_ID, List.of("nobody@test.com"));
        revoker.revoke(null, List.of("u1@test.com"));
        revoker.revoke(GROUP_ID, List.of());

        verify(clientInboundChannel, never()).send(any());
    }
}
