package com.example.demo;

import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.MatchRequestRepository.Bucket;
import com.example.demo.service.MatchScheduler;
import com.example.demo.service.MatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 편성 스케줄러(FR-G-06). 버킷 하나가 실패해도 나머지는 편성돼야 한다.
 */
class MatchSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    private MatchRequestRepository matchRequestRepository;
    private MatchService matchService;
    private MatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        matchService = mock(MatchService.class);
        scheduler = new MatchScheduler(matchRequestRepository, matchService);
        when(matchRequestRepository.countByStatusAndExpiresAtAfter(eq(MatchStatus.WAITING), any())).thenReturn(6L);
    }

    /** Bucket은 값 3개짜리 프로젝션이라 목보다 직접 구현이 짧다. */
    private static Bucket bucket(Region region, MealTime mealTime) {
        return new Bucket() {
            @Override public Region getRegion() { return region; }
            @Override public LocalDate getMealDate() { return TODAY; }
            @Override public MealTime getMealTime() { return mealTime; }
        };
    }

    @Test
    @DisplayName("대기자가 있는 버킷을 모두 편성한다 (신규 신청 없이도 돈다)")
    void formsEveryBucket() {
        when(matchRequestRepository.findWaitingBuckets(any(LocalDateTime.class)))
                .thenReturn(List.of(bucket(Region.GANGNAM, MealTime.LUNCH), bucket(Region.PANGYO, MealTime.DINNER)));

        scheduler.formGroups();

        verify(matchService).formGroupsInBucket(Region.GANGNAM, TODAY, MealTime.LUNCH);
        verify(matchService).formGroupsInBucket(Region.PANGYO, TODAY, MealTime.DINNER);
    }

    @Test
    @DisplayName("한 버킷이 실패해도 나머지 버킷은 편성한다")
    void oneFailingBucketDoesNotBlockOthers() {
        when(matchRequestRepository.findWaitingBuckets(any(LocalDateTime.class)))
                .thenReturn(List.of(
                        bucket(Region.GANGNAM, MealTime.LUNCH),
                        bucket(Region.HONGDAE, MealTime.LUNCH),
                        bucket(Region.PANGYO, MealTime.DINNER)));
        when(matchService.formGroupsInBucket(Region.HONGDAE, TODAY, MealTime.LUNCH))
                .thenThrow(new IllegalStateException("락 획득 실패"));

        scheduler.formGroups(); // 예외가 밖으로 새어 나가면 안 된다

        verify(matchService).formGroupsInBucket(Region.GANGNAM, TODAY, MealTime.LUNCH);
        verify(matchService).formGroupsInBucket(Region.PANGYO, TODAY, MealTime.DINNER);
        verify(matchService, times(3)).formGroupsInBucket(any(), any(), any());
    }

    @Test
    @DisplayName("버킷 목록 조회가 실패해도 스케줄러가 죽지 않고 다음 주기에 복구된다")
    void bucketLookupFailureRecoversNextCycle() {
        // 레거시 region 값 하나로 조회가 통째로 터지는 상황(No enum constant ...)
        when(matchRequestRepository.findWaitingBuckets(any(LocalDateTime.class)))
                .thenThrow(new IllegalArgumentException("No enum constant com.example.demo.domain.Region.강남"))
                .thenReturn(List.of(bucket(Region.GANGNAM, MealTime.LUNCH)));

        scheduler.formGroups(); // 예외가 밖으로 새어 나가면 안 된다
        verify(matchService, never()).formGroupsInBucket(any(), any(), any());

        scheduler.formGroups(); // 다음 주기
        verify(matchService).formGroupsInBucket(Region.GANGNAM, TODAY, MealTime.LUNCH);
    }
}
