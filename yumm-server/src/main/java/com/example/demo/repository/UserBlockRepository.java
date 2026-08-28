package com.example.demo.repository;

import com.example.demo.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /** 이미 차단한 상대인지. 중복 차단을 에러 없이 넘기는 데 쓴다. */
    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    /**
     * 주어진 사용자들 사이의 차단 관계 전부. 편성 한 번에 쿼리 한 번이면 된다.
     * 후보 쌍마다 조회하면 버킷 크기의 제곱만큼 쿼리가 나간다.
     *
     * 엔티티 대신 id 쌍만 꺼내는 이유: 지연 로딩된 User 프록시에서 id를 읽으면
     * 차단 건수만큼 사용자 조회가 따라붙는다.
     */
    @Query("""
            SELECT b.blocker.id AS blockerId, b.blocked.id AS blockedId
            FROM UserBlock b
            WHERE b.blocker.id IN :userIds AND b.blocked.id IN :userIds
            """)
    List<BlockPair> findPairsAmong(@Param("userIds") Collection<Long> userIds);

    /** findPairsAmong 결과를 받는 프로젝션 */
    interface BlockPair {
        Long getBlockerId();
        Long getBlockedId();
    }
}
