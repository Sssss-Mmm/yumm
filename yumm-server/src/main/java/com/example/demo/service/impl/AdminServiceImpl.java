package com.example.demo.service.impl;

import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.dto.admin.AdminReportResponse;
import com.example.demo.dto.admin.AdminStatsResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.domain.MatchRequest;
import com.example.demo.repository.AttendanceCheckRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AdminService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.service.MatchStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchService matchService;
    private final JwtRedisService jwtRedisService;
    private final AttendanceCheckRepository attendanceCheckRepository;

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

    @Override
    @Transactional
    public void suspendUser(Long userId) {
        User user = findUser(userId);
        if (user.isSuspended()) return;
        user.suspend(LocalDateTime.now());

        // 재발급을 끊는다. 이미 나간 Access Token은 JwtAuthenticationFilter가 요청마다 정지 여부를 보고 막는다.
        jwtRedisService.deleteRefreshToken(userId);

        // 정지된 사람이 대기열에 남으면 편성돼 남의 약속을 깬다. 탈퇴 정리(UserServiceImpl.clearOngoingMatch)와 같은 두 단계다.
        matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.getStatus() == MatchStatus.WAITING)
                .ifPresent(m -> matchService.cancel(userId));
        matchService.leaveAllGroups(userId);
    }

    @Override
    @Transactional
    public void unsuspendUser(Long userId) {
        findUser(userId).unsuspend();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }
        List<MatchRequest> requests = matchRequestRepository.findByMealDateBetween(from, to);
        Set<String> groupIds = requests.stream()
                .map(MatchRequest::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return MatchStats.compute(from, to, requests,
                groupIds.isEmpty() ? List.of() : attendanceCheckRepository.findByGroupIdIn(groupIds),
                LocalDateTime.now());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
