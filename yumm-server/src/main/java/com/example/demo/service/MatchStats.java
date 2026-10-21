package com.example.demo.service;

import com.example.demo.domain.AttendanceCheck;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.dto.admin.AdminStatsResponse;
import com.example.demo.dto.admin.AdminStatsResponse.Bucket;
import com.example.demo.dto.admin.AdminStatsResponse.Funnel;
import com.example.demo.dto.admin.AdminStatsResponse.Meetings;
import com.example.demo.dto.admin.AdminStatsResponse.NoShow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 운영 지표 계산(FR-D-03). 저장소를 모르는 순수 로직이라 GroupMatcher처럼 데이터만 받아 숫자를 낸다.
 *
 * ponytail: 기간 안의 행을 전부 메모리로 읽어 센다. 1개 지역 베타에서 하루 수백 행이면 충분하다.
 * 기간이 길어지거나 지역이 늘어 느려지면 그때 SQL 집계로 내린다.
 */
public final class MatchStats {

    private MatchStats() {}

    public static AdminStatsResponse compute(LocalDate from, LocalDate to, List<MatchRequest> requests,
                                             List<AttendanceCheck> checks, LocalDateTime now) {
        // 아직 대기 중인 신청은 성사될지 모르므로 분모에서 뺀다. 만료된 대기(TIMEOUT)는 "실패"로 센다.
        List<MatchRequest> finished = requests.stream()
                .filter(m -> !(m.getStatus() == MatchStatus.WAITING && !m.isExpired(now)))
                .toList();

        List<Bucket> buckets = finished.stream()
                .collect(Collectors.groupingBy(m -> Map.entry(m.getRegion(), m.getMealTime())))
                .entrySet().stream()
                .map(e -> new Bucket(e.getKey().getKey(), e.getKey().getValue(), funnel(e.getValue())))
                .sorted(Comparator.comparing((Bucket b) -> b.region()).thenComparing(Bucket::mealTime))
                .toList();

        return new AdminStatsResponse(from, to, funnel(finished), buckets, meetings(requests, checks));
    }

    private static Funnel funnel(List<MatchRequest> finished) {
        List<MatchRequest> matched = finished.stream().filter(MatchStats::everMatched).toList();
        Double avgWait = matched.stream()
                .filter(m -> m.getMatchedAt() != null)
                .mapToLong(m -> Duration.between(m.getCreatedAt(), m.getMatchedAt()).toSeconds())
                .average()
                .stream().mapToObj(sec -> Math.round(sec / 6.0) / 10.0) // 분, 소수 첫째 자리
                .findFirst().orElse(null);
        return new Funnel(finished.size(), matched.size(), rate(matched.size(), finished.size()), avgWait);
    }

    /**
     * matched_at은 FR-D-03과 함께 생긴 컬럼이라 그 전에 성사된 행엔 없다. 그 행들은 아직 그룹에 있으면(groupId)
     * 성사로 센다. 그 전에 성사됐다가 이탈한 행은 취소와 구분할 수 없어 실패로 잡힌다.
     */
    private static boolean everMatched(MatchRequest m) {
        return m.getMatchedAt() != null || m.getGroupId() != null;
    }

    /** 그룹 = 같은 groupId를 가진 현재 행들. 이탈자는 groupId가 지워져 여기 안 들어온다. */
    private static Meetings meetings(List<MatchRequest> requests, List<AttendanceCheck> checks) {
        Map<String, List<MatchRequest>> groups = requests.stream()
                .filter(m -> m.getGroupId() != null)
                .collect(Collectors.groupingBy(MatchRequest::getGroupId));
        Map<String, List<AttendanceCheck>> checksByGroup = checks.stream()
                .collect(Collectors.groupingBy(AttendanceCheck::getGroupId));

        long pairGroups = 0, responded = 0, met = 0;
        Tally pair = new Tally(), group = new Tally();
        for (Map.Entry<String, List<MatchRequest>> g : groups.entrySet()) {
            boolean isPair = g.getValue().size() == GroupMatcher.PAIR_SIZE;
            if (isPair) pairGroups++;

            List<AttendanceCheck> gc = checksByGroup.getOrDefault(g.getKey(), List.of());
            if (gc.isEmpty()) continue;
            responded++;
            if (gc.stream().anyMatch(AttendanceCheck::isAttended)) {
                met++;
            } else {
                // 응답자 전원이 서로 "안 왔어요" — 합의 취소로 보고 개인 노쇼를 매기지 않는다(FR-S-03 ④).
                continue;
            }

            Tally tally = isPair ? pair : group;
            for (MatchRequest member : g.getValue()) {
                Long target = member.getUser().getId();
                List<AttendanceCheck> about = gc.stream().filter(c -> c.getTarget().getId().equals(target)).toList();
                if (about.isEmpty()) continue; // 판정 불가(FR-S-03 ③)
                long no = about.stream().filter(c -> !c.isAttended()).count();
                tally.judged++;
                if (isNoShow(no, about.size())) tally.noShows++;
                if (no > 0) tally.flagged++;
            }
        }

        return new Meetings(groups.size(), pairGroups, rate(pairGroups, groups.size()),
                responded, met, rate(met, responded), pair.toNoShow(), group.toNoShow());
    }

    /** 노쇼 판정(FR-S-03 ①): "안 왔어요" 2명 이상이면서 응답자 과반. 페널티(FR-S-03)도 이 판정을 쓴다. */
    public static boolean isNoShow(long notAttended, long responses) {
        return notAttended >= 2 && notAttended * 2 > responses;
    }

    private static Double rate(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 10.0; // %, 소수 첫째 자리
    }

    private static final class Tally {
        long judged, noShows, flagged;

        NoShow toNoShow() {
            return new NoShow(judged, noShows, flagged, rate(noShows, judged), rate(flagged, judged));
        }
    }
}
