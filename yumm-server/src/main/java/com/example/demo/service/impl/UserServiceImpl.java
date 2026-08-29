package com.example.demo.service.impl;

import com.example.demo.service.UserService;
import com.example.demo.domain.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.dto.users.*;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.service.EmailService;
import com.example.demo.service.JwtRedisService;
import com.example.demo.service.MatchService;
import com.example.demo.domain.MatchStatus;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.domain.UserRole;
import com.example.demo.domain.Gender;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Year;


@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtRedisService jwtRedisService;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchService matchService;
    private final EmailService emailService;

    private static final String VERIFY_SUBJECT = "[yumm] 이메일 인증 코드";
    // 예측 가능한 코드는 남의 계정을 인증해 줄 수 있으므로 Random이 아니라 SecureRandom을 쓴다.
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    /**
     * 인증 코드 오입력 허용 횟수(FR-S-07).
     *
     * 코드는 6자리 숫자(경우의 수 1,000,000)다. 5회를 허용하면 코드 하나를 맞힐 확률은
     * 5/1,000,000 = 0.0005%이고, 임계를 넘기면 코드를 버리므로 이어서 대입할 수 없다.
     * 새 코드는 재발송 쿨다운(60초)을 거쳐야 나오므로 시도 속도는 분당 5회로 묶인다.
     * 사람이 오타 두세 번 낼 여지는 남기는 선이라 5로 잡았다.
     */
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    
    /** 회원 가입 */
    @Override
    @Transactional
    public void signup(SignupRequest signupRequest) {

        // 사용자 중복 검사(이메일 존재 시 예외처리)
        validateDuplicateEmail(signupRequest.getEmail());

        // 출생연도 검증 + 성인 여부 판정
        validateAdultBirthYear(signupRequest.getBirthYear(), Year.now().getValue());

        // 사용자 원문 비밀번호를 BCrypt 해시 알고리즘으로 암호화
        String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());

        // 타입캐스팅(String -> Enum)
        Gender genderEnum;
        try {
            genderEnum = Gender.valueOf(signupRequest.getGender().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_GENDER);
        }

        // UserRole ENUM 타입 (기본값, 나중에 admin계정으로 관리하는 필드)
        UserRole defaultRole = UserRole.ROLE_USER;

        // 회원가입 요청 정보를 바탕으로 User 엔티티 생성
        // profileImage는 기본 이미지가 있으면 나중에 추가할 것
        User newUser = User.builder()
                .email(signupRequest.getEmail())
                .password(encodedPassword)
                .nickname(signupRequest.getNickname())
                .gender(genderEnum)
                .birthYear(signupRequest.getBirthYear())
                .profileImageUrl(signupRequest.getProfileImageUrl()) 
                .role(defaultRole)
                .termsAgreedAt(LocalDateTime.now()) // 동의 여부는 @AssertTrue가 걸러내므로 여기 오면 동의한 것이다
                .build();

        // 회원 정보 DB에 저장
        userRepository.save(newUser);
    }


    /**
     * 가입 가능한 출생연도인지 검증한다 (FR-A-04 / BR-08).
     *
     * 생년월일이 아니라 출생연도만 받으므로 생일이 아직 지나지 않았다고 가정해 보수적으로 판정한다.
     * 즉 '올해 - 출생연도 >= 20'일 때만 만 19세 이상으로 본다. 이 때문에 경계에 걸린 사람은
     * 최대 1년 늦게 가입하게 되지만, 미성년자를 실수로 받는 것보다 그쪽이 낫다.
     *
     * @param birthYear   가입 요청의 출생연도
     * @param currentYear 판정 기준 연도(보통 올해)
     */
    public static void validateAdultBirthYear(Integer birthYear, int currentYear) {
        if (birthYear == null || birthYear < 1900 || birthYear > currentYear) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }
        if (currentYear - birthYear < 20) {
            throw new CustomException(ErrorCode.UNDERAGE_NOT_ALLOWED);
        }
    }


    /** 회원 프로필 조회 (닉네임, 프로필 이미지 URL만 반환) */
    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        // Redis 캐시에서 프로필 조회 시도
        ProfileResponse cachedResponse = jwtRedisService.getCachedUserProfile(userId);

        // 캐시 히트 시, 캐시된 DTO를 즉시 반환
        if (cachedResponse != null && cachedResponse.getNickname() != null) {
            System.out.println("DEBUG: Cache Hit for simple profile of userId: " + userId);

            return cachedResponse;
        }

        // 캐시 미스 시 DB에서 사용자 조회
        System.out.println("DEBUG: Cache Miss for user profile of userId: " + userId + ". Fetching from DB.");
        User user = findUserByIdOrThrow(userId); 

        // DB에서 조회된 User 엔티티를 ProfileResponse DTO로 반환
        ProfileResponse profileResponse = ProfileResponse.from(user);

        // Redis에 ProfileResponse DTO 캐싱
        jwtRedisService.cacheUserProfile(userId, profileResponse);

        // 응답용 DTO 반환 (닉네임, 프로필 이미지 URL만 포함)
        return profileResponse;
    }


    /** 회원 프로필 수정 (닉네임, 프로필 이미지 URL만 수정) */
    @Override
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest updateRequest) {
        // 사용자 DB 조회
        User user = findUserByIdOrThrow(userId);

        // 전달받은 정보로 사용자 엔티티 필드 수정 (User 엔티티 내부 메서드 사용)
        user.updateProfile(updateRequest.getNickname(), updateRequest.getProfileImageUrl());

        // @Transactional 어노테이션으로 트랜잭션 자동으로 DB에 반영
        // 변경 사항 DB에 반영
        //User savedUser = userRepository.save(user);

        // Redis 캐시 무효화
        jwtRedisService.deleteUserProfileCache(userId);

        // 응답용 DTO 반환
        return ProfileResponse.from(user);
    }


    /** 회원 이메일 (로그인 ID) 변경 */
    @Override
    public EmailResponse updateEmail(Long userId, EmailUpdateRequest updateRequest, String accessToken) {
        User user = findUserByIdOrThrow(userId);

        // 현재 비밀번호로 본인 인증
        if (!passwordEncoder.matches(updateRequest.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 새 이메일 중복 체크
        validateDuplicateEmail(updateRequest.getNewEmail());

        // 이메일 변경 //User savedUser = userRepository.save(user);
        user.updateEmail(updateRequest.getNewEmail());
        
        // 이메일(로그인 ID) 변경은 중요한 보안 이벤트이므로, 기존 토큰 모두 무효화.
        // 토큰을 넘기지 않으면 블랙리스트가 비어 옛 로그인 ID로 발급된 토큰이 만료(10시간)까지 살아 있다.
        jwtRedisService.invalidateAllUserTokens(userId, accessToken);

        return EmailResponse.from(user);
    }

    
    /** 비밀번호 변경 */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest updateRequest, String accessToken) {
        // 사용자 조회(존재하지 않으면 예외처리)
        User user = findUserByIdOrThrow(userId);
        
        // 현재 비밀번호 검증(불일치 시 예외 처리)
        if(!passwordEncoder.matches(updateRequest.getOldPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 새 비밀번호 암호화 후 저장 // userRepository.save(user)
        String newPassword = passwordEncoder.encode(updateRequest.getNewPassword());
        user.updatePassword(newPassword);

        // 비밀번호 변경은 중요한 보안 이벤트이므로, 해당 사용자의 모든 토큰을 무효화.
        // 토큰을 넘기지 않으면 블랙리스트가 비어 옛 비밀번호로 받은 토큰이 만료(10시간)까지 살아 있다.
        jwtRedisService.invalidateAllUserTokens(userId, accessToken);
    }


    /**
     * 회원 탈퇴(FR-A-08).
     *
     * 행을 지우지 않고 익명화한다. 신고·차단·채팅·매칭 신청이 이 행을 NOT NULL FK로 물고 있어
     * 하드 삭제는 FK 위반으로 실패하고, 성공하더라도 방침이 보존하기로 한 신고 이력까지 사라진다
     * (개인정보처리방침 5절).
     */
    @Override
    public void withdraw(Long userId, String accessToken) {
        // 해당 사용자의 모든 토큰 삭제 (Redis에서 Refresh Token 삭제 및 Access Token 블랙리스트 추가).
        // 토큰을 넘기지 않으면 블랙리스트에 아무것도 안 들어가 방금 탈퇴한 그 토큰이 만료(10시간)까지 살아 있다.
        jwtRedisService.invalidateAllUserTokens(userId, accessToken);

        // 사용자 조회(존재하지 않으면 예외처리)
        User user = findUserByIdOrThrow(userId);

        // 회원 탈퇴 시, 캐싱된 유저 데이터 모두 삭제
        jwtRedisService.deleteAllUserCache(userId);

        // 진행 중인 매칭 정리. 탈퇴자를 그룹에 남기면 없는 사람과의 약속이 성사된 것처럼 보인다.
        clearOngoingMatch(userId);

        // 개인정보만 지우고 행은 남긴다
        user.withdraw(LocalDateTime.now());
    }


    /**
     * 이메일 인증 코드 발송(FR-A-03).
     *
     * 코드는 Redis에 사용자당 한 개만 두고 TTL로 만료시킨다. 재발송은 같은 키를 덮으므로
     * 이전 코드는 그 순간 무효가 된다. 코드와 이메일 주소는 로그에 남기지 않는다(NFR-05).
     *
     * 재발송에는 쿨다운을 건다(FR-S-08). 반복 호출은 SMTP 발신 쿼터를 태우고, 쿼터가 마르면
     * EmailServiceImpl이 실패를 삼키므로 매칭 알림(FR-N-01/02/04)까지 조용히 전부 멈춘다.
     *
     * 트랜잭션 밖에서 돈다(NOT_SUPPORTED). 여기서 DB에 쓰는 것은 없는데 SMTP 왕복이 최대 5초라
     * 클래스 레벨 @Transactional을 그대로 두면 커넥션 하나를 그 시간만큼 붙들고 있게 된다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendEmailVerification(Long userId) {
        long wait = jwtRedisService.getEmailResendCooldownSeconds(userId);
        if (wait > 0) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_COOLDOWN, wait);
        }

        User user = findUserByIdOrThrow(userId);

        String code = String.format("%06d", CODE_RANDOM.nextInt(1_000_000));
        jwtRedisService.saveEmailVerificationCode(userId, code);
        // 새 코드에는 시도 횟수를 새로 준다. 안 지우면 이전 코드의 실패가 남아
        // 새로 받은 코드를 한 번도 못 넣어보고 막힌다.
        jwtRedisService.clearEmailVerifyFailCount(userId);
        // 쿨다운은 발송 전에 건다. 발송 뒤에 걸면 SMTP가 느린 5초 동안 들어온 요청이 전부 통과한다.
        jwtRedisService.startEmailResendCooldown(userId);

        emailService.send(user.getEmail(), VERIFY_SUBJECT, verificationBody(code));
    }


    /**
     * 이메일 인증 코드 확인(FR-A-03).
     *
     * 만료(키 없음)와 불일치를 같은 오류로 내린다 — 구분해서 알려주면 코드를 대입해 보는 쪽에
     * "이 계정에 유효한 코드가 살아 있다"는 정보를 준다.
     */
    @Override
    public void confirmEmailVerification(Long userId, String code) {
        String issued = jwtRedisService.getEmailVerificationCode(userId);
        if (issued == null || code == null || !issued.equals(code.trim())) {
            // 살아 있는 코드에 대한 실패만 센다. 코드가 없는 상태의 시도는 셀 대상이 없다.
            if (issued != null && jwtRedisService.increaseEmailVerifyFailCount(userId) >= MAX_VERIFY_ATTEMPTS) {
                // 임계를 넘으면 코드를 버린다. 남은 TTL 동안 대입을 이어갈 수 없고,
                // 새 코드는 재발송 쿨다운을 거쳐야 나온다. 카운터도 같이 지워 새 코드가 즉시 막히지 않게 한다.
                jwtRedisService.deleteEmailVerificationCode(userId);
                jwtRedisService.clearEmailVerifyFailCount(userId);
                throw new CustomException(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS,
                        jwtRedisService.getEmailResendCooldownSeconds(userId));
            }
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        findUserByIdOrThrow(userId).verifyEmail(LocalDateTime.now());

        // 같은 코드를 다시 쓰지 못하게 성공 직후 지운다.
        jwtRedisService.deleteEmailVerificationCode(userId);
        jwtRedisService.clearEmailVerifyFailCount(userId);
    }


    private static String verificationBody(String code) {
        return """
                yumm 이메일 인증 코드입니다.

                인증 코드: %s

                앱의 인증 창에 코드를 입력해 주세요. %d분 안에 입력해야 합니다.
                요청한 적이 없다면 이 메일은 무시하셔도 됩니다.
                """.formatted(code, JwtRedisService.EMAIL_VERIFY_TTL_MINUTES);
    }


    /** 사용자 상세 정보 조회(개발/유지보수용) */
    @Override
    @Transactional(readOnly = true)
    public UserInfoDetailsResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
            
        return UserInfoDetailsResponse.from(user);
        
    }


// =====================================================
// Helper Methods
// =====================================================

    /**
     * 탈퇴자의 진행 중인 매칭 신청을 정리한다.
     *
     * 그룹이 남아 있으면 그룹 이탈(FR-C-02)을 그대로 태운다 — 최소 인원 미달 시 해체와 대기열 복귀(FR-C-03),
     * 열려 있는 채팅 구독 해제(FR-T-02)가 거기 딸려 있어 탈퇴용 경로를 따로 만들 이유가 없다.
     *
     * 최근 1건만 보면 안 된다. 어제 매칭된 뒤(MATCHED) 오늘 재신청하면(WAITING, 지난 끼니라 BR-01이 풀린다)
     * 최근 행은 WAITING이고 어제 행의 groupId가 남아, 탈퇴 후에도 그 채팅방이 계속 열려 있다.
     * 그래서 groupId가 붙은 행은 전부 leaveAllGroups로 넘긴다.
     */
    private void clearOngoingMatch(Long userId) {
        // 대기 중인 최근 신청은 그룹이 없으니 취소만 하면 된다(cancel은 WAITING이 아니면 예외라 상태를 먼저 본다).
        matchRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .filter(m -> m.getStatus() == MatchStatus.WAITING)
                .ifPresent(m -> matchService.cancel(userId));

        matchService.leaveAllGroups(userId);
    }

    /**
     * 사용자 조회 및 예외 처리
     * 
     * userId가 DB에 존재하는지 확인 후,
     * 존재하지 않으면 CustomException 발생시킴
     */
    private User findUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 사용자 중복 확인 및 예외 처리
     * 
     * 주어진 email이 이미 존재하는지 확인 후,
     * 존재할 시 CustomException 발생시킴
     */
    private void validateDuplicateEmail(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

}