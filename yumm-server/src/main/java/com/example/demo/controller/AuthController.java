package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.auth.*;
import com.example.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;


/**
 * 인증(Authentication) 및 권한(Authorization) 관련 API를 처리하는 컨트롤러입니다.
 * 사용자 로그인, 로그아웃, Access Token 재발급 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 사용자 로그인 API.
     * 아이디(이메일)와 비밀번호를 통해 사용자를 인증하고, Access Token과 Refresh Token을 발급합니다.
     *
     * @param request 로그인 요청에 필요한 아이디(이메일)와 비밀번호를 담은 DTO
     * @return 로그인 성공 시 발급된 Access Token 및 Refresh Token 정보를 포함하는 응답
     */
    @PostMapping("/login")
    @Operation(summary = "사용자 로그인", description = "아이디/비밀번호를 통해 로그인하고 JWT를 반환합니다.")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@RequestBody LoginRequest loginRequest) {

        AuthResponseDto response = authService.login(loginRequest);

        return ApiResponse.ok("로그인 성공", response);
    }


    /**
     * 사용자 로그아웃 API.
     * 현재 로그인된 사용자의 Refresh Token을 Redis에서 삭제하여 세션을 무효화합니다.
     * Security Context도 함께 초기화됩니다.
     *
     * @param requestDto Refresh Token을 포함하는 요청 DTO.
     * @return 로그아웃 성공 메시지를 포함하는 응답
     */
    @PostMapping("/logout")
    @Operation(summary = "사용자 로그아웃", description = "refreshToken을 요청 본문에 담아 세션을 무효화하여 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody LogoutRequest logoutRequest) {
        
        authService.logout(logoutRequest);

        return ApiResponse.ok("로그아웃 성공");
    }


    /**
     * Access Token 재발급 API.
     * 만료된 Access Token 대신 Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.
     * Refresh Token은 HTTP 요청 헤더의 Authorization 필드나 쿠키에서 전달될 수 있습니다.
     *
     * @param authorizationHeader HTTP 요청 헤더의 Authorization 필드 값 (Bearer RefreshToken 형태)
     * @param refreshTokenCookie HTTP 요청 쿠키에서 추출된 Refresh Token 값
     * @return 새로 발급된 Access Token 및 갱신된 Refresh Token 정보를 포함하는 응답
     */
    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰을 기반으로 새로운 액세스 토큰을 발급합니다. 헤더 또는 쿠키에서 refreshToken을 전달합니다.")
    public ResponseEntity<ApiResponse<AccessTokenResponseDto>> refreshAccessToken(
                                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader, 
                                        @CookieValue(name = "refreshToken", required = false) String refreshTokenCookie) {
     
        AccessTokenResponseDto response = authService.refreshAccessToken(authorizationHeader, refreshTokenCookie);

        return ApiResponse.ok("액세스 토큰 재발급 성공", response);
    }

}