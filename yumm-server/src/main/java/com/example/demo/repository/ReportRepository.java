package com.example.demo.repository;

import com.example.demo.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // ponytail: 지금은 저장만 한다. 조회는 관리자 화면(FR-S-04)이 생길 때 추가한다.
}
