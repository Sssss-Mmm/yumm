package com.example.demo;

import com.example.demo.domain.AttendanceCheck;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.domain.User;
import com.example.demo.dto.match.AttendanceCheckRequest;
import com.example.demo.dto.match.MatchStatusResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.AttendanceCheckRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.service.impl.AttendanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 참석 상호 체크(FR-C-06). 분기는 "체크 창(당일~다음 날)", "이미 제출", "대상이 나를 뺀 그룹원 전원과 정확히 일치"
 * 셋이라 목으로 훑는다. 동시 제출은 잠금(findByGroupIdForUpdate)이 담당해 단위 테스트 대상이 아니다.
 */
class AttendanceCheckTest {

    private static final Long ME = 1L;
    private static final String GROUP_ID = "g-1";

    private MatchRequestRepository matchRequestRepository;
    private AttendanceCheckRepository attendanceCheckRepository;
    private AttendanceServiceImpl service;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        attendanceCheckRepository = mock(AttendanceCheckRepository.class);
        service = new AttendanceServiceImpl(attendanceCheckRepository, matchRequestRepository);
    }

    private static MatchRequest matched(long userId, String groupId, LocalDate mealDate) {
        MatchRequest request = MatchRequest.builder()
                .id(userId * 10)
                .user(User.builder().id(userId).email("u" + userId + "@test.com").build())
                .region(Region.GANGNAM)
                .mealTime(MealTime.LUNCH)
                .mealDate(mealDate)
                .status(MatchStatus.WAITING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        request.assignToGroup(groupId);
        return request;
    }

    /** 나(1)와 2, 3으로 이뤄진 3인 그룹 */
    private void givenGroup(LocalDate mealDate) {
        List<MatchRequest> members = List.of(
                matched(1, GROUP_ID, mealDate), matched(2, GROUP_ID, mealDate), matched(3, GROUP_ID, mealDate));
        when(matchRequestRepository.findByGroupIdForUpdate(GROUP_ID)).thenReturn(members);
        when(matchRequestRepository.findByGroupId(GROUP_ID)).thenReturn(members);
        when(matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(ME)).thenReturn(List.of(members.get(0)));
    }

    private static AttendanceCheckRequest checks(long... userIds) {
        return new AttendanceCheckRequest(java.util.Arrays.stream(userIds)
                .mapToObj(id -> new AttendanceCheckRequest.Item(id, id != 3))
                .toList());
    }

    @Test
    @DisplayName("나를 뺀 전원을 체크하면 한 사람당 한 행씩 저장된다")
    @SuppressWarnings("unchecked")
    void submitSavesOneRowPerOtherMember() {
        givenGroup(LocalDate.now());

        service.submit(ME, GROUP_ID, checks(2, 3));

        ArgumentCaptor<List<AttendanceCheck>> saved = ArgumentCaptor.forClass(List.class);
        verify(attendanceCheckRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(c -> c.getChecker().getId(), c -> c.getTarget().getId(), AttendanceCheck::isAttended)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L, true),
                        org.assertj.core.groups.Tuple.tuple(1L, 3L, false));
    }

    @Test
    @DisplayName("다음 날까지는 열려 있고, 그 뒤나 식사 전날에는 닫힌다")
    void windowIsMealDayAndNextDay() {
        givenGroup(LocalDate.now().minusDays(1));
        service.submit(ME, GROUP_ID, checks(2, 3));

        for (LocalDate closed : List.of(LocalDate.now().minusDays(2), LocalDate.now().plusDays(1))) {
            givenGroup(closed);
            assertThatThrownBy(() -> service.submit(ME, GROUP_ID, checks(2, 3)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_NOT_OPEN);
        }
    }

    @Test
    @DisplayName("이미 제출한 그룹은 다시 제출할 수 없다")
    void rejectsSecondSubmit() {
        givenGroup(LocalDate.now());
        when(attendanceCheckRepository.existsByGroupIdAndChecker_Id(GROUP_ID, ME)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(ME, GROUP_ID, checks(2, 3)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_ALREADY_CHECKED);
    }

    @Test
    @DisplayName("일부만, 나 자신, 그룹 밖 사람, 중복이 섞이면 400이고 아무것도 저장하지 않는다")
    void rejectsWrongTargets() {
        givenGroup(LocalDate.now());

        for (AttendanceCheckRequest bad : List.of(checks(2), checks(1, 2, 3), checks(2, 3, 99), checks(2, 2, 3))) {
            assertThatThrownBy(() -> service.submit(ME, GROUP_ID, bad))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
        verify(attendanceCheckRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 404")
    void rejectsNonMember() {
        givenGroup(LocalDate.now());
        when(matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(ME)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(ME, GROUP_ID, checks(2, 3)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MATCH_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("대기 목록에는 체크 창 안이고 아직 안 낸 그룹만 나온다")
    void pendingFiltersWindowAndSubmitted() {
        MatchRequest today = matched(ME, "today", LocalDate.now());
        MatchRequest submitted = matched(ME, "submitted", LocalDate.now());
        MatchRequest tooOld = matched(ME, "old", LocalDate.now().minusDays(2));
        MatchRequest tomorrow = matched(ME, "tomorrow", LocalDate.now().plusDays(1));
        when(matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(ME))
                .thenReturn(List.of(today, submitted, tooOld, tomorrow));
        when(attendanceCheckRepository.existsByGroupIdAndChecker_Id(anyString(), anyLong())).thenReturn(false);
        when(attendanceCheckRepository.existsByGroupIdAndChecker_Id("submitted", ME)).thenReturn(true);
        when(matchRequestRepository.findByGroupId(anyString())).thenReturn(List.of());

        assertThat(service.getPending(ME)).extracting(MatchStatusResponse::getGroupId).containsExactly("today");
    }
}
