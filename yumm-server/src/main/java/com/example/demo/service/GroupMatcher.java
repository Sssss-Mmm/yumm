package com.example.demo.service;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;

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

    private GroupMatcher() {}

    public record Candidate(
            Long id,        // MatchRequest의 id (대기열의 한 자리)
            Long userId,    // 사용자 id. 차단은 신청이 아니라 사람 사이의 관계라 따로 필요하다
            Gender gender,
            GenderPreference genderPreference,
            Set<FoodCategory> foodPreferences,
            LocalDateTime createdAt) {
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
        return groups;
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
