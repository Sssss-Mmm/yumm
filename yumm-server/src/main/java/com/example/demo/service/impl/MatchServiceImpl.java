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
import com.example.demo.repository.UserBlockRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    // ponytail: 대기 시간을 상수로 고정. 사용자가 고르게 하고 싶어지면 그때 요청 DTO로 올린다.
    private static final int WAIT_MINUTES = 30;

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

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
                .allowPair(request.isAllowPair())
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
     *
     * 차단 관계(FR-S-02)는 이 버킷 대기자들 사이의 것만 쿼리 한 번으로 읽어 편성 로직에 데이터로 넘긴다.
     * 이미 성사된 그룹은 건드리지 않는다 — 차단은 "이후" 편성에만 적용되고,
     * 지금 그룹에서 나가는 건 leaveGroup(FR-C-02)의 몫이다.
     */
    @Override
    @Transactional
    public int formGroupsInBucket(Region region, LocalDate mealDate, MealTime mealTime) {
        LocalDateTime now = LocalDateTime.now();
        List<MatchRequest> waiting = matchRequestRepository.findWaitingInBucket(region, mealDate, mealTime, now);

        // 2인 폴백이 있으므로 하한은 3이 아니라 2다(FR-G-08). 3으로 두면 대기자가 딱 2명일 때
        // 폴백이 아예 실행되지 않는다.
        if (waiting.size() < GroupMatcher.PAIR_SIZE) {
            return 0;
        }

        Map<Long, MatchRequest> byId = new HashMap<>();
        List<GroupMatcher.Candidate> candidates = waiting.stream()
                .peek(m -> byId.put(m.getId(), m))
                .map(MatchServiceImpl::toCandidate)
                .toList();

        List<List<GroupMatcher.Candidate>> groups =
                GroupMatcher.formGroups(candidates, blockedPairsAmong(waiting), now);
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

    /**
     * 그룹 이탈(FR-C-02)과 최소 인원 미달 시 해체(FR-C-03)를 한 트랜잭션에서 처리한다.
     * 대기열로 되돌린 신청은 다음 주기의 MatchScheduler가 알아서 재편성한다.
     */
    @Override
    @Transactional
    public void leaveGroup(Long userId) {
        MatchRequest request = matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.getStatus() == MatchStatus.MATCHED)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        // 이탈 처리 전에 그룹 행을 쓰기 잠금해서 조회하고 본인만 걷어낸다. 동시 이탈 트랜잭션은
        // 여기서 대기했다가 앞 이탈이 커밋된 뒤의 그룹을 보게 된다(이미 나간 사람은 안 딸려온다).
        List<MatchRequest> remaining = matchRequestRepository.findByGroupIdForUpdate(request.getGroupId()).stream()
                .filter(m -> !m.getId().equals(request.getId()))
                .toList();
        request.leaveGroup();

        if (remaining.size() >= GroupMatcher.MIN_SIZE) {
            return;
        }

        // ponytail: 본인 행만 잠금 밖에서 먼저 읽는다. 동시 이탈 시 그 낡은 스냅샷이 그대로 다시
        // 쓰이지만 leaveGroup()이 어차피 종료 상태로 덮으므로 결과가 달라지지 않는다.
        // 업그레이드 경로: 그룹에 속성(식당, 확정)이 생기면 MatchGroup 엔티티로 승격해 그 행을 잠근다.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(WAIT_MINUTES);
        remaining.forEach(m -> {
            // 지난 끼니를 대기열로 되돌리면 findWaitingBuckets가 과거 날짜 버킷을 뱉어 스케줄러가
            // 지난 날짜 그룹을 새로 편성하고, 이미 재신청한 사람은 WAITING 2건이 된다(BR-01 위반).
            if (m.isPastMeal(now.toLocalDate())) {
                m.cancel();
            } else {
                m.returnToWaiting(expiresAt);
            }
        });
    }

    /** 이 버킷 대기자들 사이의 차단 관계. 후보 쌍마다 조회하면 버킷 크기의 제곱만큼 쿼리가 나간다. */
    private Set<GroupMatcher.BlockedPair> blockedPairsAmong(List<MatchRequest> waiting) {
        Set<Long> userIds = waiting.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        return userBlockRepository.findPairsAmong(userIds).stream()
                .map(p -> new GroupMatcher.BlockedPair(p.getBlockerId(), p.getBlockedId()))
                .collect(Collectors.toSet());
    }

    private static GroupMatcher.Candidate toCandidate(MatchRequest m) {
        return new GroupMatcher.Candidate(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getGender(),
                m.getGenderPreference(),
                m.getFoodPreferences(),
                m.getCreatedAt(),
                m.getExpiresAt(),
                m.isAllowPair());
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
