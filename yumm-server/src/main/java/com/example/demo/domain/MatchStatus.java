package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MatchStatus {
    WAITING("대기중"),
    MATCHED("매칭완료"),
    CANCELLED("취소됨");

    private final String description;
}
