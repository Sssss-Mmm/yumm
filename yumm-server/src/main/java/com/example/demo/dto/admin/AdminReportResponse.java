package com.example.demo.dto.admin;

import com.example.demo.domain.Report;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자 화면에 내려주는 신고 1건(FR-D-01).
 *
 * <p>신고자·피신고자는 식별에 필요한 id와 닉네임만 담는다. 관리자라도 이메일·생년 원본은
 * 이 화면에서 볼 이유가 없다(FR-P-05의 취지).
 */
@Getter
@AllArgsConstructor
public class AdminReportResponse {

    private final Long id;
    private final Long reporterId;
    private final String reporterNickname;
    private final Long reportedId;
    private final String reportedNickname;
    private final String reason;
    private final String reasonLabel; // ReportReason.description — 화면에 그대로 쓴다
    private final String detail;
    private final LocalDateTime createdAt;
    private final LocalDateTime handledAt;

    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(
                report.getId(),
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getReported().getId(),
                report.getReported().getNickname(),
                report.getReason().name(),
                report.getReason().getDescription(),
                report.getDetail(),
                report.getCreatedAt(),
                report.getHandledAt());
    }
}
