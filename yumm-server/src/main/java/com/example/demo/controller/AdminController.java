package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.admin.AdminReportResponse;
import com.example.demo.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 전용 API(FR-D-01).
 *
 * <p>접근 제어는 {@code SecurityConfig}가 {@code /api/admin/**}에 ROLE_ADMIN을 요구하는 것으로
 * 처리한다. 컨트롤러에서 권한을 다시 확인하지 않는다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 신고 목록을 조회한다.
     *
     * @param includeHandled 처리된 신고까지 포함할지 여부. 기본값 false(미처리만)
     */
    @GetMapping("/reports")
    @Operation(summary = "신고 목록 조회",
            description = "기본은 미처리 신고만 접수 순으로 준다. includeHandled=true면 처리분까지 최근순으로 준다.")
    public ResponseEntity<ApiResponse<List<AdminReportResponse>>> getReports(
            @RequestParam(defaultValue = "false") boolean includeHandled) {
        return ApiResponse.ok("신고 목록 조회 성공", adminService.getReports(includeHandled));
    }

    /**
     * 신고를 처리 완료로 표시한다.
     *
     * @param reportId 처리할 신고 ID
     */
    @PostMapping("/reports/{reportId}/handle")
    @Operation(summary = "신고 처리 완료 표시",
            description = "이미 처리된 신고를 다시 호출해도 최초 처리 시각은 바뀌지 않는다.")
    public ResponseEntity<ApiResponse<AdminReportResponse>> handleReport(@PathVariable Long reportId) {
        return ApiResponse.ok("신고를 처리했습니다.", adminService.handleReport(reportId));
    }
}
