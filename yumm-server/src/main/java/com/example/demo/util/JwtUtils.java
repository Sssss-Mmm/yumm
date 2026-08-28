package com.example.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Base64;

@Component
public class JwtUtils {

    private static final String BEARER_PREFIX = "Bearer ";
    
    private final Key key;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenMillis;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenMillis;

    public JwtUtils(@Value("${JWT_SECRET}") String secretKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        this.key = Keys.hmacShaKeyFor(keyBytes); // key 객체 생성
    }

    /**
     * AccessToken 생성
     * 사용자 ID(email)와 권한 정보를 JWT Claim에 포함
     * 
     * @param userId 사용자 고유 ID(DB PK)
     * @param email 사용자 로그인 ID
     * @param roles 사용자 권한 목록 (ROLE_USER, ROLE_ADMIN)
     * @return AccessToken 문자열
     */
    public String generateAccessToken(Long userId, String email, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + accessTokenMillis))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }


    /**
     * RefreshToken 생성
     * 사용자 ID(email)와 권한 정보를 JWT Claim에 포함
     * 
     * @param userId 사용자 고유 ID(DB PK)
     * @param email 사용자 로그인 ID
     * @param roles 사용자 권한 목록 (ROLE_USER, ROLE_ADMIN)
     * @return AccessToken 문자열
     */
    public String generateRefreshToken(Long userId, String email, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email",email)
            .claim("roles",roles)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + refreshTokenMillis))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }


    // 리프레시 토큰 유효기간 반환
    public long getRefreshTokenMillis() {
        return refreshTokenMillis;
    }


    // 액세스 토큰 유효기간 반환
    public long getAccessTokenMillis() {
        return accessTokenMillis;
    }


    /**
     * Access Token 또는 Refresh Token에서 userId 추출
     *
     * @param token JWT 문자열
     * @return 추출된 userId (Long)
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(1)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.valueOf(claims.getSubject()); // sub 클레임에서 userId 추출
    }

    /**
     * Access Token 또는 Refresh Token에서 email 추출
     *
     * @param token JWT 문자열
     * @return 추출된 email (String)
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(1)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("email", String.class);
    }

    /**
     * Access Token 또는 Refresh Token에서 roles 추출
     *
     * @param token JWT 문자열
     * @return 추출된 roles (List<String>)
     */
    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(1)
                .build()
                .parseClaimsJws(token)
                .getBody();              
        return (List<String>) claims.get("roles");
    }

    /**
     * 토큰 유효성 검증 로직
     * 
     * parseClaimsJws(token) 호출 시, 내부적으로 다음 항목 점검
     * 
     * 서명 검증 : 토큰이 signWith(..)로 서명된 비밀 키와 일치하는지 확인(위조 토큰 차단)
     * 만료 시각 : 현재 시간보다 exp 값이 지났다면 expiredJwtException 발생
     * 잘못된 형식/무결성 : 토큰의 구조적 손상이나 잘못된 포맷일 경우 IllegalArgumentException 발생
     * 
     * @param token : 접두어("bearer") 제거한 실제 토큰 문자열
     * @throws JwtException : 유효하지 않은 토큰일 경우 예외 발생
     */
    public void validation(String token) throws JwtException {
        try {
            Jwts.parserBuilder()
            .setSigningKey(key)
            .setAllowedClockSkewSeconds(1)
            .build()
            .parseClaimsJws(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("토큰이 유효하지 않습니다.",ex);
        }
    }


    /**
     * Authorization 헤더에서 'Bearer ' 접두어를 제거하고 토큰 본문만 반환합니다.
     * 
     * @param bearerHeader 전체 Authorization 헤더 값 
     * @return 실제 토큰 문자열
     * @throws IllegalArgumentException 접두어가 없으면 예외 발생
     */
    public static String extractTokenFrom(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtException("Authorization 헤더 형식이 잘못되었습니다. 'Bearer {token}' 형식이어야 합니다.");
        }
        return bearerHeader.substring(BEARER_PREFIX.length());
    }    


    /**
     * Authorization 헤더 또는 Cookie에서 토큰을 추출합니다.
     * 헤더 우선 → 없으면 Cookie → 둘 다 없으면 예외 발생.
     *
     * @param authorizationHeader Authorization 헤더 (nullable)
     * @param refreshTokenCookie  쿠키 값 (nullable)
     * @return JWT 토큰 문자열
     * @throws ResponseStatusException 둘 다 없을 경우
     */
    public static String extractTokenFromHeaderOrCookie(String authorizationHeader, String refreshTokenCookie) {
        try {
            return extractTokenFrom(authorizationHeader);
        } catch (JwtException e) {
            if (refreshTokenCookie != null) {
                return refreshTokenCookie;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }
    }
    
}