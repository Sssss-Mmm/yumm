package com.example.demo.service;

import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.dto.match.MatchStatusResponse;

public interface MatchService {

    /** 매칭 신청 후 즉시 그룹 편성을 시도한다. 못 묶이면 대기 상태로 남는다. */
    MatchStatusResponse apply(Long userId, MatchApplyRequest request);

    /** 내 최근 매칭 신청의 현재 상태 */
    MatchStatusResponse getMyStatus(Long userId);

    /** 대기 중인 매칭 신청 취소 */
    void cancel(Long userId);
}
