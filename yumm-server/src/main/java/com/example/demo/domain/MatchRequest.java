package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 밥메이트 매칭 신청 1건(= 대기열의 한 자리).
 *
 * 지역/날짜/시간대는 서로 정확히 일치해야만 같이 먹을 수 있는 하드 조건이라
 * 이 셋으로 대기열을 버킷으로 나눈다. 성별 조건은 상대에 따라 달라지는 관계형
 * 조건이라 버킷 키가 될 수 없고, 그룹에 합류하는 시점에 검사한다.
 */
@Entity
@Table(name = "match_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region; // 하드 조건: 지역

    @Column(nullable = false)
    private LocalDate mealDate; // 하드 조건: 식사 날짜

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MealTime mealTime; // 하드 조건: 점심/저녁

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GenderPreference genderPreference; // 하드 조건이지만 관계형 -> 합류 시 검사

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "match_request_foods", joinColumns = @JoinColumn(name = "match_request_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "food", length = 20)
    @Builder.Default
    private Set<FoodCategory> foodPreferences = new HashSet<>(); // 소프트 조건: 겹칠수록 높은 점수

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status;

    // ponytail: 그룹은 이 컬럼으로만 표현한다. 같은 groupId를 가진 행들이 곧 한 그룹이라
    // MatchGroup 엔티티가 필요 없다. 그룹 자체에 속성(식당, 확정 여부)이 생기면 그때 승격.
    @Column(length = 36)
    private String groupId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt; // 지나면 MATCHING_TIMEOUT. 별도 만료 배치 없이 조회 시 걸러낸다.

    public void assignToGroup(String groupId) {
        this.groupId = groupId;
        this.status = MatchStatus.MATCHED;
    }

    public void cancel() {
        this.status = MatchStatus.CANCELLED;
    }

    /**
     * 매칭된 그룹에서 이탈한다(FR-C-02).
     * groupId를 비우는 것만으로 그룹 채팅 구독/발신/조회 판정(existsByGroupIdAndUser_Id)에서도 빠진다.
     */
    public void leaveGroup() {
        this.groupId = null;
        cancel();
    }

    /**
     * 그룹이 최소 인원 미달로 해체돼 다시 대기열로 돌아간다(FR-C-03).
     * 만료 시각을 새로 받아야 이미 지난 expiresAt 때문에 곧바로 만료되지 않는다.
     */
    public void returnToWaiting(LocalDateTime expiresAt) {
        this.groupId = null;
        this.status = MatchStatus.WAITING;
        this.expiresAt = expiresAt;
    }

    /**
     * 이미 지나간 끼니인지. 지난 끼니는 다시 매칭해 줄 이유가 없다.
     *
     * ponytail: 날짜 단위로만 본다. MealTime(점심/저녁)에서 시각을 역산하지 않는 건
     * 만날 시각을 시스템이 정하지 않는다는 기획 결정이라서다(product-plan 8절).
     * rejectIfInvalidMealDate, blocksReapply도 같은 날짜 단위라 판정이 일관된다.
     * 한계: 오늘 점심 그룹을 오후에 이탈하면 남은 인원은 여전히 대기열로 돌아간다.
     */
    public boolean isPastMeal(LocalDate today) {
        return mealDate.isBefore(today);
    }

    public boolean isExpired(LocalDateTime now) {
        return status == MatchStatus.WAITING && expiresAt.isBefore(now);
    }

    /**
     * 이 신청 때문에 새 신청을 막아야 하는지. (FR-M-05 / BR-01: 1인 1신청)
     * 매칭이 잡힌 사람은 재신청으로 두 그룹에 동시 배정되면 안 되므로 MATCHED도 막되,
     * 식사 날짜가 지나면 푼다. 취소했거나 만료된 신청은 막지 않는다.
     */
    public boolean blocksReapply(LocalDateTime now) {
        if (status == MatchStatus.MATCHED) {
            // ponytail: 날짜 비교 하나로 MATCHED를 만료시킨다. 종료 상태값도, 만료 배치도, 새 컬럼도 필요 없다.
            // 한계: 오늘 점심에 매칭된 사람은 오늘 저녁을 신청할 수 없다(하루 단위라 시간대까지 못 본다).
            // 두 그룹 동시 배정보다는 보수적인 쪽이 낫다고 보고 감수한다.
            // 업그레이드 경로: 당일 재신청은 FR-C-02(그룹 이탈)가 수동 해제를 담당한다.
            return !mealDate.isBefore(now.toLocalDate());
        }
        return status == MatchStatus.WAITING && !isExpired(now);
    }
}
