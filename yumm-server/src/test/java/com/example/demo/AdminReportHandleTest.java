package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportReason;
import com.example.demo.domain.User;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 신고 처리(FR-D-01).
 *
 * 분기가 둘이다. 목록은 includeHandled에 따라 다른 쿼리를 타야 하고,
 * 처리 표시는 이미 처리된 신고의 최초 시각을 덮어쓰면 안 된다.
 */
class AdminReportHandleTest {

    private ReportRepository reportRepository;
    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        service = new AdminServiceImpl(reportRepository);
    }

    private static User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .email(nickname + "@yumm.test")
                .password("hashed")
                .nickname(nickname)
                .gender(Gender.FEMALE)
                .birthYear(1996)
                .build();
    }

    private static Report report(Long id, LocalDateTime handledAt) {
        return new Report(id, user(1L, "신고자"), user(2L, "피신고자"),
                ReportReason.HARASSMENT, "상세", LocalDateTime.now().minusHours(1), handledAt);
    }

    @Test
    @DisplayName("기본 목록은 미처리 신고만 접수 순으로 가져온다")
    void 미처리만_조회() {
        when(reportRepository.findByHandledAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(report(1L, null)));

        var result = service.getReports(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHandledAt()).isNull();
        verify(reportRepository).findByHandledAtIsNullOrderByCreatedAtAsc();
    }

    @Test
    @DisplayName("includeHandled면 처리분까지 최근 순으로 가져온다")
    void 전체_조회() {
        when(reportRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(report(1L, LocalDateTime.now()), report(2L, null)));

        var result = service.getReports(true);

        assertThat(result).hasSize(2);
        verify(reportRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("미처리 신고를 처리하면 처리 시각이 찍힌다")
    void 처리_표시() {
        Report target = report(1L, null);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(target));

        var result = service.handleReport(1L);

        assertThat(result.getHandledAt()).isNotNull();
        assertThat(target.getHandledAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 처리된 신고를 다시 처리해도 최초 처리 시각이 유지된다")
    void 재처리는_시각을_덮어쓰지_않는다() {
        LocalDateTime first = LocalDateTime.now().minusDays(1);
        Report target = report(1L, first);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(target));

        var result = service.handleReport(1L);

        assertThat(result.getHandledAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("없는 신고를 처리하면 REPORT_NOT_FOUND")
    void 없는_신고() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleReport(99L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND);
    }
}
