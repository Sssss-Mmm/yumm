package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 같이 밥 먹을 상대의 성별 조건. 쌍방이 서로를 받아들여야 그룹이 성사된다. */
@AllArgsConstructor
@Getter
public enum GenderPreference {
    SAME_ONLY("동성만"),
    ANY("상관없음");

    private final String description;
}
