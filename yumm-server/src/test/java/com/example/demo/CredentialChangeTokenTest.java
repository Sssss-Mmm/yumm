package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import com.example.demo.dto.users.ChangePasswordRequest;
import com.example.demo.dto.users.EmailUpdateRequest;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비밀번호·이메일(로그인 ID) 변경은 "털린 것 같다"에 대한 대응이다.
 * 요청에 실린 Access Token을 블랙리스트에 넣지 않으면 그 토큰이 만료(10시간)까지 살아 있어
 * 바꾼 의미가 없다. 탈퇴 경로와 같은 규칙을 두 경로에서도 확인한다.
 */
class CredentialChangeTokenTest {

    private static final Long USER_ID = 7L;
    private static final String ACCESS_TOKEN = "header.payload.signature";

    private UserRepository userRepository;
    private JwtRedisService jwtRedisService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtRedisService = mock(JwtRedisService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$newhash");

        userService = new UserServiceImpl(userRepository, passwordEncoder, jwtRedisService,
                mock(MatchRequestRepository.class), mock(MatchService.class), mock(EmailService.class));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID)
                .email("me@yumm.local")
                .password("$2a$10$oldhash")
                .nickname("먹보")
                .gender(Gender.MALE)
                .birthYear(1995)
                .role(UserRole.ROLE_USER)
                .build()));
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 그 요청에 실린 Access Token이 블랙리스트로 간다")
    void changePasswordBlacklistsCurrentToken() {
        ChangePasswordRequest request = mock(ChangePasswordRequest.class);
        when(request.getOldPassword()).thenReturn("old-password");
        when(request.getNewPassword()).thenReturn("new-password");

        userService.changePassword(USER_ID, request, ACCESS_TOKEN);

        verify(jwtRedisService).invalidateAllUserTokens(USER_ID, ACCESS_TOKEN);
    }

    @Test
    @DisplayName("로그인 ID(이메일)를 바꾸면 그 요청에 실린 Access Token이 블랙리스트로 간다")
    void updateEmailBlacklistsCurrentToken() {
        EmailUpdateRequest request = mock(EmailUpdateRequest.class);
        when(request.getCurrentPassword()).thenReturn("old-password");
        when(request.getNewEmail()).thenReturn("new@yumm.local");
        when(userRepository.findByEmail("new@yumm.local")).thenReturn(Optional.empty());

        userService.updateEmail(USER_ID, request, ACCESS_TOKEN);

        verify(jwtRedisService).invalidateAllUserTokens(USER_ID, ACCESS_TOKEN);
    }
}
