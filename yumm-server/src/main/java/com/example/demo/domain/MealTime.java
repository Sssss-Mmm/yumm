package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MealTime {
    LUNCH("점심"),
    DINNER("저녁");

    private final String description;
}
