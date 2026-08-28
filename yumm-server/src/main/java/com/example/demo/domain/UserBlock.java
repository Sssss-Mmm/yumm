package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 차단 1건(FR-S-02). 방향이 있는 관계로 저장하고, 편성에서는 쌍방으로 배제한다.
 *
 * 같은 쌍을 두 번 저장하지 않도록 유니크 제약을 건다. 중복 차단은 에러가 아니라 성공이므로
 * 이 제약은 사용자에게 보이는 규칙이 아니라 데이터가 불어나는 걸 막는 안전망이다.
 */
@Entity
@Table(name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_blocks_pair",
                columnNames = {"blocker_id", "blocked_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
