package com.example.demo;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;
import com.example.demo.service.GroupMatcher;
import com.example.demo.service.GroupMatcher.BlockedPair;
import com.example.demo.service.GroupMatcher.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 그룹 편성 로직은 Spring 컨텍스트 없이 순수하게 검증한다. */
class GroupMatcherTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 28, 12, 0);

    /** 폴백 판정 기준 시각. 만료가 이보다 5분 이내로 남아야 2인이 성사된다. */
    private static final LocalDateTime NOW = BASE.plusMinutes(10);

    /**
     * 도착 순서를 id 순으로 두는 헬퍼 (id가 작을수록 먼저 도착). 신청 id와 사용자 id는 같게 둔다.
     * 만료는 NOW+30분이라 2인 폴백 창(5분) 밖이다 — 이 헬퍼로 만든 대기자는 폴백에 걸리지 않는다.
     */
    private static Candidate candidate(long id, Gender gender, GenderPreference pref, FoodCategory... foods) {
        return new Candidate(id, id, gender, pref, Set.of(foods), BASE.plusMinutes(id),
                NOW.plusMinutes(30), false);
    }

    /** 2인 허용을 고른 대기자. expiresIn이 5분 이내면 폴백 대상이 된다. */
    private static Candidate pairOptIn(long id, long expiresInMinutes) {
        return expiring(id, expiresInMinutes, true);
    }

    /** 만료는 임박했지만 2인 허용을 고르지 않은 대기자. 쌍방 조건(FR-G-10)을 가르는 쪽이다. */
    private static Candidate pairOptOut(long id, long expiresInMinutes) {
        return expiring(id, expiresInMinutes, false);
    }

    private static Candidate expiring(long id, long expiresInMinutes, boolean allowPair) {
        return new Candidate(id, id, Gender.MALE, GenderPreference.ANY, Set.of(FoodCategory.KOREAN),
                BASE.plusMinutes(id), NOW.plusMinutes(expiresInMinutes), allowPair);
    }

    /** 한식만 좋아하는 서로 호환되는 대기자 (차단 테스트용) */
    private static Candidate plain(long id) {
        return candidate(id, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN);
    }

    @Test
    @DisplayName("2명뿐이면 최소 인원(3명)에 못 미쳐 그룹이 생기지 않는다")
    void tooFewToForm() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(2, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN));

        assertThat(GroupMatcher.formGroups(waiting, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("조건이 맞는 4명은 한 그룹으로 묶인다")
    void formsFullGroup() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(2, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(3, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(4, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(4);
    }

    @Test
    @DisplayName("성별 조건은 쌍방이 충족해야 한다: 동성만 남 2 + 상관없음 여 2는 아무도 못 묶인다")
    void genderPreferenceIsMutual() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.SAME_ONLY, FoodCategory.KOREAN),
                candidate(2, Gender.MALE, GenderPreference.SAME_ONLY, FoodCategory.KOREAN),
                candidate(3, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(4, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN));

        // 남2는 동성끼리 묶이지만 2명이라 부족하고, 여2는 남자를 받아도 남자가 거절해서 3명을 못 채운다
        assertThat(GroupMatcher.formGroups(waiting, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("동성만 원하는 사람 3명이면 그들끼리 묶인다")
    void sameGenderTrioForms() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(2, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(3, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(4, Gender.MALE, GenderPreference.ANY, FoodCategory.CAFE));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).extracting(Candidate::id).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("도착 순서보다 취향이 겹치는 사람을 먼저 붙인다")
    void prefersOverlappingTastes() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),   // 씨앗
                candidate(2, Gender.MALE, GenderPreference.ANY, FoodCategory.WESTERN),  // 먼저 왔지만 안 겹침
                candidate(3, Gender.MALE, GenderPreference.ANY, FoodCategory.WESTERN),  // 먼저 왔지만 안 겹침
                candidate(4, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),   // 늦게 왔지만 겹침
                candidate(5, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN));  // 늦게 왔지만 겹침

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of());

        assertThat(groups).hasSize(1);
        // 한식 3인이 먼저 붙고, 남은 한 자리는 동점이라 먼저 온 2번이 채운다
        assertThat(groups.get(0)).extracting(Candidate::id).containsExactlyInAnyOrder(1L, 4L, 5L, 2L);
    }

    @Test
    @DisplayName("7명이면 4명 그룹과 3명 그룹으로 나뉜다")
    void splitsIntoTwoGroups() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(2, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(3, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(4, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(5, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(6, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(7, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of());

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(List::size).containsExactly(4, 3);
        // 아무도 두 그룹에 동시에 들어가지 않는다
        assertThat(groups.stream().flatMap(List::stream).map(Candidate::id).distinct().count()).isEqualTo(7L);
    }

    @Test
    @DisplayName("차단 관계인 두 사람은 같은 그룹에 들어가지 않는다")
    void blockedPairNeverShareGroup() {
        List<Candidate> waiting = List.of(plain(1), plain(2), plain(3), plain(4));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(new BlockedPair(1, 2)));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).extracting(Candidate::userId).containsExactlyInAnyOrder(1L, 3L, 4L);
    }

    @Test
    @DisplayName("차단은 어느 방향이든 배제된다 (2가 1을 차단해도 결과가 같다)")
    void blockIsSymmetric() {
        List<Candidate> waiting = List.of(plain(1), plain(2), plain(3), plain(4));

        // 차단한 쪽이 2, 차단당한 쪽이 1인 경우
        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(new BlockedPair(2, 1)));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).extracting(Candidate::userId).containsExactlyInAnyOrder(1L, 3L, 4L);
    }

    @Test
    @DisplayName("차단당한 사람도 차단자가 아닌 다른 사람들과는 정상 편성된다")
    void blockedUserStillMatchesOthers() {
        List<Candidate> waiting = List.of(plain(1), plain(2), plain(3), plain(4), plain(5), plain(6), plain(7));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(new BlockedPair(1, 2)));

        assertThat(groups).hasSize(2);
        List<Candidate> groupOfTwo = groups.stream()
                .filter(g -> g.stream().anyMatch(c -> c.userId() == 2L))
                .findFirst()
                .orElseThrow();
        assertThat(groupOfTwo).extracting(Candidate::userId).containsExactlyInAnyOrder(2L, 6L, 7L);
    }

    // --- 2인 폴백 (FR-G-08~10) ---

    @Test
    @DisplayName("쌍방이 2인 허용이고 만료가 임박하면 2인으로 성사된다")
    void 폴백_성사() {
        List<Candidate> waiting = List.of(pairOptIn(1, 3), pairOptIn(2, 3));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(), NOW);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
    }

    @Test
    @DisplayName("한쪽만 2인 허용이면 2인 그룹을 만들지 않는다")
    void 폴백은_쌍방일_때만() {
        // 상대도 만료가 임박했다. 여기서 안 묶이는 이유는 만료가 아니라 옵트인이 한쪽뿐이어서다.
        List<Candidate> waiting = List.of(pairOptIn(1, 3), pairOptOut(2, 3));

        assertThat(GroupMatcher.formGroups(waiting, Set.of(), NOW)).isEmpty();
    }

    @Test
    @DisplayName("만료까지 5분을 넘게 남기면 아직 2인으로 마감하지 않는다")
    void 폴백은_만료_임박에만() {
        List<Candidate> waiting = List.of(pairOptIn(1, 20), pairOptIn(2, 20));

        assertThat(GroupMatcher.formGroups(waiting, Set.of(), NOW)).isEmpty();
    }

    @Test
    @DisplayName("3인을 만들 수 있으면 2인으로 마감하지 않는다")
    void 삼인이_가능하면_삼인_우선() {
        List<Candidate> waiting = List.of(pairOptIn(1, 3), pairOptIn(2, 3), pairOptIn(3, 3));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(), NOW);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(3);
    }

    @Test
    @DisplayName("차단 관계면 2인 폴백에서도 묶이지 않는다")
    void 폴백도_차단을_지킨다() {
        List<Candidate> waiting = List.of(pairOptIn(1, 3), pairOptIn(2, 3));

        assertThat(GroupMatcher.formGroups(waiting, Set.of(new BlockedPair(1, 2)), NOW)).isEmpty();
    }

    @Test
    @DisplayName("2인 허용자도 옵트인하지 않은 사람들과 3~4인 그룹을 만들 수 있다")
    void 옵트인은_삼사인_편성을_막지_않는다() {
        List<Candidate> waiting = List.of(
                pairOptIn(1, 3),
                candidate(2, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(3, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting, Set.of(), NOW);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(3);
    }
}
