package com.example.demo.service.impl;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
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

import java.time.LocalDate;
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
        // 중복 검사보다 먼저 사용자 행을 잠근다. 같은 사용자가 동시에 신청해도
        // 뒤 트랜잭션은 여기서 대기했다가 앞 신청이 커밋된 뒤 중복 검사를 하게 된다(BR-01).
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        rejectIfInvalidMealDate(request.getMealDate(), now.toLocalDate());
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

        // 편성은 MatchScheduler가 전담한다(FR-G-06). 신청 시점에 편성하면 마지막 신청자가
        // 다음 사람이 올 때까지 안 묶이고, 편성 예외가 신청까지 롤백시킨다(NFR-08).
        return MatchStatusResponse.waiting(saved, now);
    }

    /**
     * 한 버킷의 대기자들을 3~4인 그룹으로 묶는다. 만들어진 그룹 수를 돌려준다.
     * 잠금은 findWaitingInBucket의 PESSIMISTIC_WRITE가 담당한다.
     */
    @Override
    @Transactional
    public int formGroupsInBucket(Region region, LocalDate mealDate, MealTime mealTime) {
        LocalDateTime now = LocalDateTime.now();
        List<MatchRequest> waiting = matchRequestRepository.findWaitingInBucket(region, mealDate, mealTime, now);

        if (waiting.size() < GroupMatcher.MIN_SIZE) {
            return 0;
        }

        Map<Long, MatchRequest> byId = new HashMap<>();
        List<GroupMatcher.Candidate> candidates = waiting.stream()
                .peek(m -> byId.put(m.getId(), m))
                .map(MatchServiceImpl::toCandidate)
                .toList();

        List<List<GroupMatcher.Candidate>> groups = GroupMatcher.formGroups(candidates);
        for (List<GroupMatcher.Candidate> group : groups) {
            String groupId = UUID.randomUUID().toString();
            group.forEach(c -> byId.get(c.id()).assignToGroup(groupId));
        }
        return groups.size();
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

    private static GroupMatcher.Candidate toCandidate(MatchRequest m) {
        return new GroupMatcher.Candidate(
                m.getId(),
                m.getUser().getGender(),
                m.getGenderPreference(),
                m.getFoodPreferences(),
                m.getCreatedAt());
    }

    /**
     * 당일과 익일만 신청할 수 있다(FR-M-03 / FR-M-04). "오늘 기준"이라 Bean Validation으로는 표현이 안 된다.
     *
     * ponytail: 범위를 넓히면 버킷이 날짜별로 갈려 대기자가 흩어지고 3인이 모이지 않는다.
     * 유동성이 충분해지면 그때 늘린다.
     */
    private void rejectIfInvalidMealDate(LocalDate mealDate, LocalDate today) {
        if (mealDate.isBefore(today) || mealDate.isAfter(today.plusDays(1))) {
            throw new CustomException(ErrorCode.INVALID_MEAL_DATE);
        }
    }

    /** 대기 중이거나 이미 매칭된 신청이 있으면 새 신청을 막는다. 판단은 MatchRequest에 있다. */
    private void rejectIfAlreadyWaiting(Long userId, LocalDateTime now) {
        matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.blocksReapply(now))
                .ifPresent(m -> { throw new CustomException(ErrorCode.ALREADY_WAITING); });
    }

    private MatchStatusResponse buildStatus(MatchRequest request, LocalDateTime now) {
        if (request.getGroupId() == null) {
            return MatchStatusResponse.waiting(request, now);
        }
        return MatchStatusResponse.of(request, matchRequestRepository.findByGroupId(request.getGroupId()), now);
    }
}
