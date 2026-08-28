package com.example.demo.service;

import com.example.demo.domain.MatchStatus;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.MatchRequestRepository.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주기적으로 대기열을 훑어 그룹을 편성한다(FR-G-06).
 *
 * 신청 시점에는 편성하지 않는다. 그래야 마지막 신청자도 다음 주기에 묶이고,
 * 편성 중 예외가 신청 트랜잭션을 롤백시키지 않는다(NFR-08).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {

    private static final long INTERVAL_MS = 30_000;

    private final MatchRequestRepository matchRequestRepository;
    private final MatchService matchService;

    /**
     * 대기자가 있는 버킷을 모두 돌며 편성한다. 버킷 하나가 트랜잭션 하나다.
     *
     * ponytail: 인스턴스가 여러 대가 되면 같은 버킷을 동시에 돌게 되지만
     * findWaitingInBucket의 행 잠금이 중복 배정을 막는다. 중복 실행 자체가 부담이 되면
     * 그때 Redis 락(이미 의존성에 있다)을 얹으면 된다.
     */
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void formGroups() {
        // 버킷 목록 조회까지 격리 안에 둔다. 이 조회가 터지면(예: region 컬럼에 enum 밖 값)
        // 정상 버킷까지 편성이 멈춘다. 실패한 주기는 다음 주기에 그대로 다시 시도된다.
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Bucket> buckets = matchRequestRepository.findWaitingBuckets(now);
            long waiting = matchRequestRepository.countByStatusAndExpiresAtAfter(MatchStatus.WAITING, now);

            int formed = 0;
            for (Bucket bucket : buckets) {
                try {
                    formed += matchService.formGroupsInBucket(bucket.getRegion(), bucket.getMealDate(), bucket.getMealTime());
                } catch (RuntimeException e) {
                    // 한 버킷이 실패해도 나머지는 편성한다. 실패한 버킷은 다음 주기에 다시 시도된다.
                    log.warn("[매칭 편성] 버킷 실패 region={} date={} time={}",
                            bucket.getRegion(), bucket.getMealDate(), bucket.getMealTime(), e);
                }
            }

            log.info("[매칭 편성] 버킷 {}개, 대기자 {}명, 그룹 {}개 편성", buckets.size(), waiting, formed);
        } catch (RuntimeException e) {
            // 스케줄러 밖으로 예외를 내보내지 않는다. @Scheduled이 남기는 로그로는 원인 추적이 안 된다.
            log.error("[매칭 편성] 주기 실패, 다음 주기에 재시도", e);
        }
    }
}
