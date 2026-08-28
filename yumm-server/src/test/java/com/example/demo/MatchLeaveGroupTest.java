package com.example.demo;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.impl.MatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 그룹 이탈(FR-C-02)과 최소 인원 미달 시 해체(FR-C-03).
 * 분기는 "남은 인원 >= 3이면 유지", 해체 시 "지난 끼니면 종료 / 아니면 대기열 복귀" 둘뿐이라
 * 목으로 전부 훑는다. 동시 이탈은 잠금(findByGroupIdForUpdate)이 담당해 단위 테스트 대상이 아니다.
 */
class MatchLeaveGroupTest {

    private static final Long USER_ID = 1L;
    private static final String GROUP_ID = "g-1";

    private MatchRequestRepository matchRequestRepository;
    private MatchServiceImpl service;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        service = new MatchServiceImpl(matchRequestRepository, mock(UserRepository.class), mock(UserBlockRepository.class));
    }

    private static MatchRequest matched(long id, LocalDate mealDate) {
        MatchRequest request = MatchRequest.builder()
                .id(id)
                .mealDate(mealDate)
                .status(MatchStatus.WAITING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        request.assignToGroup(GROUP_ID);
        return request;
    }

    private List<MatchRequest> group(int size) {
        return group(size, LocalDate.now());
    }

    /** 이탈자(id=1)와 나머지 인원을 한 그룹으로 세팅한다. */
    private List<MatchRequest> group(int size, LocalDate mealDate) {
        MatchRequest leaver = matched(1, mealDate);
        List<MatchRequest> members = new java.util.ArrayList<>(List.of(leaver));
        for (long id = 2; id <= size; id++) {
            members.add(matched(id, mealDate));
        }
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(leaver));
        when(matchRequestRepository.findByGroupIdForUpdate(GROUP_ID)).thenReturn(members);
        return members;
    }

    @Test
    @DisplayName("4인 그룹에서 1명이 나가면 남은 3인 그룹은 그대로 유지된다")
    void groupSurvivesAtMinimumSize() {
        List<MatchRequest> members = group(4);

        service.leaveGroup(USER_ID);

        MatchRequest leaver = members.get(0);
        assertThat(leaver.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(leaver.getGroupId()).isNull();

        assertThat(members.subList(1, 4))
                .allSatisfy(m -> {
                    assertThat(m.getStatus()).isEqualTo(MatchStatus.MATCHED);
                    assertThat(m.getGroupId()).isEqualTo(GROUP_ID);
                });
    }

    @Test
    @DisplayName("3인 그룹에서 1명이 나가면 그룹이 해체되고 남은 2명은 다시 대기 상태가 된다")
    void groupBreaksBelowMinimumSize() {
        LocalDateTime before = LocalDateTime.now();
        List<MatchRequest> members = group(3);

        service.leaveGroup(USER_ID);

        assertThat(members.get(0).getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(members.get(0).getGroupId()).isNull();

        assertThat(members.subList(1, 3))
                .allSatisfy(m -> {
                    assertThat(m.getStatus()).isEqualTo(MatchStatus.WAITING);
                    assertThat(m.getGroupId()).isNull();
                    // 이미 지난 만료 시각을 물려받으면 복귀 즉시 만료된다
                    assertThat(m.getExpiresAt()).isAfter(before.plusMinutes(29));
                });
    }

    @Test
    @DisplayName("지난 날짜 그룹이 해체되면 남은 인원은 대기열로 돌아가지 않고 종료된다")
    void pastMealIsNotReturnedToWaiting() {
        List<MatchRequest> members = group(3, LocalDate.now().minusDays(1));

        service.leaveGroup(USER_ID);

        assertThat(members.get(0).getStatus()).isEqualTo(MatchStatus.CANCELLED);

        // WAITING으로 되돌리면 과거 날짜 버킷이 스케줄러에 다시 잡힌다
        assertThat(members.subList(1, 3))
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(MatchStatus.CANCELLED));
    }

    @Test
    @DisplayName("이탈자는 바로 재신청할 수 있고, 해체로 복귀한 인원은 대기 중이라 막힌다")
    void reapplyIsUnblockedOnlyForLeaver() {
        List<MatchRequest> members = group(3);

        service.leaveGroup(USER_ID);
        LocalDateTime now = LocalDateTime.now();

        assertThat(members.get(0).blocksReapply(now)).isFalse();
        assertThat(members.subList(1, 3))
                .allSatisfy(m -> assertThat(m.blocksReapply(now)).isTrue());
    }

    @Test
    @DisplayName("매칭된 신청이 없으면 MATCH_REQUEST_NOT_FOUND")
    void rejectsWhenNotMatched() {
        MatchRequest waiting = MatchRequest.builder()
                .id(1L)
                .status(MatchStatus.WAITING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.leaveGroup(USER_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MATCH_REQUEST_NOT_FOUND);
    }
}
