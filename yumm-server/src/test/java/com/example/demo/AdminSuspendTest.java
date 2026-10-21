package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.User;
import com.example.demo.repository.AttendanceCheckRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.service.impl.AdminServiceImpl;
import com.example.demo.util.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 관리자 계정 정지(FR-D-02).
 *
 * 정지는 "앞으로 못 들어온다"만으로는 부족하다. 이미 대기열에 있으면 편성돼 남의 약속을 깨고,
 * 이미 발급된 토큰으로 계속 요청할 수 있다. 그래서 정리(대기 취소·그룹 이탈·재발급 차단)와
 * 필터 차단을 함께 확인한다.
 */
class AdminSuspendTest {

    private static final Long USER_ID = 7L;

    private UserRepository userRepository;
    private MatchRequestRepository matchRequestRepository;
    private MatchService matchService;
    private JwtRedisService jwtRedisService;
    private AdminServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        matchRequestRepository = mock(MatchRequestRepository.class);
        matchService = mock(MatchService.class);
        jwtRedisService = mock(JwtRedisService.class);
        service = new AdminServiceImpl(mock(ReportRepository.class), userRepository, matchRequestRepository,
                matchService, jwtRedisService, mock(AttendanceCheckRepository.class));
        user = User.builder().id(USER_ID).email("u@yumm.test").password("hashed")
                .nickname("먹보").gender(Gender.MALE).birthYear(1995).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void latestRequest(MatchStatus status) {
        MatchRequest m = mock(MatchRequest.class);
        when(m.getStatus()).thenReturn(status);
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(m));
    }

    @Test
    @DisplayName("대기 중에 정지되면 신청을 취소하고, 그룹에서 빠지고, 재발급 토큰을 지운다")
    void 대기중_정지() {
        latestRequest(MatchStatus.WAITING);

        service.suspendUser(USER_ID);

        assertThat(user.isSuspended()).isTrue();
        verify(matchService).cancel(USER_ID);
        verify(matchService).leaveAllGroups(USER_ID);
        verify(jwtRedisService).deleteRefreshToken(USER_ID);
    }

    @Test
    @DisplayName("매칭된 상태면 취소는 건너뛰고 그룹 이탈만 한다")
    void 매칭중_정지() {
        latestRequest(MatchStatus.MATCHED);

        service.suspendUser(USER_ID);

        verify(matchService, never()).cancel(USER_ID);
        verify(matchService).leaveAllGroups(USER_ID);
    }

    @Test
    @DisplayName("이미 정지된 계정은 정지 시각도 정리도 다시 하지 않는다")
    void 재정지는_무시() {
        LocalDateTime first = LocalDateTime.now().minusDays(1);
        user.suspend(first);

        service.suspendUser(USER_ID);

        assertThat(user.getSuspendedAt()).isEqualTo(first);
        verifyNoInteractions(matchService, jwtRedisService);
    }

    @Test
    @DisplayName("해제하면 정상 계정으로 돌아온다")
    void 해제() {
        user.suspend(LocalDateTime.now());

        service.unsuspendUser(USER_ID);

        assertThat(user.isSuspended()).isFalse();
    }

    @Test
    @DisplayName("정지 전에 발급된 토큰은 필터에서 끊긴다")
    void 정지계정_토큰_차단() throws Exception {
        String token = "header.payload.signature";
        JwtUtils jwtUtils = mock(JwtUtils.class);
        when(jwtUtils.getUserIdFromToken(token)).thenReturn(USER_ID);
        when(userRepository.existsByIdAndSuspendedAtIsNotNull(USER_ID)).thenReturn(true);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, mock(JwtRedisService.class), userRepository);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/match");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ACCOUNT_SUSPENDED");
        verify(chain, never()).doFilter(any(), any());
    }
}
