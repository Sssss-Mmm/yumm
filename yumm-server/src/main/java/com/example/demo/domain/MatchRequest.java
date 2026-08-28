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

    @Column(nullable = false, length = 50)
    private String region; // 하드 조건: 지역

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

    public boolean isExpired(LocalDateTime now) {
        return status == MatchStatus.WAITING && expiresAt.isBefore(now);
    }
}
