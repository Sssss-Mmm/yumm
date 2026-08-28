package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.safety.ReportRequest;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.SafetyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고(FR-S-01)와 차단(FR-S-02) API.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    /**
     * 사용자 신고 API.
     * 사유와 선택적인 상세 내용을 받아 신고를 기록합니다. 신고해도 상대가 자동으로 차단되지는 않습니다.
     *
     * @param userDetails 현재 인증된 사용자(신고자)
     * @param request 신고 대상·사유·상세 내용
     */
    @PostMapping("/reports")
    @Operation(summary = "사용자 신고",
            description = "그룹원 또는 채팅 상대를 사유와 함께 신고합니다(FR-S-01). "
                    + "자기 자신은 신고할 수 없습니다. 신고는 차단과 별개이며 자동으로 차단되지 않습니다.")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReportRequest request) {

        safetyService.report(userDetails.getId(), request);

        return ApiResponse.ok("신고가 접수되었습니다.");
    }

    /**
     * 사용자 차단 API.
     * 차단하면 이후 편성에서 서로 같은 그룹에 들어가지 않습니다(FR-S-02).
     * 이미 매칭된 그룹은 해체되지 않으므로, 지금 그룹에서 나가려면 그룹 이탈 API를 씁니다.
     *
     * @param userDetails 현재 인증된 사용자(차단하는 사람)
     * @param userId 차단할 사용자 ID
     */
    @PostMapping("/blocks/{userId}")
    @Operation(summary = "사용자 차단",
            description = "해당 사용자를 차단해 이후 편성에서 서로 같은 그룹에 들어가지 않게 합니다(FR-S-02). "
                    + "이미 차단한 상대를 다시 차단해도 성공합니다. 이미 성사된 그룹은 해체되지 않습니다.")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId) {

        safetyService.block(userDetails.getId(), userId);

        return ApiResponse.ok("차단되었습니다.");
    }
}
