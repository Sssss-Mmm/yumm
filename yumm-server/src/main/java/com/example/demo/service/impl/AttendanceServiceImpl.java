package com.example.demo.service.impl;

import com.example.demo.domain.AttendanceCheck;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.User;
import com.example.demo.dto.match.AttendanceCheckRequest;
import com.example.demo.dto.match.MatchStatusResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.AttendanceCheckRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceCheckRepository attendanceCheckRepository;
    private final MatchRequestRepository matchRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MatchStatusResponse> getPending(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(userId).stream()
                .filter(m -> m.getStatus() == MatchStatus.MATCHED)
                .filter(m -> isOpen(m.getMealDate(), now.toLocalDate()))
                .filter(m -> !attendanceCheckRepository.existsByGroupIdAndChecker_Id(m.getGroupId(), userId))
                .map(m -> MatchStatusResponse.of(m, matchRequestRepository.findByGroupId(m.getGroupId()), now))
                .toList();
    }

    /**
     * 그룹 행을 쓰기 잠금해서 읽는다. 같은 사람이 두 번 눌러도 뒤 요청은 앞 요청의 커밋을 본 뒤
     * 중복 검사를 하게 되어 유니크 제약 위반(500)까지 가지 않는다.
     * 그룹을 나간 사람(groupId가 지워진 행)은 여기서 안 나오므로 체크 대상도 체크할 자격도 없다.
     */
    @Override
    @Transactional
    public void submit(Long userId, String groupId, AttendanceCheckRequest request) {
        Map<Long, User> membersById = matchRequestRepository.findByGroupIdForUpdate(groupId).stream()
                .map(MatchRequest::getUser)
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 구성원이 아니면 그룹이 있는지도 알려주지 않는다.
        MatchRequest mine = matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(userId).stream()
                .filter(m -> groupId.equals(m.getGroupId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!isOpen(mine.getMealDate(), LocalDate.now())) {
            throw new CustomException(ErrorCode.ATTENDANCE_NOT_OPEN);
        }
        if (attendanceCheckRepository.existsByGroupIdAndChecker_Id(groupId, userId)) {
            throw new CustomException(ErrorCode.ATTENDANCE_ALREADY_CHECKED);
        }

        // 나를 뺀 전원을 정확히 한 번씩. 일부만 받으면 "체크 안 함"과 "안 왔다"가 집계에서 구분되지 않는다.
        List<AttendanceCheckRequest.Item> checks = request.getChecks();
        Set<Long> targets = checks.stream().map(AttendanceCheckRequest.Item::getUserId).collect(Collectors.toSet());
        Set<Long> others = membersById.keySet().stream().filter(id -> !id.equals(userId)).collect(Collectors.toSet());
        if (targets.size() != checks.size() || !targets.equals(others)) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDateTime now = LocalDateTime.now();
        User checker = membersById.get(userId);
        attendanceCheckRepository.saveAll(checks.stream()
                .map(c -> AttendanceCheck.builder()
                        .groupId(groupId)
                        .checker(checker)
                        .target(membersById.get(c.getUserId()))
                        .attended(c.getAttended())
                        .createdAt(now)
                        .build())
                .toList());
    }

    /**
     * ponytail: 식사 당일부터 채팅방이 닫힐 때(FR-T-05)까지 받는다.
     * 날짜 단위라 점심 그룹은 당일 아침에도 체크가 열린다 — 만날 시각이 시스템에 없어서다(product-plan 8절).
     * 약속 카드(FR-C-05)로 시각이 생기면 "약속 시각 이후"로 좁힌다.
     */
    private static boolean isOpen(LocalDate mealDate, LocalDate today) {
        return !today.isBefore(mealDate) && !today.isAfter(mealDate.plusDays(MatchRequest.ROOM_OPEN_DAYS_AFTER_MEAL));
    }
}
