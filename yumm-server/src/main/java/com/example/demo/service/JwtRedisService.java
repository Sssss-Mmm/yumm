package com.example.demo.service; // service 패키지에 생성

import com.example.demo.dto.users.ProfileResponse;
import com.example.demo.util.JwtUtils; // JwtUtils 주입받아 Access Token TTL을 가져오기 위함
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration; // TTL 설정에 사용
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;
    //private final ObjectMapper objectMapper; // JSON 로깅,디버깅

    // 캐시 키 프리픽스
    private static final String PROFILE_CACHE_PREFIX = "userProfile:";
    private static final String REFRESH_TOKEN_PREFIX = "refreshToken:";
    private static final String EMAIL_VERIFY_PREFIX = "emailVerify:";
    private static final String EMAIL_VERIFY_FAIL_PREFIX = "emailVerifyFail:";
    private static final String EMAIL_RESEND_PREFIX = "emailResend:";
    private static final String LOGIN_FAIL_PREFIX = "loginFail:";
    private static final String LOGIN_LOCK_PREFIX = "loginLock:";
    /** 이메일 인증 코드 유효 시간(분). 메일 본문에도 그대로 적는다. */
    public static final long EMAIL_VERIFY_TTL_MINUTES = 10;
    /** 인증 메일 재발송 쿨다운(초). */
    public static final long EMAIL_RESEND_COOLDOWN_SECONDS = 60;
    /** 연속 실패로 인정하는 시간 창(분). 이 시간 안에 몰린 실패만 누적된다. */
    public static final long LOGIN_FAIL_WINDOW_MINUTES = 10;
    /** 임계를 넘긴 (IP, 이메일) 조합을 막아 두는 시간(분). 지나면 저절로 풀린다. */
    public static final long LOGIN_LOCK_MINUTES = 10;
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
     * 이메일 인증 코드를 Redis에 저장합니다(FR-A-03).
     * key는 'emailVerify:{userId}'. 같은 키에 덮어쓰므로 재발송하면 이전 코드는 그 자리에서 무효가 됩니다.
     * TTL이 지나면 키가 사라져 만료 처리가 됩니다.
     *
     * @param userId 사용자 고유 ID
     * @param code   인증 코드
     */
    public void saveEmailVerificationCode(Long userId, String code) {
        redisTemplate.opsForValue().set(EMAIL_VERIFY_PREFIX + userId,
                code, Duration.ofMinutes(EMAIL_VERIFY_TTL_MINUTES));
    }


    /**
     * 이메일 인증 코드를 조회합니다. 만료됐거나 발급한 적이 없으면 null입니다.
     *
     * @param userId 사용자 고유 ID
     * @return 저장된 인증 코드 (없으면 null)
     */
    public String getEmailVerificationCode(Long userId) {
        Object code = redisTemplate.opsForValue().get(EMAIL_VERIFY_PREFIX + userId);
        return code == null ? null : code.toString();
    }


    /**
     * 사용한 이메일 인증 코드를 삭제합니다. 같은 코드를 두 번 쓰지 못하게 합니다.
     *
     * @param userId 사용자 고유 ID
     */
    public void deleteEmailVerificationCode(Long userId) {
        redisTemplate.delete(EMAIL_VERIFY_PREFIX + userId);
    }


    /**
     * 이메일 인증 코드 오입력 횟수를 1 올리고 누적 횟수를 반환합니다(FR-S-07).
     * 카운터 TTL은 코드 TTL과 같아서 코드가 만료되면 카운터도 같이 사라집니다.
     *
     * @param userId 사용자 고유 ID
     * @return 이번 실패까지 누적된 오입력 횟수
     */
    public long increaseEmailVerifyFailCount(Long userId) {
        return increaseCounter(EMAIL_VERIFY_FAIL_PREFIX + userId,
                Duration.ofMinutes(EMAIL_VERIFY_TTL_MINUTES));
    }


    /**
     * 이메일 인증 코드 오입력 횟수를 지웁니다. 인증 성공 또는 새 코드 발급 시 호출합니다.
     *
     * @param userId 사용자 고유 ID
     */
    public void clearEmailVerifyFailCount(Long userId) {
        redisTemplate.delete(EMAIL_VERIFY_FAIL_PREFIX + userId);
    }


    /**
     * 인증 메일 재발송 쿨다운을 시작합니다(FR-S-08). 키의 TTL이 그대로 남은 대기 시간입니다.
     *
     * @param userId 사용자 고유 ID
     */
    public void startEmailResendCooldown(Long userId) {
        redisTemplate.opsForValue().set(EMAIL_RESEND_PREFIX + userId, "sent",
                Duration.ofSeconds(EMAIL_RESEND_COOLDOWN_SECONDS));
    }


    /**
     * 인증 메일 재발송까지 남은 시간(초)을 반환합니다. 0 이하면 지금 보낼 수 있습니다.
     *
     * @param userId 사용자 고유 ID
     * @return 남은 초 (쿨다운이 없으면 0)
     */
    public long getEmailResendCooldownSeconds(Long userId) {
        return remainingSeconds(EMAIL_RESEND_PREFIX + userId);
    }


    /**
     * 로그인 실패 횟수를 1 올리고 누적 횟수를 반환합니다(FR-S-07).
     * 키는 요청 출처 IP와 이메일 해시의 조합입니다 - 계정 단위로 잠그면
     * 남의 이메일로 일부러 실패시켜 그 계정을 잠그는 서비스 거부가 가능해집니다.
     *
     * @param clientIp 요청 출처 IP
     * @param email    로그인 시도에 쓰인 이메일
     * @return 이번 실패까지 누적된 실패 횟수
     */
    public long increaseLoginFailCount(String clientIp, String email) {
        return increaseCounter(loginKey(LOGIN_FAIL_PREFIX, clientIp, email),
                Duration.ofMinutes(LOGIN_FAIL_WINDOW_MINUTES));
    }


    /**
     * 해당 (IP, 이메일) 조합의 로그인을 일정 시간 막습니다. 누적 카운터는 함께 비웁니다.
     *
     * @param clientIp 요청 출처 IP
     * @param email    로그인 시도에 쓰인 이메일
     */
    public void lockLogin(String clientIp, String email) {
        redisTemplate.opsForValue().set(loginKey(LOGIN_LOCK_PREFIX, clientIp, email), "locked",
                Duration.ofMinutes(LOGIN_LOCK_MINUTES));
        redisTemplate.delete(loginKey(LOGIN_FAIL_PREFIX, clientIp, email));
    }


    /**
     * 로그인 잠금이 풀리기까지 남은 시간(초)입니다. 0 이하면 지금 시도할 수 있습니다.
     *
     * @param clientIp 요청 출처 IP
     * @param email    로그인 시도에 쓰인 이메일
     * @return 남은 초 (잠금이 없으면 0)
     */
    public long getLoginLockSeconds(String clientIp, String email) {
        return remainingSeconds(loginKey(LOGIN_LOCK_PREFIX, clientIp, email));
    }


    /**
     * 로그인 실패 카운터를 지웁니다. 로그인 성공 시 호출합니다.
     *
     * @param clientIp 요청 출처 IP
     * @param email    로그인에 쓰인 이메일
     */
    public void clearLoginFailCount(String clientIp, String email) {
        redisTemplate.delete(loginKey(LOGIN_FAIL_PREFIX, clientIp, email));
    }


    /**
     * 로그인 실패/잠금 키를 만듭니다.
     *
     * 이메일을 평문으로 쓰면 "누구 계정을 노렸는지" 목록이 Redis에 그대로 남습니다(NFR-05).
     * 키는 같은 값인지만 구분하면 되므로 해시로 충분합니다.
     */
    private static String loginKey(String prefix, String clientIp, String email) {
        String hashed = UUID.nameUUIDFromBytes(
                email.toLowerCase().getBytes(StandardCharsets.UTF_8)).toString();
        return prefix + clientIp + ":" + hashed;
    }


    /**
     * 카운터를 1 올립니다. 값이 처음 생길 때만 TTL을 겁니다 -
     * 실패할 때마다 TTL을 갱신하면 계속 시도하는 것만으로 잠금이 무한정 연장됩니다.
     */
    private long increaseCounter(String key, Duration window) {
        // INCR로 쓴 값은 value 직렬화(JSON)를 거치지 않는다. 그래서 이 키는 get()으로 읽지 않고
        // INCR 반환값과 TTL만 쓴다.
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 1L : count;
    }


    /** 키의 남은 TTL(초). 키가 없거나 TTL이 없으면 0. */
    private long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
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