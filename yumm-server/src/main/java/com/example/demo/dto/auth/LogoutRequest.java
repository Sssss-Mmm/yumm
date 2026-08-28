package com.example.demo.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 로그아웃 요청 데이터
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutRequest {
    private String refreshToken;
}
