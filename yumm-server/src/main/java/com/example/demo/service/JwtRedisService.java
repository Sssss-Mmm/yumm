package com.example.demo.service; // service 패키지에 생성

import com.example.demo.dto.users.ProfileResponse;
import com.example.demo.util.JwtUtils; // JwtUtils 주입받아 Access Token TTL을 가져오기 위함
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration; // TTL 설정에 사용
import java.util.List;

@Service
public class JwtRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;
    //private final ObjectMapper objectMapper; // JSON 로깅,디버깅

    // 캐시 키 프리픽스
    private static final String PROFILE_CACHE_PREFIX = "userProfile:";
    private static final String REFRESH_TOKEN_PREFIX = "refreshToken:";
    private static final long PROFILE_CACHE_TTL_DAYS = 7; // 7일 동안 캐시 유지

    public JwtRedisService(RedisTemplate<String, Object> redisTemplate, JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.jwtUtils = jwtUtils;
        //this.objectMapper = objectMapper;
    }


    /**
     * Refresh Token을 Redis에 저장합니다.
     * key는 'refreshToken:{userId}', value는 Refresh Token 문자열입니다.
     * TTL (Time To Live)은 Refresh Token의 만료 시간과 동일하게 설정합니다.
     *
     * @param userId         사용자 고유 ID
     * @param refreshToken   Refresh Token 문자열
     * @param refreshTokenMillis Refresh Token의 만료 시간 (밀리초)
     */
    public void saveRefreshToken(Long userId, String refreshToken, long refreshTokenMillis) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        // OpsForValue는 Redis의 String(문자열) 자료구조를 다루는 데 사용됩니다.
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(refreshTokenMillis));
        // key: "refreshToken:1", value: "eyJhbGciOi..." (refresh token string), expire: 7 days
    }


    /**
     * Redis에서 Refresh Token을 조회합니다.
     *
     * @param userId 사용자 고유 ID
     * @return 저장된 Refresh Token 문자열 (없으면 null)
     */
    public String getRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        return (String) redisTemplate.opsForValue().get(key);
    }


    /**
     * Redis에서 Refresh Token을 삭제합니다. (로그아웃 시)
     *
     * @param userId 사용자 고유 ID
     */
    public void deleteRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        redisTemplate.delete(key);
    }


    /**
     * Access Token을 블랙리스트에 추가합니다.
     * Access Token의 남은 만료 시간 동안만 유효합니다.
     * key는 'blacklist:{accessToken}', value는 아무 값이나(ex - logout) 설정합니다.
     *
     * @param accessToken Access Token 문자열
     */
    public void addAccessTokenToBlacklist(String accessToken) {
        String key = "blacklist:" + accessToken.toString();
        
        // Access Token 자체의 만료 시간을 TTL로 설정
        long remainingMillis = jwtUtils.getAccessTokenMillis(); // 또는 토큰에서 직접 남은 시간 계산
        redisTemplate.opsForValue().set(key, "logout", Duration.ofMillis(remainingMillis));
        // key: "blacklist:eyJhbGci...", value: "logout", expire: remaining_access_token_time
    }


    /**
     * Access Token이 블랙리스트에 있는지 확인합니다.
     *
     * @param accessToken Access Token 문자열
     * @return 블랙리스트에 있으면 true, 없으면 false
     */
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String key = "blacklist:" + accessToken.toString();
        return redisTemplate.hasKey(key);
    }


    /**
     * 특정 사용자의 모든 토큰을 무효화합니다. (비밀번호 변경 등 보안 강화 시)
     * Refresh Token을 삭제하고, 현재 Access Token을 블랙리스트에 추가합니다.
     *
     * @param userId 사용자 고유 ID
     * @param currentAccessToken 현재 사용 중인 Access Token 문자열 (선택 사항)
     */
    public void invalidateAllUserTokens(Long userId, String currentAccessToken) {
        // Refresh Token 삭제
        deleteRefreshToken(userId);

        // 현재 사용 중인 Access Token을 블랙리스트에 추가
        if (currentAccessToken != null && !currentAccessToken.isEmpty()) {
            addAccessTokenToBlacklist(currentAccessToken);
        }
        // 만약 추후에 해당 사용자의 다른 모든 세션의 Access Token을 무효화하려면,
        // 모든 발급된 Access Token을 저장하고 관리하는 로직 필요
    }


    /**
     * 사용자 프로필 정보를 Redis에 캐시합니다.
     *
     * @param userId 캐시할 사용자의 고유 ID
     * @param response 캐시할 사용자 프로필 정보
     */
    public void cacheUserProfile(Long userId, ProfileResponse profileResponse) {
        String key = PROFILE_CACHE_PREFIX + userId.toString(); 
        redisTemplate.opsForValue().set(key, profileResponse, Duration.ofDays(PROFILE_CACHE_TTL_DAYS));
    }


    /**
     * 사용자 프로필을 Redis에서 조회합니다.
     *
     * @param userId 조회할 사용자의 ID
     * @return 캐시된 ProfileResponse 객체 또는 null
     */
    public ProfileResponse getCachedUserProfile(Long userId) {
        String key = PROFILE_CACHE_PREFIX + userId.toString();
        Object cachedObject = redisTemplate.opsForValue().get(key);
        
        if (cachedObject instanceof ProfileResponse) {
            return (ProfileResponse) cachedObject;
        }
        return null;
    }

    
    /**
     * Redis에서 특정 사용자의 프로필 캐시를 삭제합니다.
     *
     * @param userId 캐시를 삭제할 사용자의 고유 ID
     */
    public void deleteUserProfileCache(Long userId) {
        String key = PROFILE_CACHE_PREFIX + userId.toString();
        
        redisTemplate.delete(key);
    }


    /**
     * 해당 유저와 관련된 모든 캐시 데이터를 Redis에서 삭제합니다.
     * 
     * @param userId 삭제할 사용자 ID
     */
    public void deleteAllUserCache(Long userId) {
        List<String> keys = List.of(
            PROFILE_CACHE_PREFIX + userId
        );

        redisTemplate.delete(keys);
    }

}