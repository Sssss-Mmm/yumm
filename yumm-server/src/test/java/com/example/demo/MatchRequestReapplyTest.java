package com.example.demo;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 1인 1신청 판정(FR-M-05 / BR-01). Spring 컨텍스트 없이 순수하게 검증한다. */
class MatchRequestReapplyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0);

    private static MatchRequest request(MatchStatus status, LocalDateTime expiresAt) {
        return request(status, expiresAt, NOW.toLocalDate());
    }

    private static MatchRequest request(MatchStatus status, LocalDateTime expiresAt, LocalDate mealDate) {
        return MatchRequest.builder()
                .status(status)
                .mealDate(mealDate)
                .createdAt(NOW.minusMinutes(10))
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("식사 날짜가 남은 매칭은 재신청을 막는다 (두 그룹 동시 배정 방지)")
    void matchedBlocksReapply() {
        // 매칭된 신청은 대기 만료시각(expiresAt)이 지났어도 막아야 한다. 기준은 식사 날짜다.
        assertThat(request(MatchStatus.MATCHED, NOW.plusMinutes(20)).blocksReapply(NOW)).isTrue();
        assertThat(request(MatchStatus.MATCHED, NOW.minusMinutes(1)).blocksReapply(NOW)).isTrue();
        // 내일 식사도 아직 안 지났으므로 막는다
        assertThat(request(MatchStatus.MATCHED, NOW.minusMinutes(1), NOW.toLocalDate().plusDays(1))
                .blocksReapply(NOW)).isTrue();
    }

    @Test
    @DisplayName("식사 날짜가 지난 매칭은 재신청을 허용한다 (영구 차단 방지)")
    void pastMatchedAllowsReapply() {
        assertThat(request(MatchStatus.MATCHED, NOW.minusMinutes(1), NOW.toLocalDate().minusDays(1))
                .blocksReapply(NOW)).isFalse();
        // 한 달 전 매칭도 당연히 안 막는다
        assertThat(request(MatchStatus.MATCHED, NOW.minusMinutes(1), NOW.toLocalDate().minusMonths(1))
                .blocksReapply(NOW)).isFalse();
    }

    @Test
    @DisplayName("아직 유효한 대기 신청은 재신청을 막는다")
    void activeWaitingBlocksReapply() {
        assertThat(request(MatchStatus.WAITING, NOW.plusMinutes(20)).blocksReapply(NOW)).isTrue();
    }

    @Test
    @DisplayName("만료된 대기와 취소된 신청은 재신청을 허용한다")
    void expiredOrCancelledAllowsReapply() {
        assertThat(request(MatchStatus.WAITING, NOW.minusMinutes(1)).blocksReapply(NOW)).isFalse();
        assertThat(request(MatchStatus.CANCELLED, NOW.plusMinutes(20)).blocksReapply(NOW)).isFalse();
    }
}
