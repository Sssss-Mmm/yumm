package com.example.demo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 이미 열려 있는 채팅방 구독을 서버가 끊는다(FR-T-02).
 *
 * {@link StompAuthChannelInterceptor}의 구성원 검사는 SUBSCRIBE/SEND 프레임이 올 때만 돈다.
 * 그래서 그룹을 이탈해도 이탈 전에 열어둔 구독은 소켓이 끊길 때까지 계속 메시지를 받는다 —
 * 나간 사람이 남은 대화를 그대로 엿볼 수 있다는 뜻이다. 이탈·해체 시점에 여기서 실제로 끊는다.
 *
 * 세션 레지스트리를 따로 만들지 않는다. STOMP CONNECT에서 인터셉터가 심어준 Principal 덕분에
 * Spring의 {@link SimpUserRegistry}가 이미 사용자 → 세션 → 구독을 들고 있다. 사용자 이름은
 * {@link CustomUserDetails#getUsername()} 즉 이메일이다.
 *
 * ponytail: UNSUBSCRIBE 프레임을 서버가 대신 흘려보내 해당 구독만 지운다. 소켓과 나머지 구독은
 * 살려두므로 다른 방 대화가 끊기지 않는다. 클라이언트는 조용히 수신이 멎는 것만 본다 —
 * "왜 끊겼는지" 알려야 하면 그때 시스템 메시지를 한 통 보낸다.
 */
@Component
@RequiredArgsConstructor
public class StompSubscriptionRevoker {

    private static final String ROOM_PREFIX = "/sub/chat/room/";
    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final SimpUserRegistry simpUserRegistry;
    // MessageChannel 타입 빈이 여럿이라(inbound/outbound/broker) 어느 것인지 못 박는다.
    // 브로커가 구독을 지우는 경로는 클라이언트 인바운드 채널 하나뿐이다.
    @Qualifier("clientInboundChannel")
    private final MessageChannel clientInboundChannel;

    /**
     * 지정한 사용자들의 이 방 구독을 끊는다. 이미 나갔거나 접속 중이 아니면 아무 일도 하지 않는다.
     *
     * @param groupId   채팅방 = 그룹 ID
     * @param usernames 끊을 사용자의 로그인 이메일
     */
    public void revoke(String groupId, Collection<String> usernames) {
        if (groupId == null || usernames == null || usernames.isEmpty()) {
            return;
        }

        String destination = ROOM_PREFIX + groupId;
        Set<SimpSubscription> subscriptions = simpUserRegistry.findSubscriptions(
                subscription -> destination.equals(subscription.getDestination())
                        && belongsTo(subscription, usernames));

        subscriptions.forEach(this::unsubscribe);
    }

    private static boolean belongsTo(SimpSubscription subscription, Collection<String> usernames) {
        SimpUser user = subscription.getSession().getUser();
        return user != null && usernames.contains(user.getName());
    }

    /** 클라이언트가 보낸 것과 같은 모양의 UNSUBSCRIBE. 브로커는 세션ID + 구독ID로 구독을 지운다. */
    private void unsubscribe(SimpSubscription subscription) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(subscription.getSession().getId());
        accessor.setSubscriptionId(subscription.getId());
        accessor.setLeaveMutable(true);

        clientInboundChannel.send(MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders()));
    }
}
