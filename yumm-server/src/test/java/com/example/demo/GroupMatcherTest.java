package com.example.demo;

import com.example.demo.domain.FoodCategory;
import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;
import com.example.demo.service.GroupMatcher;
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

    /** 도착 순서를 id 순으로 두는 헬퍼 (id가 작을수록 먼저 도착) */
    private static Candidate candidate(long id, Gender gender, GenderPreference pref, FoodCategory... foods) {
        return new Candidate(id, gender, pref, Set.of(foods), BASE.plusMinutes(id));
    }

    @Test
    @DisplayName("2명뿐이면 최소 인원(3명)에 못 미쳐 그룹이 생기지 않는다")
    void tooFewToForm() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(2, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN));

        assertThat(GroupMatcher.formGroups(waiting)).isEmpty();
    }

    @Test
    @DisplayName("조건이 맞는 4명은 한 그룹으로 묶인다")
    void formsFullGroup() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(2, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(3, Gender.MALE, GenderPreference.ANY, FoodCategory.KOREAN),
                candidate(4, Gender.FEMALE, GenderPreference.ANY, FoodCategory.KOREAN));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting);

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
        assertThat(GroupMatcher.formGroups(waiting)).isEmpty();
    }

    @Test
    @DisplayName("동성만 원하는 사람 3명이면 그들끼리 묶인다")
    void sameGenderTrioForms() {
        List<Candidate> waiting = List.of(
                candidate(1, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(2, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(3, Gender.FEMALE, GenderPreference.SAME_ONLY, FoodCategory.CAFE),
                candidate(4, Gender.MALE, GenderPreference.ANY, FoodCategory.CAFE));

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting);

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

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting);

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

        List<List<Candidate>> groups = GroupMatcher.formGroups(waiting);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(List::size).containsExactly(4, 3);
        // 아무도 두 그룹에 동시에 들어가지 않는다
        assertThat(groups.stream().flatMap(List::stream).map(Candidate::id).distinct().count()).isEqualTo(7L);
    }
}
