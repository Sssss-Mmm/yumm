package com.example.demo.service.impl;

import com.example.demo.domain.Report;
import com.example.demo.dto.admin.AdminReportResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ReportRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminReportResponse> getReports(boolean includeHandled) {
        // 미처리 기본값은 오래된 순이다. 신고는 접수 순서대로 처리하는 게 맞다.
        List<Report> reports = includeHandled
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByHandledAtIsNullOrderByCreatedAtAsc();
        return reports.stream().map(AdminReportResponse::from).toList();
    }

    @Override
    @Transactional
    public AdminReportResponse handleReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
        report.markHandled(LocalDateTime.now());
        return AdminReportResponse.from(report);
    }
}
