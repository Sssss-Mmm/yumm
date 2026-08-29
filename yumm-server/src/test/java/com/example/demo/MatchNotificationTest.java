package com.example.demo;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.domain.User;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.impl.EmailServiceImpl;
import com.example.demo.security.StompSubscriptionRevoker;
import com.example.demo.service.impl.MatchServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 매칭 알림 메일 — 성사(FR-N-01), 이탈·해체(FR-N-02), 당일 아침 리마인드(FR-N-04).
 *
 * <p>지키는 것 세 가지다 — 구성원 전원에게 나가는가, <b>발송이 커밋 뒤로 미뤄지는가</b>,
 * 그리고 <b>SMTP가 죽어도 편성이 살아남는가</b>. 알림은 편성의 전제가 아니라 부가 기능이다.
 */
class MatchNotificationTest {

    private static final LocalDate MEAL_DATE = LocalDate.of(2026, 8, 29);
    private static final String GROUP_ID = "g-1";
    private static final LocalDateTime CREATED_AT = LocalDateTime.now().minusMinutes(1);

    private MatchRequestRepository matchRequestRepository;
    private JavaMailSender mailSender;
    private MatchServiceImpl service;
    private List<MatchRequest> waiting;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        mailSender = mock(JavaMailSender.class);
        // 실제 발송 구현을 끼운다. 삼키는 자리가 여기라 목으로 바꾸면 검증하려는 것이 사라진다.
        // 호스트는 예약 TLD. 값이 비어 있으면 구현이 발송을 건너뛰므로 아무 값이나 있어야 한다.
        EmailServiceImpl emailService = new EmailServiceImpl(mailSender, "no-reply@yumm.local", "smtp.invalid");
        service = new MatchServiceImpl(matchRequestRepository, mock(UserRepository.class),
                mock(UserBlockRepository.class), emailService, mock(StompSubscriptionRevoker.class));

        waiting = List.of(request(1), request(2), request(3));
        when(matchRequestRepository.findWaitingInBucket(any(), any(), any(), any())).thenReturn(waiting);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 서로 호환되는(성별 무관·한식) 대기자. 3명이면 그룹 하나가 만들어진다. */
    private static MatchRequest request(long id) {
        return request(id, MEAL_DATE);
    }

    private static MatchRequest request(long id, LocalDate mealDate) {
        User user = User.builder().id(id).email("user" + id + "@example.com").nickname("u" + id)
                .gender(Gender.MALE).build();
        return MatchRequest.builder()
                .id(id)
                .user(user)
                .region(Region.GANGNAM)
                .mealDate(mealDate)
                .mealTime(MealTime.LUNCH)
                .genderPreference(GenderPreference.ANY)
                .foodPreferences(Set.of(FoodCategory.KOREAN))
                .allowPair(false)
                .status(MatchStatus.WAITING)
                .createdAt(CREATED_AT.plusSeconds(id))
                .expiresAt(CREATED_AT.plusMinutes(30))
                .build();
    }

    private int formGroups() {
        return service.formGroupsInBucket(Region.GANGNAM, MEAL_DATE, MealTime.LUNCH);
    }

    @Test
    @DisplayName("그룹이 만들어지면 구성원 전원에게 메일이 나간다")
    void notifiesEveryMember() {
        assertThat(formGroups()).isEqualTo(1);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(3)).send(sent.capture());

        assertThat(sent.getAllValues()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com", "user3@example.com");

        SimpleMailMessage first = sent.getAllValues().get(0);
        assertThat(first.getSubject()).contains("매칭");
        // 버킷 정보가 본문에 실려야 "어느 약속인지"를 앱을 열지 않고도 안다.
        assertThat(first.getText()).contains("강남").contains("점심").contains(MEAL_DATE.toString()).contains("3명");
    }

    @Test
    @DisplayName("발송은 편성이 커밋된 뒤에 이뤄진다 (트랜잭션 안에서 SMTP를 기다리지 않는다)")
    void sendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization(); // 트랜잭션이 열린 상태를 흉내낸다

        assertThat(formGroups()).isEqualTo(1);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(TransactionSynchronization::afterCommit); // 커밋
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP가 죽어도 편성은 그대로 커밋된다 (알림 실패가 롤백을 일으키지 않는다)")
    void mailFailureDoesNotRollBackMatching() {
        doThrow(new MailSendException("서버 연결 실패")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(formGroups()).isEqualTo(1); // 예외가 새어 나가면 여기서 실패한다

        assertThat(waiting).allSatisfy(m -> {
            assertThat(m.getStatus()).isEqualTo(MatchStatus.MATCHED);
            assertThat(m.getGroupId()).isNotNull();
        });
        // 첫 통이 실패해도 나머지 구성원에게는 계속 보낸다
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    /** 오늘 날짜로 성사된 3인 그룹. 이탈자는 id=1이다. */
    private List<MatchRequest> matchedGroupToday() {
        List<MatchRequest> members = List.of(request(1, LocalDate.now()), request(2, LocalDate.now()),
                request(3, LocalDate.now()));
        members.forEach(m -> m.assignToGroup(GROUP_ID));
        return members;
    }

    @Test
    @DisplayName("그룹이 해체되면 남은 전원에게 알림이 가고 본문에 대기열 복귀가 적힌다")
    void notifiesEveryRemainingMemberOnDisband() {
        List<MatchRequest> members = matchedGroupToday();
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(members.get(0)));
        when(matchRequestRepository.findByGroupIdForUpdate(GROUP_ID)).thenReturn(members);

        service.leaveGroup(1L);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        // 나간 본인은 받지 않는다. 남은 두 명은 빠짐없이 받는다.
        assertThat(sent.getAllValues()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("user2@example.com", "user3@example.com");

        SimpleMailMessage first = sent.getAllValues().get(0);
        assertThat(first.getSubject()).contains("해체");
        // 대기열 복귀(FR-C-03)를 모르면 다시 신청해야 하는 줄 안다
        assertThat(first.getText()).contains("해체").contains("대기열").contains("강남").contains("점심");
        // 다른 구성원의 닉네임·이메일은 넣지 않는다(NFR-05)
        assertThat(first.getText()).doesNotContain("u2").doesNotContain("@example.com");
    }

    @Test
    @DisplayName("1명이 나가도 그룹이 유지되면 남은 인원에게 해체가 아니라 변동을 알린다")
    void notifiesRemainingMembersWhenGroupSurvives() {
        List<MatchRequest> members = new java.util.ArrayList<>(matchedGroupToday());
        members.add(request(4, LocalDate.now()));
        members.get(3).assignToGroup(GROUP_ID);
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(members.get(0)));
        when(matchRequestRepository.findByGroupIdForUpdate(GROUP_ID)).thenReturn(members);

        service.leaveGroup(1L);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(3)).send(sent.capture());
        SimpleMailMessage first = sent.getAllValues().get(0);
        assertThat(first.getSubject()).doesNotContain("해체");
        assertThat(first.getText()).contains("유지").contains("남은 인원: 3명");
    }

    @Test
    @DisplayName("당일 아침 리마인드는 스케줄러가 여러 번 돌아도 하루 한 번만 나간다")
    void remindsOncePerDay() {
        List<MatchRequest> members = matchedGroupToday();
        when(matchRequestRepository.findMatchedOnDate(LocalDate.now())).thenReturn(members);

        assertThat(service.remindTodayMeals()).isEqualTo(3);
        assertThat(service.remindTodayMeals()).isZero(); // 다음 주기(10분 뒤)
        assertThat(service.remindTodayMeals()).isZero();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(3)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com", "user3@example.com");

        SimpleMailMessage first = sent.getAllValues().get(0);
        assertThat(first.getText()).contains("오늘").contains("강남").contains("점심").contains("3명");
        // 만날 시각은 시스템에 없다. 본문에 지어내면 안 된다.
        assertThat(first.getText()).doesNotContain(":00").doesNotContain("시에");
    }
}
