package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.GenderPreference;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.Region;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.User;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.impl.MatchServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2인 허용 옵트인이 신청 요청에서 편성 후보까지 끊기지 않고 전달되는지 본다(FR-M-12).
 *
 * <p>편성 로직 자체는 GroupMatcherTest가 덮는다. 여기서 막는 건 다른 종류의 결함이다 —
 * 배선이 끊기면 사용자가 체크박스를 눌러도 폴백 대상이 되지 않는데, 그 상태로도 편성
 * 테스트는 전부 통과한다. {@code Candidate}가 record 위치 인자라 조용히 어긋날 수 있는 자리다.
 */
class MatchAllowPairWiringTest {

    /** 요청 본문을 DTO로 푸는 데만 쓴다. allowPair의 JSON 키가 바뀌면 여기서 먼저 깨진다. */
    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.now();

    private MatchRequestRepository matchRequestRepository;
    private UserBlockRepository userBlockRepository;
    private MatchServiceImpl service;

    @BeforeEach
    void setUp() {
        matchRequestRepository = mock(MatchRequestRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        service = new MatchServiceImpl(matchRequestRepository, userRepository, userBlockRepository, mock(EmailService.class));

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(
                User.builder().id(USER_ID).nickname("서연").gender(Gender.FEMALE).build()));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
    }

    private static MatchApplyRequest applyRequest(boolean allowPair) {
        MatchApplyRequest request = mock(MatchApplyRequest.class);
        when(request.getMealDate()).thenReturn(TODAY);
        when(request.isAllowPair()).thenReturn(allowPair);
        return request;
    }

    private boolean savedAllowPair(boolean requested) {
        service.apply(USER_ID, applyRequest(requested));
        ArgumentCaptor<MatchRequest> saved = ArgumentCaptor.forClass(MatchRequest.class);
        verify(matchRequestRepository).save(saved.capture());
        return saved.getValue().isAllowPair();
    }

    @Test
    @DisplayName("2인 허용을 고르면 그대로 저장된다")
    void 옵트인이_저장된다() {
        assertThat(savedAllowPair(true)).isTrue();
    }

    @Test
    @DisplayName("고르지 않으면 false로 저장된다 (기본값 미선택)")
    void 미선택이_기본이다() {
        assertThat(savedAllowPair(false)).isFalse();
    }

    /**
     * 목이 아니라 실제 요청 본문에서 시작한다. 위 두 테스트는 {@code MatchApplyRequest}를 목으로 두므로
     * DTO 필드명이 바뀌어도(=클라이언트가 보내는 {@code allowPair} 키가 안 붙어도) 통과한다.
     * POST /api/match의 본문 → DTO → 저장되는 {@code MatchRequest}까지가 이 테스트의 범위다.
     * 컨트롤러는 서비스로 그대로 넘기기만 해서 스프링 컨텍스트 없이 본문 역직렬화로 대신한다.
     */
    @Test
    @DisplayName("요청 본문의 allowPair가 저장되는 MatchRequest까지 도달한다")
    void 요청_본문에서_저장까지_전달된다() throws Exception {
        String body = """
                {"region":"GANGNAM","mealDate":"%s","mealTime":"LUNCH","genderPreference":"ANY",
                 "foodPreferences":["KOREAN"],"allowPair":true}
                """.formatted(TODAY);

        service.apply(USER_ID, MAPPER.readValue(body, MatchApplyRequest.class));

        ArgumentCaptor<MatchRequest> saved = ArgumentCaptor.forClass(MatchRequest.class);
        verify(matchRequestRepository).save(saved.capture());
        assertThat(saved.getValue().isAllowPair()).isTrue();
        // 본문이 실제로 바인딩됐는지(전 필드가 null인 채 통과하는 것 방지)
        assertThat(saved.getValue().getRegion()).isEqualTo(Region.GANGNAM);
    }

    /**
     * 저장까지 맞아도 편성 후보로 옮길 때 어긋나면 폴백이 안 돈다. {@code toCandidate}는 private이라
     * 직접 부를 수 없으므로 편성 결과로 확인한다 — 옵트인이나 만료 시각이 후보로 넘어가지 않으면
     * 2인이 성사되지 않는다.
     */
    @Test
    @DisplayName("저장된 allowPair·만료가 편성 후보까지 전달되어 2인이 성사된다")
    void 후보까지_전달된다() {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(3); // 폴백 창(5분) 안
        when(matchRequestRepository.findWaitingInBucket(any(), any(), any(), any()))
                .thenReturn(List.of(waiting(1L, 11L, true, soon), waiting(2L, 12L, true, soon)));
        when(userBlockRepository.findPairsAmong(any())).thenReturn(List.of());

        assertThat(service.formGroupsInBucket(Region.GANGNAM, TODAY, MealTime.LUNCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("옵트인하지 않았으면 같은 조건에서도 2인이 성사되지 않는다")
    void 옵트인이_없으면_성사되지_않는다() {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(3);
        when(matchRequestRepository.findWaitingInBucket(any(), any(), any(), any()))
                .thenReturn(List.of(waiting(1L, 11L, false, soon), waiting(2L, 12L, false, soon)));
        when(userBlockRepository.findPairsAmong(any())).thenReturn(List.of());

        assertThat(service.formGroupsInBucket(Region.GANGNAM, TODAY, MealTime.LUNCH)).isZero();
    }

    private static MatchRequest waiting(long id, long userId, boolean allowPair, LocalDateTime expiresAt) {
        return MatchRequest.builder()
                .id(id)
                .user(User.builder().id(userId).gender(Gender.FEMALE).build())
                .region(Region.GANGNAM)
                .mealDate(TODAY)
                .mealTime(MealTime.LUNCH)
                .genderPreference(GenderPreference.ANY)
                .foodPreferences(Set.of())
                .status(MatchStatus.WAITING)
                .createdAt(LocalDateTime.now().minusMinutes(id))
                .expiresAt(expiresAt)
                .allowPair(allowPair)
                .build();
    }
}
