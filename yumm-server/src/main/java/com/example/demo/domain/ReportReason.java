package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 신고 사유(FR-S-01). 사용자가 고르는 항목이므로 설명을 그대로 화면 라벨로 쓴다. */
@AllArgsConstructor
@Getter
public enum ReportReason {
    HARASSMENT("괴롭힘·불쾌한 언행"),
    // 밥메이트가 소개팅·영업 채널로 변질되는 걸 막는 항목. 기획이 명시적으로 요구했다.
    OFF_PURPOSE("목적 이탈(영업·홍보·데이팅)"),
    NO_SHOW("약속 불이행"),
    INAPPROPRIATE_PROFILE("부적절한 프로필"),
    OTHER("기타");

    private final String description;
}
