package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 참석 상호 체크 1건(FR-C-06). "checker가 보기에 target이 그 그룹 식사에 나왔는가".
 *
 * 노쇼 판정(FR-S-03)과 전환율 지표(FR-D-03)의 원천 데이터다. 판정 규칙(몇 명이 "안 왔다"고 해야 노쇼인지)은
 * 여기 두지 않는다 — 원본 응답만 남기고 해석은 집계하는 쪽이 한다.
 * 한 사람이 한 그룹에서 같은 상대를 두 번 체크하지 않도록 유니크 제약을 건다.
 */
@Entity
@Table(name = "attendance_checks",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_checks",
                columnNames = {"group_id", "checker_id", "target_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AttendanceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checker_id", nullable = false)
    private User checker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Column(nullable = false)
    private boolean attended;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
