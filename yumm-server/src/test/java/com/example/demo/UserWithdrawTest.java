package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportReason;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.service.impl.AuthServiceImpl;
import com.example.demo.service.impl.UserServiceImpl;
import com.example.demo.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원 탈퇴는 하드 삭제가 아니라 익명화다(FR-A-08).
 *
 * 신고·차단·채팅·매칭 신청이 users 행을 NOT NULL FK로 물고 있어 삭제하면 FK 위반으로 500이 나고,
 * 성공하더라도 방침이 보존하기로 한 신고 이력이 함께 사라진다. 여기서 확인하는 것은
 * "행을 지우지 않는다", "개인정보는 비운다", "탈퇴 계정은 로그인하지 못한다" 셋이다.
 */
class UserWithdrawTest {

    private static final Long USER_ID = 7L;

    private static final String ACCESS_TOKEN = "header.payload.signature";

    private UserRepository userRepository;
    private JwtRedisService jwtRedisService;
    private MatchRequestRepository matchRequestRepository;
    private MatchService matchService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        matchRequestRepository = mock(MatchRequestRepository.class);
        matchService = mock(MatchService.class);
        jwtRedisService = mock(JwtRedisService.class);
        userService = new UserServiceImpl(userRepository, mock(PasswordEncoder.class), jwtRedisService,
                matchRequestRepository, matchService, mock(EmailService.class));
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("$2a$10$hashedhashedhashedhashedhashedhashedhashedhashedhashe")
                .nickname("먹보")
                .gender(Gender.MALE)
                .birthYear(1995)
                .profileImageUrl("https://example.com/me.png")
                .role(UserRole.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("신고 기록이 있어도 탈퇴가 성공하고, 신고 행이 가리키는 사용자 행은 지워지지 않는다")
    void withdrawKeepsReportedUserRow() {
        User reported = user(USER_ID, "me@yumm.local");
        User reporter = user(9L, "other@yumm.local");
        Report report = Report.builder()
                .reporter(reporter)
                .reported(reported)
                .reason(ReportReason.NO_SHOW)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(reported));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        userService.withdraw(USER_ID, ACCESS_TOKEN);

        // 삭제하면 reports.reported_id(NOT NULL FK)가 깨진다. 행은 남아야 한다.
        verify(userRepository, never()).delete(any());
        assertThat(report.getReported()).isSameAs(reported);
        assertThat(report.getReported().getId()).isEqualTo(USER_ID);

        // 남은 행에서 개인정보는 비워져 있어야 한다
        assertThat(reported.isWithdrawn()).isTrue();
        assertThat(reported.getEmail()).isEqualTo("withdrawn-7@yumm.invalid").isNotEqualTo("me@yumm.local");
        assertThat(reported.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(reported.getProfileImageUrl()).isNull();
        assertThat(reported.getPassword()).isNotBlank().doesNotStartWith("$2a$");

        // 탈퇴 요청에 실린 Access Token은 그 자리에서 죽어야 한다. null을 넘기면 블랙리스트에
        // 아무것도 안 들어가 만료(10시간)까지 그 토큰으로 서비스를 계속 쓸 수 있다.
        verify(jwtRedisService).invalidateAllUserTokens(USER_ID, ACCESS_TOKEN);
    }

    @Test
    @DisplayName("탈퇴한 계정으로는 로그인할 수 없다")
    void withdrawnAccountCannotLogin() {
        User withdrawn = user(USER_ID, "me@yumm.local");
        withdrawn.withdraw(LocalDateTime.now());

        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("withdrawn-7@yumm.invalid")).thenReturn(Optional.of(withdrawn));
        AuthServiceImpl authService = new AuthServiceImpl(users, mock(PasswordEncoder.class), mock(JwtUtils.class),
                mock(JwtRedisService.class));

        LoginRequest request = LoginRequest.builder()
                .email("withdrawn-7@yumm.invalid")
                .password("whatever1234")
                .build();

        assertThatThrownBy(() -> authService.login(request, "10.0.0.1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("매칭된 탈퇴자는 그룹 이탈로, 대기 중인 탈퇴자는 신청 취소로 정리된다")
    void withdrawClearsOngoingMatch() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "me@yumm.local")));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(matchRequest(MatchStatus.MATCHED)));

        userService.withdraw(USER_ID, ACCESS_TOKEN);

        // 그룹 해체(FR-C-03)와 구독 해제(FR-T-02)가 이탈 경로에 딸려 있으므로 탈퇴 전용 경로를 만들지 않는다
        verify(matchService).leaveAllGroups(USER_ID);
        verify(matchService, never()).cancel(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "me2@yumm.local")));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(matchRequest(MatchStatus.WAITING)));

        userService.withdraw(USER_ID, ACCESS_TOKEN);

        verify(matchService).cancel(USER_ID);
    }

    /**
     * 어제 매칭된 뒤(MATCHED) 오늘 재신청한(WAITING) 계정. 최근 1건만 보면 어제 행의 groupId가 살아남아
     * 탈퇴 후에도 그 채팅방을 계속 읽고 쓸 수 있다. 최근 행의 상태와 무관하게 그룹 정리가 돌아야 한다.
     */
    @Test
    @DisplayName("최근 신청이 대기 중이어도 오래된 MATCHED 행의 그룹까지 정리한다")
    void withdrawClearsOlderMatchedGroup() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "me@yumm.local")));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(matchRequest(MatchStatus.WAITING)));

        userService.withdraw(USER_ID, ACCESS_TOKEN);

        verify(matchService).cancel(USER_ID);
        verify(matchService).leaveAllGroups(USER_ID);
    }

    private MatchRequest matchRequest(MatchStatus status) {
        return MatchRequest.builder()
                .id(1L)
                .status(status)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }
}
