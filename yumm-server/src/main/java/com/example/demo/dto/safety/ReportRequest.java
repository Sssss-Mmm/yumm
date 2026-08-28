package com.example.demo.dto.safety;

import com.example.demo.domain.Report;
import com.example.demo.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 신고 요청(FR-S-01). 신고자는 인증 정보에서 가져오므로 본문에 받지 않는다. */
@Getter
@NoArgsConstructor
public class ReportRequest {

    @NotNull(message = "신고 대상을 선택해 주세요.")
    private Long reportedUserId;

    @NotNull(message = "신고 사유를 선택해 주세요.")
    private ReportReason reason;

    @Size(max = Report.MAX_DETAIL_LENGTH, message = "상세 내용은 500자 이내로 입력해 주세요.")
    private String detail; // 선택
}
