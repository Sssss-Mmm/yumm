package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 신고 1건(FR-S-01).
 *
 * 처리 상태는 {@code handledAt} 하나로 표현한다. null이면 미처리다.
 * ponytail: 상태 enum(대기/처리중/완료)도 처리자 FK도 두지 않는다. 관리자가 한 명이고
 * 실제로 필요한 판단은 "이 신고를 봤는가"뿐이다. 처리 이력이 필요해지면 그때 테이블을 판다.
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report {

    public static final int MAX_DETAIL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = MAX_DETAIL_LENGTH)
    private String detail; // 선택 입력

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 관리자가 확인한 시각. null이면 미처리(FR-D-01). */
    private LocalDateTime handledAt;

    /** 이미 처리된 신고를 다시 처리해도 최초 처리 시각을 덮어쓰지 않는다. */
    public void markHandled(LocalDateTime at) {
        if (handledAt == null) handledAt = at;
    }
}
