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

    /**
     * 오늘 식사 예정인 성사된 신청 전부(FR-N-04 리마인드 대상).
     *
     * 이미 보낸 행(remindedAt != null)까지 같이 읽는다. 그래야 그룹 인원 수를 이 결과만으로 셀 수 있다 —
     * 보낸 행을 빼면 일부만 발송된 그룹의 인원이 실제보다 적게 나온다. 발송 여부 판정은 서비스가 한다.
     * 메일 주소를 쓰므로 user를 같이 읽는다(그러지 않으면 대상 수만큼 추가 쿼리가 나간다).
     */
    @Query("""
            SELECT m FROM MatchRequest m
            JOIN FETCH m.user
            WHERE m.status = com.example.demo.domain.MatchStatus.MATCHED
              AND m.mealDate = :today
              AND m.groupId IS NOT NULL
            """)
    List<MatchRequest> findMatchedOnDate(@Param("today") LocalDate today);

    /** 편성 지표 로그용 대기자 총원 */
    long countByStatusAndExpiresAtAfter(MatchStatus status, LocalDateTime now);

    /** 사용자의 가장 최근 매칭 신청 1건 */
    Optional<MatchRequest> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, MatchStatus status);

    /** 같은 groupId를 가진 행들이 곧 한 그룹 */
    List<MatchRequest> findByGroupId(String groupId);

    /**
     * 이탈 처리용. 그룹 구성원 행을 쓰기 잠금해서 읽는다.
     *
     * 잠그지 않으면 같은 그룹에서 두 명이 동시에 이탈할 때 뒤 트랜잭션이 앞 트랜잭션의
     * 커밋을 못 보고, 이미 나간 사람을 낡은 스냅샷 그대로 WAITING으로 되살린다.
     * 조회 전용 경로(getMyStatus)는 잠글 이유가 없으므로 findByGroupId를 그대로 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MatchRequest m WHERE m.groupId = :groupId")
    List<MatchRequest> findByGroupIdForUpdate(@Param("groupId") String groupId);

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
