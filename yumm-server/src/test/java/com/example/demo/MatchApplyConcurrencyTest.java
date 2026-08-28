package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.User;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.impl.MatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 같은 사용자의 동시 매칭 신청이 여러 대기 행을 만들지 않아야 한다(BR-01 / FR-M-05 / FR-G-05).
 *
 * ponytail: 실제 스레드 경합은 DB 행 잠금이 막는다(Spring 컨텍스트 없이는 재현 불가).
 * 여기서는 잠금 조회가 중복 검사보다 "먼저" 호출되는지를 검증한다 — 순서가 뒤집히면 경합이 다시 열린다.
 */
class MatchApplyConcurrencyTest {

    private static final Long USER_ID = 16L;
    private static final LocalDate TODAY = LocalDate.now();

    private MatchRequestRepository matchRequestRepository;
    private UserRepository userRepository;
    private MatchServiceImpl service;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        userRepository = mock(UserRepository.class);
        service = new MatchServiceImpl(matchRequestRepository, userRepository);

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(
                User.builder().id(USER_ID).nickname("u600").gender(Gender.MALE).build()));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** MatchApplyRequest는 세터도 빌더도 없다. 신청 검증에 필요한 건 식사 날짜뿐이라 목이 제일 짧다. */
    private static MatchApplyRequest applyRequest() {
        MatchApplyRequest request = mock(MatchApplyRequest.class);
        when(request.getMealDate()).thenReturn(TODAY);
        return request;
    }

    @Test
    @DisplayName("사용자 행을 잠근 뒤에 중복 검사를 한다 (동시 신청 직렬화)")
    void locksUserBeforeDuplicateCheck() {
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.apply(USER_ID, applyRequest()).getStatus()).isEqualTo("WAITING");

        InOrder order = inOrder(userRepository, matchRequestRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);   // 잠금이 먼저
        order.verify(matchRequestRepository).findFirstByUser_IdOrderByCreatedAtDesc(USER_ID);
        order.verify(matchRequestRepository).save(any(MatchRequest.class));
        // 잠금 없는 조회로 되돌아가면 경합이 다시 열린다
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("먼저 커밋된 대기 행이 보이면 뒤이은 동시 신청은 ALREADY_WAITING")
    void secondConcurrentApplyIsRejected() {
        // 잠금 대기 뒤 앞 트랜잭션의 WAITING 행이 보이는 상황
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(
                MatchRequest.builder()
                        .status(MatchStatus.WAITING)
                        .mealDate(TODAY)
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusMinutes(30))
                        .build()));

        assertThatThrownBy(() -> service.apply(USER_ID, applyRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_WAITING);

        verify(matchRequestRepository, never()).save(any(MatchRequest.class));
    }
}
