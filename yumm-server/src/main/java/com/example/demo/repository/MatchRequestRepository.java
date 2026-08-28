package com.example.demo.repository;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    /**
     * 한 버킷(지역+날짜+시간대)에서 아직 유효한 대기자들.
     * 만료된 신청은 여기서 걸러지므로 별도의 만료 처리 배치가 필요 없다.
     *
     * 동시에 두 명이 신청하면 같은 대기자를 각자의 그룹에 넣어버릴 수 있어
     * 조회한 행을 쓰기 잠금한다. synchronized로는 트랜잭션 커밋 전에 락이 풀려서 막지 못한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m FROM MatchRequest m
            WHERE m.status = com.example.demo.domain.MatchStatus.WAITING
              AND m.region = :region
              AND m.mealDate = :mealDate
              AND m.mealTime = :mealTime
              AND m.expiresAt > :now
            """)
    List<MatchRequest> findWaitingInBucket(@Param("region") Region region,
                                          @Param("mealDate") LocalDate mealDate,
                                          @Param("mealTime") MealTime mealTime,
                                          @Param("now") LocalDateTime now);

    /**
     * 편성이 필요한 버킷(지역+날짜+시간대) 목록. 스케줄러가 이 목록을 돌며 편성한다.
     * 대기자가 한 명도 없는 버킷은 애초에 나오지 않으므로 별도 필터가 필요 없다.
     */
    @Query("""
            SELECT DISTINCT m.region AS region, m.mealDate AS mealDate, m.mealTime AS mealTime
            FROM MatchRequest m
            WHERE m.status = com.example.demo.domain.MatchStatus.WAITING
              AND m.expiresAt > :now
            """)
    List<Bucket> findWaitingBuckets(@Param("now") LocalDateTime now);

    /** 편성 지표 로그용 대기자 총원 */
    long countByStatusAndExpiresAtAfter(MatchStatus status, LocalDateTime now);

    /** 사용자의 가장 최근 매칭 신청 1건 */
    Optional<MatchRequest> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, MatchStatus status);

    /** 같은 groupId를 가진 행들이 곧 한 그룹 */
    List<MatchRequest> findByGroupId(String groupId);

    /**
     * 그 그룹(=채팅방)의 구성원인지. 채팅 구독/발신(STOMP)과 지난 대화 조회(REST)가
     * 같은 판정을 써야 하므로 여기 한 곳에만 둔다.
     */
    boolean existsByGroupIdAndUser_Id(String groupId, Long userId);

    /** findWaitingBuckets 결과를 받는 프로젝션 (버킷 키 3개) */
    interface Bucket {
        Region getRegion();
        LocalDate getMealDate();
        MealTime getMealTime();
    }
}
