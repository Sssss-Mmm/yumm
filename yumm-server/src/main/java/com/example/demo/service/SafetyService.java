package com.example.demo.service;

import com.example.demo.dto.safety.ReportRequest;

/** 신고(FR-S-01)와 차단(FR-S-02). 둘 다 "상대에 대한 조치"라 한 서비스에 둔다. */
public interface SafetyService {

    /** 신고를 기록한다. 신고했다고 차단되지는 않는다(별개 기능). */
    void report(Long reporterId, ReportRequest request);

    /** 상대를 차단한다. 이미 차단한 상대면 아무 일도 하지 않고 성공한다. */
    void block(Long blockerId, Long blockedUserId);
}
