package com.example.demo.domain; // 또는 com.example.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor // 각 enum 상수에 값을 주입하기 위해 필요
@Getter // description 값을 가져오기 위한 getter
public enum Gender {
    MALE("남성"),
    FEMALE("여성"),
    UNKNOWN("알 수 없음"); // 성별을 입력하지 않았거나 알 수 없는 경우

    private final String description; // 성별에 대한 설명 또는 표시 이름
}