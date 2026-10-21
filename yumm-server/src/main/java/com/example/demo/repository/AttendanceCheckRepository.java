package com.example.demo.repository;

import com.example.demo.domain.AttendanceCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttendanceCheckRepository extends JpaRepository<AttendanceCheck, Long> {

    /** 이 그룹에 대해 이미 체크를 제출했는지. 제출은 그룹당 한 번이라 한 건만 있어도 끝난 것이다. */
    boolean existsByGroupIdAndChecker_Id(String groupId, Long checkerId);

    /** 여러 그룹의 체크 전부(FR-D-03 지표). */
    List<AttendanceCheck> findByGroupIdIn(Collection<String> groupIds);
}
