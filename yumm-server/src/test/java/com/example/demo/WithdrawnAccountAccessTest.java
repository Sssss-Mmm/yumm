package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.StompSubscriptionRevoker;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.impl.MatchServiceImpl;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴한 계정은 이미 발급된 Access Token(최대 10시간)으로 서비스를 계속 쓸 수 있으면 안 된다(FR-A-08).
 *
 * 검사를 컨트롤러마다 흩어 놓으면 새 엔드포인트에서 빠지므로, 모든 인증 요청이 지나는 JwtAuthenticationFilter
 * 한 곳에서 거른다. 여기서 확인하는 것은 "탈퇴자의 토큰은 컨트롤러까지 못 간다",
 * "정상 계정은 그대로 통과한다", "탈퇴가 이메일 인증까지 무효화해 매칭 신청 게이트도 닫힌다" 셋이다.
 */
class WithdrawnAccountAccessTest {

    private static final Long USER_ID = 7L;
    private static final String TOKEN = "header.payload.signature";

    private JwtUtils jwtUtils;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        userRepository = mock(UserRepository.class);
        when(jwtUtils.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(jwtUtils.getEmailFromToken(TOKEN)).thenReturn("me@yumm.local");
        when(jwtUtils.getRolesFromToken(TOKEN)).thenReturn(List.of("ROLE_USER"));
        filter = new JwtAuthenticationFilter(jwtUtils, mock(JwtRedisService.class), userRepository);
    }

    private MockHttpServletRequest matchApplyRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/match");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    @Test
    @DisplayName("탈퇴한 계정의 토큰으로 보낸 매칭 신청은 필터에서 끊겨 컨트롤러까지 가지 못한다")
    void withdrawnTokenIsRejected() throws Exception {
        when(userRepository.existsByIdAndWithdrawnAtIsNotNull(USER_ID)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(matchApplyRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("WITHDRAWN_ACCOUNT");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("탈퇴하지 않은 계정은 그대로 통과한다")
    void activeTokenPasses() throws Exception {
        when(userRepository.existsByIdAndWithdrawnAtIsNotNull(USER_ID)).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(matchApplyRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("탈퇴하면 이메일 인증 기록도 지워져 매칭 신청의 인증 게이트를 통과하지 못한다")
    void withdrawClearsEmailVerification() {
        User user = User.builder()
                .id(USER_ID)
                .email("me@yumm.local")
                .password("$2a$10$hashedhashedhashedhashedhashedhashedhashedhashedhashe")
                .nickname("먹보")
                .gender(Gender.MALE)
                .birthYear(1995)
                .role(UserRole.ROLE_USER)
                .build();
        user.verifyEmail(LocalDateTime.now());

        user.withdraw(LocalDateTime.now());

        assertThat(user.isEmailVerified()).isFalse();

        MatchRequestRepository matchRequests = mock(MatchRequestRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        MatchServiceImpl matchService = new MatchServiceImpl(matchRequests, users, mock(UserBlockRepository.class),
                mock(EmailService.class), mock(StompSubscriptionRevoker.class));

        assertThatThrownBy(() -> matchService.apply(USER_ID, new MatchApplyRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);
    }
}
