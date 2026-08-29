package com.example.demo;

import com.example.demo.domain.*;
import com.example.demo.repository.*;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.StompAuthChannelInterceptor;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.security.StompSubscriptionRevoker;
import com.example.demo.service.impl.MatchServiceImpl;
import com.example.demo.service.impl.UserServiceImpl;
import com.example.demo.util.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * QA 재현 절차 전체를 실제 서비스/리포지터리로 밟는다.
 * 어제 매칭 성사 -> 오늘 재신청 -> 소켓 연결 -> 탈퇴 -> 열린 소켓으로 SUBSCRIBE/SEND.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:qa2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class WithdrawnAccountChatAccessTest {

    private static final String GROUP_ID = "11111111-2222-3333-4444-555555555555";

    @Autowired private MatchRequestRepository matchRequestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserBlockRepository userBlockRepository;
    @Autowired private TransactionTemplate txTemplate;

    @Test
    @DisplayName("탈퇴 후에는 이미 열린 소켓으로도 어제 그룹 채팅을 구독/발신할 수 없다")
    void withdrawnUserCannotUseOpenSocket() {
        // 1. 어제 3명 매칭 성사
        User me = userRepository.save(user("me@yumm.local"));
        User b = userRepository.save(user("b@yumm.local"));
        User c = userRepository.save(user("c@yumm.local"));
        User d = userRepository.save(user("d@yumm.local"));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        matchRequestRepository.save(row(me, yesterday, MatchStatus.MATCHED, GROUP_ID, LocalDateTime.now().minusDays(1)));
        matchRequestRepository.save(row(b, yesterday, MatchStatus.MATCHED, GROUP_ID, LocalDateTime.now().minusDays(1)));
        matchRequestRepository.save(row(c, yesterday, MatchStatus.MATCHED, GROUP_ID, LocalDateTime.now().minusDays(1)));
        matchRequestRepository.save(row(d, yesterday, MatchStatus.MATCHED, GROUP_ID, LocalDateTime.now().minusDays(1)));

        // 2. 오늘 재신청 -> 최근 행은 WAITING, 그룹 없음
        matchRequestRepository.save(row(me, LocalDate.now(), MatchStatus.WAITING, null, LocalDateTime.now()));
        matchRequestRepository.flush();

        StompSubscriptionRevoker revoker = mock(StompSubscriptionRevoker.class);
        MatchServiceImpl matchService = new MatchServiceImpl(
                matchRequestRepository, userRepository, userBlockRepository,
                mock(EmailService.class), revoker);
        JwtRedisService redis = mock(JwtRedisService.class);
        UserServiceImpl userService = new UserServiceImpl(
                userRepository, mock(PasswordEncoder.class), redis,
                matchRequestRepository, matchService, mock(EmailService.class));
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                mock(JwtUtils.class), redis, matchRequestRepository, userRepository);
        MessageChannel channel = mock(MessageChannel.class);

        // 3. 탈퇴 전에는 어제 방을 구독/발신할 수 있다 (기존 정상 동작)
        assertThat(interceptor.preSend(subscribe(me.getId(), me.getEmail()), channel)).isNotNull();
        assertThat(interceptor.preSend(send(me.getId(), me.getEmail()), channel)).isNotNull();

        // 4. 탈퇴
        txTemplate.executeWithoutResult(s -> userService.withdraw(me.getId(), "header.payload.sig"));
        matchRequestRepository.flush();

        // 5. 열린 소켓으로 재시도 -> 막혀야 한다
        assertThatThrownBy(() -> interceptor.preSend(subscribe(me.getId(), me.getEmail()), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(send(me.getId(), me.getEmail()), channel))
                .isInstanceOf(MessageDeliveryException.class);

        // 어제 행의 groupId도 실제로 비워졌다
        assertThat(matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(me.getId())).isEmpty();

        // 남은 3명은 최소 인원을 채우므로 그룹이 유지된다(FR-C-02 회귀)
        assertThat(matchRequestRepository.findByGroupId(GROUP_ID)).hasSize(3);

        // 정상 사용자 b는 그대로 채팅할 수 있다 (회귀 확인)
        assertThat(interceptor.preSend(subscribe(b.getId(), b.getEmail()), channel)).isNotNull();
    }

    private static Message<byte[]> subscribe(Long userId, String email) {
        return frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + GROUP_ID, userId, email);
    }

    private static Message<byte[]> send(Long userId, String email) {
        return frame(StompCommand.SEND, "/pub/chatroom." + GROUP_ID, userId, email);
    }

    private static Message<byte[]> frame(StompCommand cmd, String dest, Long userId, String email) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        accessor.setDestination(dest);
        CustomUserDetails ud = new CustomUserDetails(userId, email, List.of("ROLE_USER"));
        accessor.setUser(new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static User user(String email) {
        return User.builder()
                .email(email).password("x").nickname("n")
                .gender(Gender.MALE).birthYear(1995).role(UserRole.ROLE_USER)
                .build();
    }

    private static MatchRequest row(User u, LocalDate date, MatchStatus status, String groupId, LocalDateTime createdAt) {
        return MatchRequest.builder()
                .user(u).region(Region.GANGNAM).mealDate(date).mealTime(MealTime.LUNCH)
                .genderPreference(GenderPreference.ANY).status(status).groupId(groupId)
                .createdAt(createdAt).expiresAt(createdAt.plusMinutes(30))
                .build();
    }

    /**
     * 여러 그룹을 한 번에 정리해도 그룹당 한 통씩만 나가야 한다(FR-N-02 중복 발송 회귀).
     * 알림은 afterCommit에 걸려 있어 테스트 트랜잭션 안에서는 절대 발화하지 않는다.
     * NOT_SUPPORTED로 테스트 트랜잭션을 끄고 TransactionTemplate이 실제로 커밋하게 한다.
     */
    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("두 그룹에 남아 있던 계정이 탈퇴하면 그룹마다 한 번씩만 알림이 나간다")
    void multiGroupWithdrawNotifiesOncePerGroup() {
        String groupA = "aaaaaaaa-8888-7777-6666-555555555555";
        String otherGroup = "99999999-8888-7777-6666-555555555555";
        User me = userRepository.save(user("m2@yumm.local"));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime t = LocalDateTime.now().minusDays(1);

        matchRequestRepository.save(row(me, yesterday, MatchStatus.MATCHED, groupA, t));
        matchRequestRepository.save(row(me, yesterday, MatchStatus.MATCHED, otherGroup, t));
        for (int i = 0; i < 3; i++) {
            User u = userRepository.save(user("g1-" + i + "@yumm.local"));
            matchRequestRepository.save(row(u, yesterday, MatchStatus.MATCHED, groupA, t));
            User v = userRepository.save(user("g2-" + i + "@yumm.local"));
            matchRequestRepository.save(row(v, yesterday, MatchStatus.MATCHED, otherGroup, t));
        }
        matchRequestRepository.flush();

        EmailService email = mock(EmailService.class);
        MatchServiceImpl matchService = new MatchServiceImpl(
                matchRequestRepository, userRepository, userBlockRepository,
                email, mock(StompSubscriptionRevoker.class));
        UserServiceImpl userService = new UserServiceImpl(
                userRepository, mock(PasswordEncoder.class), mock(JwtRedisService.class),
                matchRequestRepository, matchService, mock(EmailService.class));

        txTemplate.executeWithoutResult(s -> userService.withdraw(me.getId(), "h.p.s"));

        // 그룹당 남은 3명 = 총 6통. 한 사람이 같은 그룹 건으로 두 번 받으면 안 된다.
        org.mockito.ArgumentCaptor<String> to = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(6))
                .send(to.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(to.getAllValues()).doesNotHaveDuplicates();
        assertThat(matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(me.getId())).isEmpty();
    }
}
