package com.example.demo.service;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 버킷(같은 지역/날짜/시간대) 안의 대기자들을 3~4인 그룹으로 묶는 순수 로직.
 *
 * FAISS 같은 근사 최근접 검색을 쓰지 않는 이유: top-k는 "A와 가까운 3명"을 주지만
 * 그 3명끼리 서로 맞는지는 보장하지 않는다. 그룹 매칭에 필요한 건 최근접이 아니라
 * 서로 호환되는 집합이다. 게다가 한 버킷의 대기자는 많아야 수십 명이라
 * 완전탐색으로 충분하다.
 */
public final class GroupMatcher {

    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 4;

    /** 2인 폴백으로 성사되는 그룹 크기(FR-G-08). */
    public static final int PAIR_SIZE = 2;

    /** 폴백을 허용하는 만료 잔여 시간(FR-G-09). 이보다 여유가 있으면 3인을 더 기다린다. */
    public static final Duration PAIR_FALLBACK_WINDOW = Duration.ofMinutes(5);

    private GroupMatcher() {}

    public record Candidate(
            Long id,        // MatchRequest의 id (대기열의 한 자리)
            Long userId,    // 사용자 id. 차단은 신청이 아니라 사람 사이의 관계라 따로 필요하다
            Gender gender,
            GenderPreference genderPreference,
            Set<FoodCategory> foodPreferences,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,   // 2인 폴백의 잔여시간 판정에 쓴다(FR-G-09)
            boolean allowPair) {       // 2인 허용 옵트인(FR-M-12)
    }

    /**
     * 차단 관계 한 쌍. 어느 쪽이 차단했는지는 편성에서 중요하지 않으므로
     * 생성 시점에 작은 id를 앞으로 정렬해 방향을 지운다. 대칭성이 자료구조로 보장되니
     * 두 방향을 각각 넣어줄 필요도, 한쪽을 빠뜨릴 여지도 없다.
     */
    public record BlockedPair(long low, long high) {
        public BlockedPair {
            if (low > high) {
                long swap = low;
                low = high;
                high = swap;
            }
        }
    }

    /**
     * 대기자 목록을 그룹들로 나눈다. 어느 그룹에도 못 들어간 사람은 결과에서 빠지고
     * 대기열에 그대로 남는다.
     *
     * ponytail: 오래 기다린 사람을 씨앗으로 삼아 한 명씩 붙이는 그리디.
     * 전역 최적해가 아니라 "먼저 온 사람이 먼저 매칭된다"를 보장하는 쪽을 택했다.
     * 대기자가 수백 명 규모로 커지고 매칭 품질이 문제되면 그때 지역 탐색을 얹으면 된다.
     */
    public static List<List<Candidate>> formGroups(List<Candidate> waiting, Set<BlockedPair> blocked) {
        return formGroups(waiting, blocked, LocalDateTime.now());
    }

    /**
     * @param now 만료 잔여시간 판정 기준 시각. 테스트가 시계를 고정할 수 있도록 인자로 받는다.
     */
    public static List<List<Candidate>> formGroups(List<Candidate> waiting, Set<BlockedPair> blocked,
                                                   LocalDateTime now) {
        List<Candidate> pool = new ArrayList<>(waiting);
        pool.sort(Comparator.comparing(Candidate::createdAt).thenComparing(Candidate::id));

        List<List<Candidate>> groups = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        for (Candidate seed : pool) {
            if (used.contains(seed.id())) continue;

            List<Candidate> group = new ArrayList<>();
            group.add(seed);

            while (group.size() < MAX_SIZE) {
                Candidate best = findBestFit(pool, used, group, blocked);
                if (best == null) break;
                group.add(best);
            }

            // 3명을 못 채웠으면 성사시키지 않는다. 씨앗은 used에 넣지 않으므로
            // 다음 씨앗의 그룹에 합류할 기회가 남는다.
            if (group.size() >= MIN_SIZE) {
                groups.add(group);
                group.forEach(c -> used.add(c.id()));
            }
        }

        groups.addAll(formPairs(pool, used, blocked, now));
        return groups;
    }

    /**
     * 3~4인 편성에서 남은 사람들 중 2인 그룹을 만든다(FR-G-08).
     *
     * <p>순서가 중요하다. 3인을 만들 수 있는데 2인으로 마감하면 손해이므로 폴백은 항상 뒤다.
     * 조건은 셋이다 — 쌍방이 2인 허용을 골랐고(FR-G-10), 둘 다 만료가 임박했고(FR-G-09),
     * 성별·차단 조건이 맞아야 한다.
     *
     * <p>allowPair를 {@link #compatible}에 넣지 않은 이유: 그건 2인 성사 조건이지 일반 호환성이
     * 아니다. 거기 넣으면 옵트인한 사람이 옵트인하지 않은 사람들과 3~4인 그룹을 만들지 못한다.
     */
    private static List<List<Candidate>> formPairs(List<Candidate> pool, Set<Long> used,
                                                   Set<BlockedPair> blocked, LocalDateTime now) {
        List<Candidate> eligible = pool.stream()
                .filter(c -> !used.contains(c.id()))
                .filter(Candidate::allowPair)
                .filter(c -> expiringSoon(c, now))
                .toList();

        List<List<Candidate>> pairs = new ArrayList<>();
        for (Candidate seed : eligible) {
            if (used.contains(seed.id())) continue;
            for (Candidate other : eligible) { // eligible은 pool 정렬을 물려받아 먼저 온 사람이 먼저다
                if (other.id().equals(seed.id()) || used.contains(other.id())) continue;
                if (!compatible(seed, other, blocked)) continue;

                pairs.add(List.of(seed, other));
                used.add(seed.id());
                used.add(other.id());
                break;
            }
        }
        return pairs;
    }

    /** 만료까지 남은 시간이 폴백 창 이내인가(FR-G-09). 이미 만료된 건은 상위에서 걸러져 들어오지 않는다. */
    static boolean expiringSoon(Candidate c, LocalDateTime now) {
        return Duration.between(now, c.expiresAt()).compareTo(PAIR_FALLBACK_WINDOW) <= 0;
    }

    /** 그룹 전원과 호환되는 후보 중 취향이 가장 많이 겹치는 사람. 동점이면 먼저 온 사람. */
    private static Candidate findBestFit(List<Candidate> pool, Set<Long> used, List<Candidate> group,
                                        Set<BlockedPair> blocked) {
        Candidate best = null;
        int bestScore = -1;

        for (Candidate c : pool) {
            if (used.contains(c.id()) || containsId(group, c.id())) continue;
            if (!compatibleWithAll(c, group, blocked)) continue;

            int score = score(c, group);
            if (score > bestScore) { // pool이 대기순으로 정렬돼 있어 동점은 먼저 온 사람이 이긴다
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    static boolean compatibleWithAll(Candidate c, List<Candidate> group, Set<BlockedPair> blocked) {
        return group.stream().allMatch(member -> compatible(c, member, blocked));
    }

    /**
     * 성별 조건은 쌍방 충족이어야 한다. 한쪽만 만족하는 매칭은 성사시키지 않는다.
     * 차단도 같은 이유로 쌍방 배제다 — 누가 누구를 차단했든 둘은 같은 그룹에 들어가지 않는다(FR-S-02).
     */
    static boolean compatible(Candidate a, Candidate b, Set<BlockedPair> blocked) {
        if (blocked.contains(new BlockedPair(a.userId(), b.userId()))) return false;
        return accepts(a, b) && accepts(b, a);
    }

    private static boolean accepts(Candidate x, Candidate other) {
        return x.genderPreference() == GenderPreference.ANY || x.gender() == other.gender();
    }

    /** 그룹 구성원들과 겹치는 음식 취향의 총 개수. */
    static int score(Candidate c, List<Candidate> group) {
        int total = 0;
        for (Candidate member : group) {
            Set<FoodCategory> overlap = new HashSet<>(c.foodPreferences());
            overlap.retainAll(member.foodPreferences());
            total += overlap.size();
        }
        return total;
    }

    private static boolean containsId(List<Candidate> group, Long id) {
        return group.stream().anyMatch(c -> c.id().equals(id));
    }
}
