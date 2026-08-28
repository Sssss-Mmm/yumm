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
 * ponytail: 처리 상태 컬럼은 두지 않는다. 관리자 화면(FR-S-04)이 없어 아무도 읽지 않는 값이고,
 * 지금 신고의 목적은 기록을 남기는 것이다. 관리자 화면이 생기면 그때 상태를 붙인다.
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
}
