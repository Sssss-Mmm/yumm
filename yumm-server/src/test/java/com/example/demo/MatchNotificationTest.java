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
 * 매칭 성사 알림 메일(FR-N-01).
 *
 * <p>지키는 것 세 가지다 — 구성원 전원에게 나가는가, <b>발송이 커밋 뒤로 미뤄지는가</b>,
 * 그리고 <b>SMTP가 죽어도 편성이 살아남는가</b>. 알림은 편성의 전제가 아니라 부가 기능이다.
 */
class MatchNotificationTest {

    private static final LocalDate MEAL_DATE = LocalDate.of(2026, 8, 29);
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
                mock(UserBlockRepository.class), emailService);

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
        User user = User.builder().id(id).email("user" + id + "@example.com").nickname("u" + id)
                .gender(Gender.MALE).build();
        return MatchRequest.builder()
                .id(id)
                .user(user)
                .region(Region.GANGNAM)
                .mealDate(MEAL_DATE)
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
}
