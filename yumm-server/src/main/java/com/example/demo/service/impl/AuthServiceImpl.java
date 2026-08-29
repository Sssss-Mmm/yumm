package com.example.demo.service.impl;

import com.example.demo.service.AuthService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.domain.User;
import com.example.demo.dto.auth.AccessTokenResponseDto;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.AuthResponseDto;
import com.example.demo.dto.auth.LogoutRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.JwtUtils;

import jakarta.transaction.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
       
    private final UserRepository            userRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JwtUtils                  jwtUtils;
    private final JwtRedisService           jwtRedisService;

    /**
     * 허용하는 연속 로그인 실패 횟수(FR-S-07).
     *
     * 8자 이상 비밀번호를 5회로 맞힐 확률은 사실상 0이고, 사람이 오타를 내는 횟수로는 넉넉하다.
     * 카운터 창(10분)이 지나면 저절로 풀리므로 관리자 개입이 필요 없다.
     */
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 사용자 로그인 처리.
     *
     * 대입 방지 카운터는 계정이 아니라 <b>(요청 출처 IP + 이메일) 조합</b>에 건다.
     * 계정 단위로 잠그면 남의 이메일로 일부러 5번 틀리는 것만으로 그 사람을 로그인 못 하게 만들 수 있다 -
     * 즉 잠금 자체가 서비스 거부 수단이 된다. 조합 단위면 공격자는 자기 출처만 잠그고,
     * 피해자는 자기 IP의 카운터가 깨끗하므로 평소대로 로그인된다.
     *
     * ponytail: 여러 IP에 흩어진 대입은 이 층에서 못 막는다. 그건 앞단 rate limit의 몫이고,
     * 실제로 그런 트래픽이 관측되면 그때 붙인다.
     *
     * @param loginRequest 이메일/비밀번호
     * @param clientIp     요청 출처 IP(카운터 키의 일부)
     */
    @Override
    public AuthResponseDto login(LoginRequest loginRequest, String clientIp) {
        String email = loginRequest.getEmail();

        // 이미 잠긴 조합이면 비밀번호를 보기 전에 끊는다.
        long locked = jwtRedisService.getLoginLockSeconds(clientIp, email);
        if (locked > 0) {
            throw new CustomException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, locked);
        }

        // 사용자 조회. 없는 계정도 실패로 센다 -
        // 세지 않으면 "아무리 시도해도 안 잠기는 이메일 = 가입 안 된 이메일"이 되어 계정 존재 여부가 샌다.
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                countFailure(clientIp, email);
                return new CustomException(ErrorCode.USER_NOT_FOUND);
            });

        // 탈퇴한 계정은 비밀번호가 익명화 값으로 덮여 있어 어차피 검증을 통과하지 못한다.
        // INVALID_CREDENTIALS로 흘려보내면 본인이 탈퇴한 사실을 모른 채 비밀번호만 다시 시도하게 되므로
        // 여기서 명시적으로 끊는다(FR-A-08).
        if (user.isWithdrawn()) {
            throw new CustomException(ErrorCode.WITHDRAWN_ACCOUNT);
        }

        // 비밀번호 검증
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            countFailure(clientIp, email);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 성공했으면 카운터를 비운다. 안 그러면 오타 몇 번 낸 사람이 다음 로그인에서 갑자기 잠긴다.
        jwtRedisService.clearLoginFailCount(clientIp, email);

        // AccessToken 생성
        List<String> roles = List.of(user.getRole().getRoleName());
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), roles);
        
        // RefreshToken 생성 및 Redis에 저장(userId가 key값)
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(),user.getEmail(),roles);
        jwtRedisService.saveRefreshToken(user.getId(), refreshToken, jwtUtils.getRefreshTokenMillis());
       
        // 응답 DTO 반환
        return AuthResponseDto.of(accessToken, refreshToken);
    }


    /**
     * 실패를 1회 센다. 횟수·이메일은 로그에 남기지 않는다(NFR-05) - 카운터는 Redis에만 있다.
     */
    private void countFailure(String clientIp, String email) {
        if (jwtRedisService.increaseLoginFailCount(clientIp, email) >= MAX_LOGIN_ATTEMPTS) {
            jwtRedisService.lockLogin(clientIp, email);
        }
    }


    /** 사용자 로그아웃 처리 */
    @Override
    @Transactional
    public void logout(LogoutRequest logoutRequest) {
        String refreshToken = logoutRequest.getRefreshToken();

        // refreshToken 유효성 검증
        jwtUtils.validation(refreshToken);

        // token에서 userId 추출
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);

        // Redis에 저장된 rfToken과 요청된 토큰 일치 여부
        String storedRefreshToken = jwtRedisService.getRefreshToken(userId);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            // 이 경우, 유효하지 않은 (탈취되었거나 이미 사용된) 토큰으로 간주
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        
        // userId로 Redis에서 Refresh Token 삭제
        jwtRedisService.deleteRefreshToken(userId); 
       
        // 인증 객체 삭제(SecurityContext Clear)
        SecurityContextHolder.clearContext();      
    }


    /** Access Token 재발급 처리 */
    @Override
    public AccessTokenResponseDto refreshAccessToken(String authorizationHeader, String refreshTokenCookie) {
        // JwtUtils를 통해 validation 검사 및 토큰 추출
        String refreshToken = JwtUtils.extractTokenFromHeaderOrCookie(authorizationHeader, refreshTokenCookie);

        // RefreshToken 유효성 검증
        jwtUtils.validation(refreshToken);

        // RefreshToken에서 userId 추출
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);

        // Redis에 저장된 RefreshToken과 재발급을 위해 요청받은 토큰 일치 여부 확인
        String storedRefreshToken = jwtRedisService.getRefreshToken(userId);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            // Redis에 없거나, 클라이언트가 보낸 토큰과 Redis의 토큰이 일치하지 않는 경우
            // -> 유효하지 않은 Refresh Token이므로 예외처리 
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // 새 Access Token 생성 (email, roles 포함)
        List<String> roles = jwtUtils.getRolesFromToken(refreshToken);
        String email = jwtUtils.getEmailFromToken(refreshToken);
        String newAccessToken = jwtUtils.generateAccessToken(userId, email, roles);

        // Refresh Token 재발급 (슬라이딩 윈도우 방식)
        String newRefreshToken = jwtUtils.generateRefreshToken(userId, email, roles);
        jwtRedisService.saveRefreshToken(userId, newRefreshToken, jwtUtils.getRefreshTokenMillis());

        // 응답 DTO 반환
        return AccessTokenResponseDto.of(newAccessToken, newRefreshToken);
    }

}