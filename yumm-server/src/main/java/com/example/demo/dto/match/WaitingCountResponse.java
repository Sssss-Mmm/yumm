package com.example.demo.dto.match;

import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.repository.MatchRequestRepository;

import java.time.LocalDate;

/** 한 버킷(지역+날짜+시간대)의 현재 대기 인원(FR-A-10, FR-M-10). */
public record WaitingCountResponse(Region region, LocalDate mealDate, MealTime mealTime, long count) {

    public static WaitingCountResponse from(MatchRequestRepository.BucketCount b) {
        return new WaitingCountResponse(b.getRegion(), b.getMealDate(), b.getMealTime(), b.getCount());
    }
}
