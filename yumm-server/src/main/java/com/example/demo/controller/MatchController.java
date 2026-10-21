package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.match.AttendanceCheckRequest;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.dto.match.MatchStatusResponse;
import com.example.demo.dto.match.WaitingCountResponse;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final AttendanceService attendanceService;

    /**
     * 밥메이트 매칭 신청 API.
     * 지역/날짜/시간대/성별조건/선호음식을 받아 대기열에 등록합니다. 신청 시점에는 편성하지 않습니다(FR-G-06).
     *
     * @param userDetails 현재 인증된 사용자
     * @param request 매칭 조건
     * @return 신청 직후 상태. 항상 WAITING이며, 편성 결과는 상태 조회로 확인합니다.
     */
    @PostMapping
    @Operation(summary = "밥메이트 매칭 신청",
            description = "매칭 조건을 받아 대기열에 등록합니다. 응답은 항상 WAITING이고, "
                    + "3~4인 그룹 편성은 30초 주기 스케줄러가 수행하므로 매칭 상태 조회 API로 확인합니다.")
    public ResponseEntity<ApiResponse<MatchStatusResponse>> apply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MatchApplyRequest request) {

        MatchStatusResponse response = matchService.apply(userDetails.getId(), request);

        return ApiResponse.ok("매칭 대기열에 등록되었습니다.", response);
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

    /**
     * 그룹 이탈 API. 매칭이 성사된 신청만 이탈할 수 있습니다.
     * 이탈하면 그룹 채팅 접근도 함께 끊기고, 남은 인원이 3명 미만이면 그룹이 해체되어
     * 남은 구성원은 다시 대기열로 돌아갑니다.
     */
    @DeleteMapping("/group")
    @Operation(summary = "매칭 그룹 이탈",
            description = "매칭된 그룹에서 나갑니다(FR-C-02). 남은 인원이 최소 인원(3명) 미만이면 그룹을 해체하고 "
                    + "남은 구성원을 30분짜리 대기 상태로 되돌려 다음 편성 주기에 재매칭합니다(FR-C-03).")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        matchService.leaveGroup(userDetails.getId());

        return ApiResponse.ok("그룹에서 나갔습니다.");
    }

    /**
     * 버킷별 현재 대기 인원 조회 API. 로그인 없이 호출할 수 있습니다(FR-A-10).
     * 대기자가 있는 버킷만 내려가며, 인원 수 외의 정보는 담지 않습니다.
     */
    @GetMapping("/waiting-counts")
    @Operation(summary = "지역별 대기 인원 조회",
            description = "지역·날짜·시간대별 현재 대기 인원을 줍니다. 비로그인 둘러보기(FR-A-10)와 대기 화면(FR-M-10)이 함께 씁니다.")
    public ResponseEntity<ApiResponse<List<WaitingCountResponse>>> getWaitingCounts() {
        return ApiResponse.ok("대기 인원 조회 성공", matchService.getWaitingCounts());
    }

    /**
     * 참석 체크 대기 목록 API. 식사 당일~다음 날 사이이고 아직 체크하지 않은 그룹만 내려갑니다(FR-C-06).
     * 그룹원 목록에는 나도 포함됩니다.
     */
    @GetMapping("/attendance")
    @Operation(summary = "참석 체크 대기 목록", description = "만남 뒤 참석 여부를 아직 체크하지 않은 그룹 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<MatchStatusResponse>>> getPendingAttendance(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.ok("참석 체크 대기 목록 조회 성공", attendanceService.getPending(userDetails.getId()));
    }

    /**
     * 참석 체크 제출 API. 나를 뺀 그룹원 전원의 참석 여부를 한 번에 제출하며, 그룹당 한 번만 할 수 있습니다(FR-C-06).
     */
    @PostMapping("/groups/{groupId}/attendance")
    @Operation(summary = "참석 체크 제출",
            description = "식사 당일부터 다음 날까지, 나를 뺀 그룹원 전원이 실제로 나왔는지 제출합니다. "
                    + "그룹당 한 번이며 일부만 보내거나 그룹원이 아닌 사람을 넣으면 400입니다.")
    public ResponseEntity<ApiResponse<Void>> submitAttendance(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String groupId,
            @Valid @RequestBody AttendanceCheckRequest request) {

        attendanceService.submit(userDetails.getId(), groupId, request);

        return ApiResponse.ok("참석 체크가 제출되었습니다.");
    }
}
