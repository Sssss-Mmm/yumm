package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 매칭 버킷 키가 되는 지역(FR-M-02).
 *
 * 자유 문자열이면 "강남"과 "강남구"가 서로 다른 버킷이 되어 매칭이 조용히 실패한다.
 * 고정 선택지로 두어 요청 파싱 단계에서 걸러낸다.
 *
 * ponytail: 목록 조회 API는 두지 않는다. 지역 목록은 프론트 상수와 1:1로 유지한다.
 */
@AllArgsConstructor
@Getter
public enum Region {
    GANGNAM("강남"),
    HONGDAE("홍대"),
    SINCHON("신촌"),
    KONDAE("건대"),
    JONGNO("종로"),
    YEOUIDO("여의도"),
    PANGYO("판교");

    private final String description;
}
