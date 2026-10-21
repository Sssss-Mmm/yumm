package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.admin.AdminReportResponse;
import com.example.demo.dto.admin.AdminStatsResponse;
import com.example.demo.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 전용 API. 신고 처리(FR-D-01), 계정 정지(FR-D-02), 운영 지표(FR-D-03).
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

    /**
     * 계정을 정지한다(FR-D-02). 로그인·기존 토큰·채팅이 즉시 막히고 진행 중인 매칭은 정리된다.
     *
     * @param userId 정지할 사용자 ID
     */
    @PostMapping("/users/{userId}/suspend")
    @Operation(summary = "계정 정지",
            description = "로그인과 이미 발급된 토큰을 모두 막는다. 대기 중인 신청은 취소하고 속한 그룹에서는 이탈 처리한다. "
                    + "이미 정지된 계정에 다시 호출해도 최초 정지 시각은 바뀌지 않는다.")
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable Long userId) {
        adminService.suspendUser(userId);
        return ApiResponse.ok("계정을 정지했습니다.");
    }

    /**
     * 계정 정지를 해제한다(FR-D-02).
     *
     * @param userId 해제할 사용자 ID
     */
    @DeleteMapping("/users/{userId}/suspend")
    @Operation(summary = "계정 정지 해제", description = "정지로 취소된 신청·그룹은 되살리지 않는다.")
    public ResponseEntity<ApiResponse<Void>> unsuspendUser(@PathVariable Long userId) {
        adminService.unsuspendUser(userId);
        return ApiResponse.ok("계정 정지를 해제했습니다.");
    }

    /**
     * 운영 지표를 조회한다(FR-D-03). 기간은 식사 날짜 기준이며 기본은 오늘까지 최근 7일이다.
     *
     * @param from 시작 식사 날짜(포함)
     * @param to   끝 식사 날짜(포함)
     */
    @GetMapping("/stats")
    @Operation(summary = "운영 지표 조회",
            description = "신청 → 성사율·평균 대기시간(전체, 지역·끼니별), 2인 비중, 만남 전환율, 2인/3~4인 노쇼율을 준다. "
                    + "비율은 % 단위이고 분모가 0이면 null이다.")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        return ApiResponse.ok("운영 지표 조회 성공", adminService.getStats(start, end));
    }
}
