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
import com.example.demo.security.StompSubscriptionRevoker;
import com.example.demo.service.EmailService;
import com.example.demo.service.GroupMatcher;
import com.example.demo.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    // ponytail: 대기 시간을 상수로 고정. 사용자가 고르게 하고 싶어지면 그때 요청 DTO로 올린다.
    private static final int WAIT_MINUTES = 30;

    private static final String MATCHED_SUBJECT = "[yumm] 밥메이트 매칭이 성사됐어요";
    private static final String MEMBER_LEFT_SUBJECT = "[yumm] 밥메이트 구성원 한 명이 나갔어요";
    private static final String DISBANDED_SUBJECT = "[yumm] 밥메이트 그룹이 해체됐어요";
    private static final String REMINDER_SUBJECT = "[yumm] 오늘 밥메이트 약속이 있어요";

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final EmailService emailService;
    private final StompSubscriptionRevoker stompSubscriptionRevoker;

    @Override
    @Transactional
    public MatchStatusResponse apply(Long userId, MatchApplyRequest request) {
        LocalDateTime now = LocalDateTime.now();
        // 중복 검사보다 먼저 사용자 행을 잠근다. 같은 사용자가 동시에 신청해도
        // 뒤 트랜잭션은 여기서 대기했다가 앞 신청이 커밋된 뒤 중복 검사를 하게 된다(BR-01).
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이메일 인증 게이트(FR-A-03). 가입·로그인·프로필 수정은 미인증으로도 되고 여기 하나만 막는다.
        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

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
        List<Notice> notices = new ArrayList<>();
        for (List<GroupMatcher.Candidate> group : groups) {
            String groupId = UUID.randomUUID().toString();
            group.forEach(c -> byId.get(c.id()).assignToGroup(groupId));
            // 본문은 트랜잭션 안에서 만든다. 커밋 뒤에는 User가 준영속이라 이메일/닉네임을 못 읽는다.
            notices.addAll(matchedNotices(group, byId, region, mealDate, mealTime));
        }
        notifyAfterCommit(notices);
        return groups.size();
    }

    /** 알림 메일 한 통. 본문은 그룹당 같고 받는 사람만 다르다. */
    private record Notice(String to, String subject, String body) {}

    private List<Notice> matchedNotices(List<GroupMatcher.Candidate> group, Map<Long, MatchRequest> byId,
                                        Region region, LocalDate mealDate, MealTime mealTime) {
        // 다른 구성원의 닉네임·연락처는 넣지 않는다. 메일은 앱보다 유출 경로가 많다(NFR-05).
        String body = """
                밥메이트 매칭이 성사됐어요.

                지역: %s
                날짜: %s
                끼니: %s
                인원: %d명

                yumm에서 그룹 채팅으로 만날 곳을 정해 보세요.
                """.formatted(region.getDescription(), mealDate, mealTime.getDescription(), group.size());

        return notices(group.stream().map(c -> byId.get(c.id())).toList(), MATCHED_SUBJECT, body);
    }

    /**
     * 받는 사람 목록을 신청 행에서 뽑는다. 이메일이 없는 행이 하나 있다고 호출한 쪽 트랜잭션이
     * NPE로 깨지면 안 되므로 그 사람만 건너뛴다.
     */
    private static List<Notice> notices(List<MatchRequest> targets, String subject, String body) {
        return targets.stream()
                .map(MatchRequest::getUser)
                .filter(Objects::nonNull)
                .map(User::getEmail)
                .filter(Objects::nonNull)
                .map(email -> new Notice(email, subject, body))
                .toList();
    }

    /**
     * 편성이 커밋된 뒤에 보낸다. 트랜잭션 안에서 보내면 SMTP 왕복 동안 버킷 잠금을 붙들고,
     * 커밋이 실패해도 "매칭됐다" 메일이 나간다. 발송 실패는 EmailService가 삼킨다.
     *
     * ponytail: 스케줄러 스레드에서 순차 발송한다(통당 타임아웃 5초, 주기 30초).
     * 그룹 수가 늘어 주기를 넘기기 시작하면 그때 별도 스레드나 큐로 뺀다.
     */
    private void notifyAfterCommit(List<Notice> notices) {
        if (notices.isEmpty()) {
            return;
        }
        afterCommit(() -> send(notices));
    }

    /** 커밋된 뒤에 실행한다. 트랜잭션 밖에서 호출되면(단위 테스트 등) 그 자리에서 실행한다. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void send(List<Notice> notices) {
        notices.forEach(n -> emailService.send(n.to(), n.subject(), n.body()));
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

        // leaveGroup()이 groupId를 지우므로 채팅방 주소는 먼저 챙겨둔다.
        String groupId = request.getGroupId();

        // 이탈 처리 전에 그룹 행을 쓰기 잠금해서 조회하고 본인만 걷어낸다. 동시 이탈 트랜잭션은
        // 여기서 대기했다가 앞 이탈이 커밋된 뒤의 그룹을 보게 된다(이미 나간 사람은 안 딸려온다).
        List<MatchRequest> remaining = matchRequestRepository.findByGroupIdForUpdate(request.getGroupId()).stream()
                .filter(m -> !m.getId().equals(request.getId()))
                .toList();
        request.leaveGroup();

        if (remaining.size() >= GroupMatcher.MIN_SIZE) {
            // 나간 사람의 열려 있는 구독을 끊는다. 안 끊으면 소켓이 살아 있는 동안 남은 대화를 계속 본다(FR-T-02).
            revokeSubscriptionsAfterCommit(groupId, emailsOf(List.of(request)));
            // 그룹은 유지된다. 나간 사람이 누구인지는 알리지 않는다 — 닉네임도 신원이다(NFR-05).
            notifyAfterCommit(notices(remaining, MEMBER_LEFT_SUBJECT, memberLeftBody(request, remaining.size())));
            return;
        }

        // ponytail: 본인 행만 잠금 밖에서 먼저 읽는다. 동시 이탈 시 그 낡은 스냅샷이 그대로 다시
        // 쓰이지만 leaveGroup()이 어차피 종료 상태로 덮으므로 결과가 달라지지 않는다.
        // 업그레이드 경로: 그룹에 속성(식당, 확정)이 생기면 MatchGroup 엔티티로 승격해 그 행을 잠근다.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(WAIT_MINUTES);
        // 한 그룹은 한 버킷에서 나오므로 mealDate가 전원 같다. 판정을 한 번만 하고 본문에도 그대로 쓴다.
        boolean pastMeal = request.isPastMeal(now.toLocalDate());
        remaining.forEach(m -> {
            // 지난 끼니를 대기열로 되돌리면 findWaitingBuckets가 과거 날짜 버킷을 뱉어 스케줄러가
            // 지난 날짜 그룹을 새로 편성하고, 이미 재신청한 사람은 WAITING 2건이 된다(BR-01 위반).
            if (pastMeal) {
                m.cancel();
            } else {
                m.returnToWaiting(expiresAt);
            }
        });
        // 해체됐으니 방 자체가 없어진다. 나간 사람과 남은 사람의 구독을 모두 끊는다(FR-T-02).
        List<MatchRequest> everyone = new ArrayList<>(remaining);
        everyone.add(request);
        revokeSubscriptionsAfterCommit(groupId, emailsOf(everyone));

        // 해체는 남은 전원에게 알린다. 앱을 열지 않으면 약속이 사라진 걸 알 방법이 없다(FR-N-02).
        notifyAfterCommit(notices(remaining, DISBANDED_SUBJECT, disbandedBody(request, pastMeal)));
    }

    /**
     * 이미 열려 있는 채팅방 구독을 커밋 뒤에 끊는다(FR-T-02).
     *
     * 커밋 전에 끊으면 롤백된 이탈에도 구독이 날아간다. 반대로 커밋 후 끊기 전에 프로세스가 죽으면
     * 그 세션의 수신만 소켓이 끊길 때까지 남는다 — 새 구독·발신·이력 조회는 인터셉터가 이미 막는다.
     */
    private void revokeSubscriptionsAfterCommit(String groupId, List<String> usernames) {
        if (groupId == null || usernames.isEmpty()) {
            return;
        }
        afterCommit(() -> stompSubscriptionRevoker.revoke(groupId, usernames));
    }

    /** STOMP 사용자 이름은 로그인 이메일이다(CustomUserDetails.getUsername). 준영속이 되기 전에 뽑는다. */
    private static List<String> emailsOf(List<MatchRequest> requests) {
        return requests.stream()
                .map(MatchRequest::getUser)
                .filter(Objects::nonNull)
                .map(User::getEmail)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 구성원 이탈, 그룹 유지(FR-N-02). 나간 사람이 아니라 "몇 명이 남았는지"만 알린다. */
    private static String memberLeftBody(MatchRequest request, int remaining) {
        return """
                밥메이트 구성원 한 명이 그룹에서 나갔어요. 약속은 그대로 유지됩니다.

                지역: %s
                날짜: %s
                끼니: %s
                남은 인원: %d명

                yumm에서 그룹 채팅으로 이어서 이야기해 보세요.
                """.formatted(request.getRegion().getDescription(), request.getMealDate(),
                request.getMealTime().getDescription(), remaining);
    }

    /** 최소 인원 미달로 해체(FR-N-02). 대기열로 돌아간다는 사실까지 적는다(FR-C-03). */
    private static String disbandedBody(MatchRequest request, boolean pastMeal) {
        String tail = pastMeal
                ? "이미 지난 끼니라 다시 매칭하지 않아요. 새 약속은 다시 신청해 주세요."
                : "자동으로 대기열에 다시 올려뒀어요. %d분 안에 새 그룹이 만들어지면 다시 알려드릴게요."
                        .formatted(WAIT_MINUTES);

        return """
                인원이 최소 인원(%d명) 미만이 되어 밥메이트 그룹이 해체됐어요.

                지역: %s
                날짜: %s
                끼니: %s

                %s
                """.formatted(GroupMatcher.MIN_SIZE, request.getRegion().getDescription(), request.getMealDate(),
                request.getMealTime().getDescription(), tail);
    }

    /**
     * 오늘 식사 예정인 성사된 신청에 리마인드를 보낸다(FR-N-04). 보낸 통수를 돌려준다.
     *
     * 만날 시각은 시스템에 없다(MealTime은 점심/저녁뿐). 그래서 본문에 시각을 적지 않고
     * 지역·날짜·끼니·인원까지만 넣는다. 중복 발송은 remindedAt 컬럼 하나로 막는다.
     */
    @Override
    @Transactional
    public int remindTodayMeals() {
        LocalDateTime now = LocalDateTime.now();
        List<MatchRequest> matchedToday = matchRequestRepository.findMatchedOnDate(now.toLocalDate());
        if (matchedToday.isEmpty()) {
            return 0;
        }

        Map<String, Long> sizeByGroup = matchedToday.stream()
                .collect(Collectors.groupingBy(MatchRequest::getGroupId, Collectors.counting()));

        List<Notice> pending = new ArrayList<>();
        for (MatchRequest m : matchedToday) {
            if (m.getRemindedAt() != null) {
                continue;
            }
            // 보냈다는 표시를 발송보다 먼저 커밋한다. 반대로 하면 커밋 실패 시 같은 사람에게 매 주기 또 나간다.
            // 한계: 커밋 뒤 발송 전에 프로세스가 죽으면 그 통은 사라진다. 리마인드는 재발송보다 중복이 나쁘다.
            m.markReminded(now);
            pending.addAll(notices(List.of(m), REMINDER_SUBJECT,
                    reminderBody(m, sizeByGroup.getOrDefault(m.getGroupId(), 1L))));
        }

        notifyAfterCommit(pending);
        return pending.size();
    }

    /**
     * ponytail: 오늘 아침 이후에 성사된 그룹도 한 통 받는다. 매칭 성사 메일(FR-N-01)과 내용이 겹치지만
     * 하루 한 통을 넘지 않고, 겹침을 없애려면 "언제 성사됐는지"를 또 저장해야 한다.
     */
    private static String reminderBody(MatchRequest request, long groupSize) {
        return """
                오늘 밥메이트 약속이 있어요.

                지역: %s
                날짜: %s
                끼니: %s
                인원: %d명

                만날 시간과 장소는 yumm 그룹 채팅에서 확인하세요.
                """.formatted(request.getRegion().getDescription(), request.getMealDate(),
                request.getMealTime().getDescription(), groupSize);
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
