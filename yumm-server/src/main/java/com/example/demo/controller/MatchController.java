package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.dto.match.MatchStatusResponse;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * 밥메이트 매칭 신청 API.
     * 지역/날짜/시간대/성별조건/선호음식을 받아 대기열에 등록하고, 곧바로 그룹 편성을 시도합니다.
     *
     * @param userDetails 현재 인증된 사용자
     * @param request 매칭 조건
     * @return 신청 직후 상태. 바로 3~4인이 모이면 MATCHED, 아니면 WAITING.
     */
    @PostMapping
    @Operation(summary = "밥메이트 매칭 신청", description = "매칭 조건을 받아 대기열에 등록하고 3~4인 그룹 편성을 시도합니다.")
    public ResponseEntity<ApiResponse<MatchStatusResponse>> apply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MatchApplyRequest request) {

        MatchStatusResponse response = matchService.apply(userDetails.getId(), request);

        return ApiResponse.ok(response.isMatched() ? "매칭이 성사되었습니다." : "매칭 대기열에 등록되었습니다.", response);
    }

    /**
     * 내 매칭 상태 조회 API.
     * 가장 최근 매칭 신청의 상태와, 성사됐다면 그룹 구성원을 반환합니다.
     */
    @GetMapping
    @Operation(summary = "내 매칭 상태 조회", description = "가장 최근 매칭 신청의 상태와 그룹 구성원을 조회합니다.")
    public ResponseEntity<ApiResponse<MatchStatusResponse>> getMyStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.ok("매칭 상태 조회 성공", matchService.getMyStatus(userDetails.getId()));
    }

    /**
     * 매칭 취소 API. 대기 중인 신청만 취소할 수 있습니다.
     */
    @DeleteMapping
    @Operation(summary = "매칭 취소", description = "대기 중인 매칭 신청을 취소합니다.")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        matchService.cancel(userDetails.getId());

        return ApiResponse.ok("매칭 신청이 취소되었습니다.");
    }
}
