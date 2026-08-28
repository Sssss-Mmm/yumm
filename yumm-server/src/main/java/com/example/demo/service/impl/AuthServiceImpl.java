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

    /** 사용자 로그인 처리 */
    @Override
    public AuthResponseDto login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();

        // 사용자 조회
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 검증
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // AccessToken 생성
        List<String> roles = List.of(user.getRole().getRoleName());
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), roles);
        
        // RefreshToken 생성 및 Redis에 저장(userId가 key값)
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(),user.getEmail(),roles);
        jwtRedisService.saveRefreshToken(user.getId(), refreshToken, jwtUtils.getRefreshTokenMillis());
       
        // 응답 DTO 반환
        return AuthResponseDto.of(accessToken, refreshToken);
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