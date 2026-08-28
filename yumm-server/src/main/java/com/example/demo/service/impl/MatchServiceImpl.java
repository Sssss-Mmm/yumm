package com.example.demo.service.impl;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.User;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.dto.match.MatchStatusResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.GroupMatcher;
import com.example.demo.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    // ponytail: 대기 시간을 상수로 고정. 사용자가 고르게 하고 싶어지면 그때 요청 DTO로 올린다.
    private static final int WAIT_MINUTES = 30;

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MatchStatusResponse apply(Long userId, MatchApplyRequest request) {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        rejectIfAlreadyWaiting(userId, now);

        MatchRequest saved = matchRequestRepository.save(MatchRequest.builder()
                .user(user)
                .region(request.getRegion())
                .mealDate(request.getMealDate())
                .mealTime(request.getMealTime())
                .genderPreference(request.getGenderPreference())
                .foodPreferences(request.getFoodPreferences())
                .status(MatchStatus.WAITING)
                .createdAt(now)
                .expiresAt(now.plusMinutes(WAIT_MINUTES))
                .build());

        // ponytail: 편성 트리거는 신청 시점뿐. 스케줄러가 없어서 마지막 신청자는 다음 사람이
        // 올 때까지 그룹이 안 만들어진다. 대기가 길어지는 게 문제되면 @Scheduled를 얹으면 된다.
        formGroupsInBucket(saved, now);

        return buildStatus(saved, now);
    }

    @Override
    @Transactional(readOnly = true)
    public MatchStatusResponse getMyStatus(Long userId) {
        MatchRequest request = matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        return buildStatus(request, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void cancel(Long userId) {
        MatchRequest request = matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.getStatus() == MatchStatus.WAITING)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        request.cancel();
    }

    /**
     * 방금 들어온 신청과 같은 버킷의 대기자들을 3~4인 그룹으로 묶는다.
     * 잠금은 findWaitingInBucket의 PESSIMISTIC_WRITE가 담당한다.
     */
    private void formGroupsInBucket(MatchRequest trigger, LocalDateTime now) {
        List<MatchRequest> waiting = matchRequestRepository.findWaitingInBucket(
                trigger.getRegion(), trigger.getMealDate(), trigger.getMealTime(), now);

        if (waiting.size() < GroupMatcher.MIN_SIZE) {
            return;
        }

        Map<Long, MatchRequest> byId = new HashMap<>();
        List<GroupMatcher.Candidate> candidates = waiting.stream()
                .peek(m -> byId.put(m.getId(), m))
                .map(MatchServiceImpl::toCandidate)
                .toList();

        for (List<GroupMatcher.Candidate> group : GroupMatcher.formGroups(candidates)) {
            String groupId = UUID.randomUUID().toString();
            group.forEach(c -> byId.get(c.id()).assignToGroup(groupId));
        }
    }

    private static GroupMatcher.Candidate toCandidate(MatchRequest m) {
        return new GroupMatcher.Candidate(
                m.getId(),
                m.getUser().getGender(),
                m.getGenderPreference(),
                m.getFoodPreferences(),
                m.getCreatedAt());
    }

    private void rejectIfAlreadyWaiting(Long userId, LocalDateTime now) {
        matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.getStatus() == MatchStatus.WAITING && !m.isExpired(now))
                .ifPresent(m -> { throw new CustomException(ErrorCode.ALREADY_WAITING); });
    }

    private MatchStatusResponse buildStatus(MatchRequest request, LocalDateTime now) {
        if (request.getGroupId() == null) {
            return MatchStatusResponse.waiting(request, now);
        }
        return MatchStatusResponse.of(request, matchRequestRepository.findByGroupId(request.getGroupId()), now);
    }
}
