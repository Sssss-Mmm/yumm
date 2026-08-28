package com.example.demo.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessTokenResponseDto {

    private String accessToken;
    private String refreshToken;

    public static AccessTokenResponseDto of(String accessToken, String refreshToken) {
        return AccessTokenResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
    }
}
