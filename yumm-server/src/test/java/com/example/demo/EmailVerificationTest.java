package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import com.example.demo.dto.match.MatchApplyRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.security.StompSubscriptionRevoker;
import com.example.demo.service.impl.MatchServiceImpl;
import com.example.demo.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이메일 인증(FR-A-03). 게이트는 매칭 신청 하나뿐이고, 인증 자체는 발송 → 확인 두 단계다.
 * 분기는 "코드가 없다/틀렸다/맞다" 셋과 신청 시 "인증됐다/아니다" 둘이라 목으로 전부 훑는다.
 */
class EmailVerificationTest {

    private static final Long USER_ID = 42L;

    private UserRepository userRepository;
    private JwtRedisService jwtRedisService;
    private EmailService emailService;
    private UserServiceImpl userService;

    private MatchRequestRepository matchRequestRepository;
    private MatchServiceImpl matchService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtRedisService = mock(JwtRedisService.class);
        emailService = mock(EmailService.class);
        userService = new UserServiceImpl(userRepository, mock(PasswordEncoder.class), jwtRedisService,
                mock(MatchRequestRepository.class), mock(MatchService.class), emailService);

        matchRequestRepository = mock(MatchRequestRepository.class);
        matchService = new MatchServiceImpl(matchRequestRepository, userRepository, mock(UserBlockRepository.class),
                emailService, mock(StompSubscriptionRevoker.class));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
    }

    private static User user() {
        return User.builder()
                .id(USER_ID)
                .email("me@test.com")
                .password("$2a$10$hashedhashedhashedhashedhashedhashedhashedhashedhashe")
                .nickname("먹보")
                .gender(Gender.FEMALE)
                .birthYear(1995)
                .role(UserRole.ROLE_USER)
                .build();
    }

    private static MatchApplyRequest applyRequest() {
        MatchApplyRequest request = mock(MatchApplyRequest.class);
        when(request.getMealDate()).thenReturn(LocalDate.now());
        return request;
    }

    @Test
    @DisplayName("인증 코드는 6자리 숫자로 Redis에 저장되고 가입 이메일로 나간다")
    void sendsSixDigitCode() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.sendEmailVerification(USER_ID);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(jwtRedisService).saveEmailVerificationCode(eq(USER_ID), code.capture());
        assertThat(code.getValue()).matches("\\d{6}");
        // 메일 본문에 코드가 실제로 들어가야 사용자가 입력할 수 있다
        verify(emailService).send(eq("me@test.com"), anyString(), org.mockito.ArgumentMatchers.contains(code.getValue()));
    }

    @Test
    @DisplayName("코드가 만료됐거나 발급된 적이 없으면 인증에 실패한다")
    void expiredCodeIsRejected() {
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> userService.confirmEmailVerification(USER_ID, "123456"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
    }

    @Test
    @DisplayName("코드가 틀리면 인증되지 않고 코드도 지워지지 않는다")
    void wrongCodeIsRejected() {
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn("111111");

        assertThatThrownBy(() -> userService.confirmEmailVerification(USER_ID, "222222"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
        verify(jwtRedisService, never()).deleteEmailVerificationCode(USER_ID);
    }

    @Test
    @DisplayName("코드가 맞으면 인증 시각이 찍히고 코드는 재사용되지 않게 지워진다")
    void correctCodeVerifies() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn("111111");

        userService.confirmEmailVerification(USER_ID, "111111");

        assertThat(user.isEmailVerified()).isTrue();
        verify(jwtRedisService).deleteEmailVerificationCode(USER_ID);
    }

    @Test
    @DisplayName("미인증 사용자의 매칭 신청은 EMAIL_NOT_VERIFIED로 막힌다")
    void unverifiedCannotApply() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> matchService.apply(USER_ID, applyRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                // 웹(Match.tsx)이 이 이름 문자열로 인증 창을 띄운다. 바뀌면 화면이 깨진다
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(matchRequestRepository, never()).save(any(MatchRequest.class));
    }

    @Test
    @DisplayName("이메일을 인증하면 매칭을 신청할 수 있다")
    void verifiedCanApply() {
        User user = user();
        user.verifyEmail(LocalDateTime.now());
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        matchService.apply(USER_ID, applyRequest());

        verify(matchRequestRepository).save(any(MatchRequest.class));
    }

    @Test
    @DisplayName("이메일을 바꾸면 인증이 풀린다 - 인증된 계정으로 아무 주소나 쓸 수 없다")
    void changingEmailResetsVerification() {
        User user = user();
        user.verifyEmail(LocalDateTime.now());

        user.updateEmail("other@test.com");

        assertThat(user.isEmailVerified()).isFalse();
    }
}
