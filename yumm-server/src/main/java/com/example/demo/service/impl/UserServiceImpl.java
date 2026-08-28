package com.example.demo.service.impl;

import com.example.demo.service.UserService;
import com.example.demo.domain.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.dto.users.*;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.service.JwtRedisService;
import com.example.demo.domain.UserRole;
import com.example.demo.domain.Gender;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtRedisService jwtRedisService;
    
    /** 회원 가입 */
    @Override
    @Transactional
    public void signup(SignupRequest signupRequest) {

        // 사용자 중복 검사(이메일 존재 시 예외처리)
        validateDuplicateEmail(signupRequest.getEmail());

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
                .age(signupRequest.getAge())
                .phoneNumber(signupRequest.getPhoneNumber())
                .profileImageUrl(signupRequest.getProfileImageUrl()) 
                .role(defaultRole)
                .build();

        // 회원 정보 DB에 저장
        userRepository.save(newUser);
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
    public EmailResponse updateEmail(Long userId, EmailUpdateRequest updateRequest) {
        User user = findUserByIdOrThrow(userId);

        // 현재 비밀번호로 본인 인증
        if (!passwordEncoder.matches(updateRequest.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 새 이메일 중복 체크
        validateDuplicateEmail(updateRequest.getNewEmail());

        // 이메일 변경 //User savedUser = userRepository.save(user);
        user.updateEmail(updateRequest.getNewEmail());
        
        // 이메일(로그인 ID) 변경은 중요한 보안 이벤트이므로, 기존 토큰 모두 무효화
        // 클라이언트에서 사용 중인 AccessToken도 invalidateAllUserTokens에 전달할 수 있음
        jwtRedisService.invalidateAllUserTokens(userId, null);

        return EmailResponse.from(user);
    }


    /** 회원 전화번호 변경 */
    @Override
    public PhoneNumberResponse updatePhoneNumber(Long userId, PhoneNumberUpdateRequest updateRequest) {
        // 사용자 조회
        User user = findUserByIdOrThrow(userId);

        if (!passwordEncoder.matches(updateRequest.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 전화번호 변경 //User savedUser = userRepository.save(user);
        user.updatePhoneNumber(updateRequest.getNewPhoneNumber());

        return PhoneNumberResponse.from(user);
    }

    
    /** 비밀번호 변경 */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest updateRequest) {
        // 사용자 조회(존재하지 않으면 예외처리)
        User user = findUserByIdOrThrow(userId);
        
        // 현재 비밀번호 검증(불일치 시 예외 처리)
        if(!passwordEncoder.matches(updateRequest.getOldPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 새 비밀번호 암호화 후 저장 // userRepository.save(user)
        String newPassword = passwordEncoder.encode(updateRequest.getNewPassword());
        user.updatePassword(newPassword);

        // 비밀번호 변경은 중요한 보안 이벤트이므로, 해당 사용자의 모든 토큰을 무효화
        jwtRedisService.invalidateAllUserTokens(userId, null); 
    }


    /** 회원 탈퇴 */
    @Override
    public void withdraw(Long userId) {
        // 해당 사용자의 모든 토큰 삭제 (Redis에서 Refresh Token 삭제 및 Access Token 블랙리스트 추가)
        jwtRedisService.invalidateAllUserTokens(userId, null);

        // 사용자 조회(존재하지 않으면 예외처리)
        User user = findUserByIdOrThrow(userId);

        // 회원 탈퇴 시, 캐싱된 유저 데이터 모두 삭제
        jwtRedisService.deleteAllUserCache(userId);

        // 사용자 엔티티 DB에서 삭제
        userRepository.delete(user);
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