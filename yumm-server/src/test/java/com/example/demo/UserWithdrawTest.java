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

    private UserRepository userRepository;
    private MatchRequestRepository matchRequestRepository;
    private MatchService matchService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        matchRequestRepository = mock(MatchRequestRepository.class);
        matchService = mock(MatchService.class);
        userService = new UserServiceImpl(userRepository, mock(PasswordEncoder.class), mock(JwtRedisService.class),
                matchRequestRepository, matchService);
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

        userService.withdraw(USER_ID);

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

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("매칭된 탈퇴자는 그룹 이탈로, 대기 중인 탈퇴자는 신청 취소로 정리된다")
    void withdrawClearsOngoingMatch() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "me@yumm.local")));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(matchRequest(MatchStatus.MATCHED)));

        userService.withdraw(USER_ID);

        // 그룹 해체(FR-C-03)가 leaveGroup에 딸려 있으므로 탈퇴 전용 경로를 만들지 않는다
        verify(matchService).leaveGroup(USER_ID);
        verify(matchService, never()).cancel(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "me2@yumm.local")));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(matchRequest(MatchStatus.WAITING)));

        userService.withdraw(USER_ID);

        verify(matchService).cancel(USER_ID);
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
