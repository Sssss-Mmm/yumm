package com.example.demo.service;

import com.example.demo.dto.auth.AccessTokenResponseDto;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.AuthResponseDto;
import com.example.demo.dto.auth.LogoutRequest;

/**
 * 인증 관련 서비스 인터페이스
 * 
 * 로그인, 로그아웃, 액세스 토큰 재발급 기능 정의.
 */
public interface AuthService {

    /**
     * 사용자를 인증하고 Access Token 및 Refresh Token을 발급합니다.
     *
     * @param request 로그인 요청에 필요한 사용자 이메일과 비밀번호를 담은 DTO.
     * @return 로그인 성공 시 발급된 Access Token과 Refresh Token 정보를 담은 DTO.
     */
    AuthResponseDto login(LoginRequest loginRequest);

        
    /**
     * 현재 로그인된 사용자를 로그아웃 처리합니다.
     * Redis에 저장된 해당 사용자의 Refresh Token을 무효화합니다.
     *
     * @param userId 로그아웃할 사용자의 고유 ID.
     */
    void logout(LogoutRequest logoutRequest);


    /**
     * Refresh Token을 사용하여 Access Token을 재발급합니다.
     * Refresh Token의 유효성을 검증하고, Redis에 저장된 토큰과 일치하는지 확인합니다.
     * 성공 시 새로운 Access Token과 (선택적으로 갱신된) Refresh Token을 반환합니다.
     *
     * @param authorizationHeader HTTP 요청 헤더의 `Authorization` 필드 값. (일반적으로 만료된 Access Token 또는 Bearer Refresh Token 형태).
     * @param refreshTokenCookie HTTP 요청 쿠키에서 추출된 `refreshToken` 값.
     * @return 새로 발급된 Access Token 및 갱신된 Refresh Token 정보를 담은 DTO.
     */
    AccessTokenResponseDto refreshAccessToken(String authorizationHeader, String refreshTokenCookie);
 
}