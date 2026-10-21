package com.example.demo.service;

import com.example.demo.dto.admin.AdminReportResponse;
import com.example.demo.dto.admin.AdminStatsResponse;

import java.time.LocalDate;
import java.util.List;

/** 관리자 기능. 신고를 읽고 처리 표시를 남기고(FR-D-01), 계정을 정지·해제하고(FR-D-02), 운영 지표를 본다(FR-D-03). */
public interface AdminService {

    /**
     * 신고 목록을 조회한다.
     *
     * @param includeHandled true면 처리분까지 최근순, false면 미처리만 오래된순
     */
    List<AdminReportResponse> getReports(boolean includeHandled);

    /** 신고를 처리 완료로 표시한다. 이미 처리된 신고는 최초 처리 시각을 유지한다. */
    AdminReportResponse handleReport(Long reportId);

    /**
     * 계정을 정지한다. 로그인·기존 토큰·채팅이 전부 막히고, 대기 중인 신청은 취소되며 속한 그룹에서는 빠진다.
     * 이미 정지된 계정이면 아무것도 바꾸지 않는다.
     */
    void suspendUser(Long userId);

    /** 정지를 해제한다. 정지로 취소된 신청·그룹은 되살리지 않는다. 다시 로그인하면 된다. */
    void unsuspendUser(Long userId);

    /** 식사 날짜가 [from, to]인 신청·그룹의 성사율·대기시간·만남 전환율·노쇼율(FR-D-03). */
    AdminStatsResponse getStats(LocalDate from, LocalDate to);
}
