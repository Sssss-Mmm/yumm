package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import com.example.demo.dto.auth.AuthResponseDto;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

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
 * 남용 방지(FR-S-07 / FR-S-08).
 *
 * 세 가지 제한이 전부 "카운터를 세다가 임계를 넘으면 막는다"는 같은 모양이라 한 곳에 모았다.
 * 확인할 분기는 임계 미만(통과) / 임계 초과(차단) / 정상 사용자(영향 없음) 셋이다.
 * Redis는 목이다 - 여기서 검증하는 것은 카운터 값이 아니라 임계를 넘겼을 때의 서비스 동작이다.
 */
class AbuseGuardTest {

    private static final Long USER_ID = 7L;
    private static final String EMAIL = "me@yumm.local";
    private static final String IP = "203.0.113.10";

    private UserRepository userRepository;
    private JwtRedisService jwtRedisService;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtRedisService = mock(JwtRedisService.class);
        emailService = mock(EmailService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        userService = new UserServiceImpl(userRepository, passwordEncoder, jwtRedisService,
                mock(MatchRequestRepository.class), mock(MatchService.class), emailService);

        JwtUtils jwtUtils = mock(JwtUtils.class);
        when(jwtUtils.generateAccessToken(any(), anyString(), any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(any(), anyString(), any())).thenReturn("refresh-token");
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtUtils, jwtRedisService);
    }

    private static User user() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password("$2a$10$hashedhashedhashedhashedhashedhashedhashedhashedhashe")
                .nickname("먹보")
                .gender(Gender.FEMALE)
                .birthYear(1995)
                .role(UserRole.ROLE_USER)
                .build();
    }

    private static LoginRequest loginRequest() {
        return LoginRequest.builder().email(EMAIL).password("whatever1234").build();
    }

    // ----------------------------------------------------------------
    // 1. 이메일 인증 코드 시도 제한 (FR-S-07)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("오입력이 임계 미만이면 코드는 살아 있다 - 오타 낸 사용자가 다시 넣을 수 있어야 한다")
    void wrongCodeUnderThresholdKeepsCode() {
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn("111111");
        when(jwtRedisService.increaseEmailVerifyFailCount(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> userService.confirmEmailVerification(USER_ID, "222222"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

        verify(jwtRedisService, never()).deleteEmailVerificationCode(USER_ID);
    }

    @Test
    @DisplayName("오입력이 임계를 넘으면 그 코드는 무효가 되고 429로 막힌다")
    void wrongCodeOverThresholdInvalidatesCode() {
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn("111111");
        when(jwtRedisService.increaseEmailVerifyFailCount(USER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> userService.confirmEmailVerification(USER_ID, "222222"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);

        // 코드를 지우지 않으면 남은 TTL 동안 대입을 계속 이어갈 수 있다
        verify(jwtRedisService).deleteEmailVerificationCode(USER_ID);
        // 카운터도 비워야 다음에 받은 코드가 첫 시도부터 막히지 않는다
        verify(jwtRedisService).clearEmailVerifyFailCount(USER_ID);
    }

    @Test
    @DisplayName("코드가 없는 상태의 시도는 세지 않는다 - 셀 대상이 없다")
    void noCodeIsNotCounted() {
        when(jwtRedisService.getEmailVerificationCode(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> userService.confirmEmailVerification(USER_ID, "222222"))
                .isInstanceOf(CustomException.class);

        verify(jwtRedisService, never()).increaseEmailVerifyFailCount(USER_ID);
    }

    // ----------------------------------------------------------------
    // 2. 인증 메일 발송 쿼터 (FR-S-08)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("쿨다운 중에는 메일이 나가지 않고 남은 초가 응답에 실린다")
    void resendDuringCooldownIsBlocked() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(jwtRedisService.getEmailResendCooldownSeconds(USER_ID)).thenReturn(42L);

        CustomException thrown = (CustomException) org.assertj.core.api.Assertions
                .catchThrowable(() -> userService.sendEmailVerification(USER_ID));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
        // 화면이 카운트다운하려면 남은 시간을 알아야 한다
        assertThat(thrown.getRetryAfterSeconds()).isEqualTo(42L);
        // SMTP 쿼터를 태우지 않는 것이 이 제한의 목적이다
        verify(emailService, never()).send(anyString(), anyString(), anyString());
        verify(jwtRedisService, never()).saveEmailVerificationCode(eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("쿨다운이 지났으면 메일이 나가고 다음 쿨다운이 걸린다")
    void resendAfterCooldownSends() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(jwtRedisService.getEmailResendCooldownSeconds(USER_ID)).thenReturn(0L);

        userService.sendEmailVerification(USER_ID);

        verify(emailService).send(eq(EMAIL), anyString(), anyString());
        verify(jwtRedisService).startEmailResendCooldown(USER_ID);
        // 새 코드에는 시도 횟수를 새로 준다
        verify(jwtRedisService).clearEmailVerifyFailCount(USER_ID);
    }

    // ----------------------------------------------------------------
    // 3. 로그인 시도 제한 (FR-S-07)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("비밀번호 실패가 임계에 닿으면 해당 (IP, 이메일) 조합이 잠긴다")
    void repeatedLoginFailureLocks() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(jwtRedisService.getLoginLockSeconds(IP, EMAIL)).thenReturn(0L);
        when(jwtRedisService.increaseLoginFailCount(IP, EMAIL)).thenReturn(5L);

        assertThatThrownBy(() -> authService.login(loginRequest(), IP))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(jwtRedisService).lockLogin(IP, EMAIL);
    }

    @Test
    @DisplayName("잠긴 동안에는 비밀번호를 보지도 않고 429로 막고 남은 초를 준다")
    void lockedLoginIsRejectedBeforePasswordCheck() {
        when(jwtRedisService.getLoginLockSeconds(IP, EMAIL)).thenReturn(600L);

        CustomException thrown = (CustomException) org.assertj.core.api.Assertions
                .catchThrowable(() -> authService.login(loginRequest(), IP));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        assertThat(thrown.getRetryAfterSeconds()).isEqualTo(600L);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("남이 다른 곳에서 실패시켜도 본인은 로그인된다 - 잠금이 서비스 거부가 되면 안 된다")
    void lockOnAnotherSourceDoesNotBlockOwner() {
        String victimIp = "198.51.100.20";
        // 공격자 IP는 잠겨 있지만 피해자 본인의 출처는 깨끗하다
        when(jwtRedisService.getLoginLockSeconds(IP, EMAIL)).thenReturn(600L);
        when(jwtRedisService.getLoginLockSeconds(victimIp, EMAIL)).thenReturn(0L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthResponseDto response = authService.login(loginRequest(), victimIp);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        // 성공했으면 카운터를 비운다. 안 그러면 오타 몇 번 낸 사람이 다음 로그인에서 갑자기 잠긴다
        verify(jwtRedisService).clearLoginFailCount(victimIp, EMAIL);
    }

    @Test
    @DisplayName("없는 계정에 대한 시도도 실패로 센다 - 안 세면 '안 잠기는 이메일 = 없는 계정'이 된다")
    void unknownEmailIsAlsoCounted() {
        when(jwtRedisService.getLoginLockSeconds(IP, EMAIL)).thenReturn(0L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest(), IP))
                .isInstanceOf(CustomException.class);

        verify(jwtRedisService).increaseLoginFailCount(IP, EMAIL);
    }

    // ----------------------------------------------------------------
    // 4. 운영 프로파일 Swagger 차단
    // ----------------------------------------------------------------

    @Test
    @DisplayName("운영 프로파일에서는 API 문서가 꺼져 있다 - 비로그인으로 전체 스펙이 열리면 안 된다")
    void swaggerIsDisabledInProd() throws Exception {
        Properties prod = new Properties();
        try (InputStream in = new ClassPathResource("application-prod.properties").getInputStream()) {
            prod.load(in);
        }

        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("개발 프로파일에서는 그대로 켜져 있다")
    void swaggerStaysOnInDefaultProfile() throws Exception {
        Properties base = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            base.load(in);
        }

        assertThat(base.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
    }
}
