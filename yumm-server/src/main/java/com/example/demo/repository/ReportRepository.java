package com.example.demo.repository;

import com.example.demo.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 관리자 화면 기본 목록. 미처리 신고만, 오래된 것부터 — 먼저 들어온 신고를 먼저 본다. */
    List<Report> findByHandledAtIsNullOrderByCreatedAtAsc();

    /** 처리분까지 포함한 전체 목록. 최근 것부터. */
    List<Report> findAllByOrderByCreatedAtDesc();
}
