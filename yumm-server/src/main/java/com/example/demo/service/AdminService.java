package com.example.demo.service;

import com.example.demo.dto.admin.AdminReportResponse;

import java.util.List;

/** 관리자 기능(FR-D-01). 신고를 읽고 처리 표시를 남긴다. */
public interface AdminService {

    /**
     * 신고 목록을 조회한다.
     *
     * @param includeHandled true면 처리분까지 최근순, false면 미처리만 오래된순
     */
    List<AdminReportResponse> getReports(boolean includeHandled);

    /** 신고를 처리 완료로 표시한다. 이미 처리된 신고는 최초 처리 시각을 유지한다. */
    AdminReportResponse handleReport(Long reportId);
}
