package com.example.demo.dto.admin;

import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;

import java.time.LocalDate;
import java.util.List;

/**
 * 운영 지표(FR-D-03). 비율은 분모가 0이면 null이다 — 0%와 "잴 게 없음"을 구분한다.
 *
 * @param funnel  전체 신청 → 성사
 * @param buckets 지역·끼니별 신청 → 성사. 날짜는 묶지 않는다(기간 전체 합)
 * @param meetings 성사된 그룹 → 실제 만남·노쇼(FR-C-06 체크 기반, 판정 규칙은 FR-S-03)
 */
public record AdminStatsResponse(LocalDate from, LocalDate to, Funnel funnel, List<Bucket> buckets, Meetings meetings) {

    /**
     * @param requests       끝난 신청 수. 아직 대기 시간이 남은 신청은 결과를 모르므로 뺀다
     * @param matched        한 번이라도 그룹에 묶인 신청 수
     * @param avgWaitMinutes 신청 → 첫 성사까지 평균 분. matched_at이 있는 신청만 센다
     */
    public record Funnel(long requests, long matched, Double matchRate, Double avgWaitMinutes) {}

    public record Bucket(Region region, MealTime mealTime, Funnel funnel) {}

    /**
     * @param groups          성사된 그룹 수
     * @param pairGroups      그중 2인 그룹 수
     * @param pairShare       2인 비중(목표 30% 이하)
     * @param respondedGroups 체크가 1건이라도 들어온 그룹 수
     * @param metGroups       "왔어요"가 1건이라도 있는 그룹 수
     * @param conversionRate  만남 전환율 = metGroups / respondedGroups (목표 70% 이상)
     * @param pair            2인 그룹의 노쇼
     * @param group           3~4인 그룹의 노쇼
     */
    public record Meetings(long groups, long pairGroups, Double pairShare,
                           long respondedGroups, long metGroups, Double conversionRate,
                           NoShow pair, NoShow group) {}

    /**
     * @param judged      판정 대상 인원. 자기에 대한 응답이 1건 이상이고, 무산된 그룹이 아닌 사람
     * @param noShows     노쇼 판정 인원("안 왔어요" 2명 이상 + 응답자 과반). 페널티 대상
     * @param flagged     "안 왔어요"가 1건이라도 있는 인원(노쇼 + 의심). 2인은 판정이 불가능해 이걸로 비교한다
     * @param noShowRate  noShows / judged (목표 15% 이하)
     * @param flaggedRate flagged / judged. 2인 vs 3~4인 격차(2배 이내) 비교용
     */
    public record NoShow(long judged, long noShows, long flagged, Double noShowRate, Double flaggedRate) {}
}
