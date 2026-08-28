package com.example.demo.service;

import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.dto.match.MatchStatusResponse;

import java.time.LocalDate;

public interface MatchService {

    /** 매칭 신청을 대기열에 넣는다. 편성은 MatchScheduler가 하므로 응답은 항상 대기 상태다. */
    MatchStatusResponse apply(Long userId, MatchApplyRequest request);

    /**
     * 한 버킷(지역+날짜+시간대)의 대기자들을 3~4인 그룹으로 묶고 만들어진 그룹 수를 돌려준다.
     * 스케줄러가 버킷마다 호출한다(버킷당 트랜잭션 1개).
     */
    int formGroupsInBucket(Region region, LocalDate mealDate, MealTime mealTime);

    /** 내 최근 매칭 신청의 현재 상태 */
    MatchStatusResponse getMyStatus(Long userId);

    /** 대기 중인 매칭 신청 취소 */
    void cancel(Long userId);

    /**
     * 매칭된 그룹에서 이탈한다(FR-C-02). 남은 인원이 최소 인원 미달이면 그룹을 해체하고
     * 남은 구성원을 대기열로 되돌린다(FR-C-03).
     */
    void leaveGroup(Long userId);
}
