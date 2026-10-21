package com.example.demo;

import com.example.demo.domain.AttendanceCheck;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.domain.User;
import com.example.demo.dto.admin.AdminStatsResponse;
import com.example.demo.service.MatchStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 지표(FR-D-03)와 노쇼 판정 규칙(FR-S-03). 순수 계산이라 데이터만 만들어 넣는다.
 */
class MatchStatsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final LocalDate DAY = NOW.toLocalDate();

    private static MatchRequest request(long userId, MatchStatus status, String groupId,
                                        LocalDateTime createdAt, LocalDateTime matchedAt) {
        return MatchRequest.builder()
                .id(userId)
                .user(User.builder().id(userId).build())
                .region(Region.GANGNAM)
                .mealDate(DAY)
                .mealTime(MealTime.LUNCH)
                .status(status)
                .groupId(groupId)
                .createdAt(createdAt)
                .expiresAt(createdAt.plusMinutes(30))
                .matchedAt(matchedAt)
                .build();
    }

    /** 신청 10분 뒤 성사된 그룹 구성원들 */
    private static List<MatchRequest> group(String groupId, long... userIds) {
        List<MatchRequest> members = new ArrayList<>();
        for (long id : userIds) {
            members.add(request(id, MatchStatus.MATCHED, groupId, NOW.minusHours(2), NOW.minusHours(2).plusMinutes(10)));
        }
        return members;
    }

    private static AttendanceCheck check(String groupId, long checker, long target, boolean attended) {
        return AttendanceCheck.builder().groupId(groupId)
                .checker(User.builder().id(checker).build())
                .target(User.builder().id(target).build())
                .attended(attended).createdAt(NOW).build();
    }

    @Test
    @DisplayName("노쇼는 '안 왔어요' 2명 이상이면서 응답자 과반일 때만이다")
    void noShowRule() {
        assertThat(MatchStats.isNoShow(1, 1)).isFalse(); // 2인 그룹: 상대 한 명의 말만으로는 판정하지 않는다
        assertThat(MatchStats.isNoShow(1, 3)).isFalse();
        assertThat(MatchStats.isNoShow(2, 2)).isTrue();
        assertThat(MatchStats.isNoShow(2, 3)).isTrue();
        assertThat(MatchStats.isNoShow(2, 4)).isFalse(); // 동수는 과반이 아니다
    }

    @Test
    @DisplayName("성사율은 끝난 신청만 분모로 쓰고, 만료는 실패·진행 중 대기는 제외한다")
    void funnelExcludesActiveWaiting() {
        List<MatchRequest> requests = new ArrayList<>(group("g", 1, 2, 3));
        requests.add(request(4, MatchStatus.WAITING, null, NOW.minusHours(1), null));   // 만료 → 실패
        requests.add(request(5, MatchStatus.WAITING, null, NOW.minusMinutes(5), null)); // 아직 대기 → 제외
        // 성사 뒤 이탈: groupId는 지워졌지만 matchedAt이 남아 성사로 센다
        requests.add(request(6, MatchStatus.CANCELLED, null, NOW.minusHours(2), NOW.minusHours(2).plusMinutes(20)));

        AdminStatsResponse.Funnel funnel = MatchStats.compute(DAY, DAY, requests, List.of(), NOW).funnel();

        assertThat(funnel.requests()).isEqualTo(5);
        assertThat(funnel.matched()).isEqualTo(4);
        assertThat(funnel.matchRate()).isEqualTo(80.0);
        assertThat(funnel.avgWaitMinutes()).isEqualTo(12.5); // (10 + 10 + 10 + 20) / 4
    }

    @Test
    @DisplayName("만남 전환율·노쇼는 응답이 있는 그룹으로 세고, 2인과 3~4인을 따로 낸다")
    void meetings() {
        List<MatchRequest> requests = new ArrayList<>();
        requests.addAll(group("four", 1, 2, 3, 4));  // 4번은 셋 중 둘이 "안 왔어요" → 노쇼
        requests.addAll(group("pair", 5, 6));        // 6번은 상대 한 명이 "안 왔어요" → 의심만
        requests.addAll(group("off", 7, 8, 9));      // 전원 서로 "안 왔어요" → 무산, 개인 노쇼 아님
        requests.addAll(group("silent", 10, 11, 12)); // 응답 없음

        List<AttendanceCheck> checks = List.of(
                check("four", 1, 2, true), check("four", 1, 3, true), check("four", 1, 4, false),
                check("four", 2, 1, true), check("four", 2, 3, true), check("four", 2, 4, false),
                check("four", 3, 1, true), check("four", 3, 2, true), check("four", 3, 4, true),
                check("pair", 5, 6, false),
                check("off", 7, 8, false), check("off", 7, 9, false), check("off", 8, 7, false));

        AdminStatsResponse.Meetings m = MatchStats.compute(DAY, DAY, requests, checks, NOW).meetings();

        assertThat(m.groups()).isEqualTo(4);
        assertThat(m.pairGroups()).isEqualTo(1);
        assertThat(m.pairShare()).isEqualTo(25.0);
        assertThat(m.respondedGroups()).isEqualTo(3);
        assertThat(m.metGroups()).isEqualTo(1); // "pair"는 "왔어요"가 없어 무산과 같다
        assertThat(m.conversionRate()).isEqualTo(33.3);

        // four: 1·2·3·4 모두 응답이 있다(5번 그룹원 없음). 노쇼 1명, 의심 포함 1명.
        assertThat(m.group().judged()).isEqualTo(4);
        assertThat(m.group().noShows()).isEqualTo(1);
        assertThat(m.group().noShowRate()).isEqualTo(25.0);
        // pair는 "왔어요"가 한 건도 없어 무산으로 빠진다 — 판정 대상 0명, 비율 null
        assertThat(m.pair().judged()).isZero();
        assertThat(m.pair().flaggedRate()).isNull();
    }
}
