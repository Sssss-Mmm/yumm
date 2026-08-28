package com.example.demo.dto.match;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.GenderPreference;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Getter
@NoArgsConstructor
public class MatchApplyRequest {

    @NotNull(message = "지역은 필수입니다.")
    private Region region;

    @NotNull(message = "식사 날짜는 필수입니다.")
    private LocalDate mealDate;

    @NotNull(message = "점심/저녁을 선택해 주세요.")
    private MealTime mealTime;

    @NotNull(message = "성별 조건을 선택해 주세요.")
    private GenderPreference genderPreference;

    @NotEmpty(message = "선호 음식을 하나 이상 선택해 주세요.")
    private Set<FoodCategory> foodPreferences;
}
