package com.example.demo.service;

import com.example.demo.dto.users.SignupRequest;
import com.example.demo.dto.users.EmailResponse;
import com.example.demo.dto.users.EmailUpdateRequest;
import com.example.demo.dto.users.PhoneNumberResponse;
import com.example.demo.dto.users.PhoneNumberUpdateRequest;
import com.example.demo.dto.users.ProfileResponse;
import com.example.demo.dto.users.ProfileUpdateRequest;
import com.example.demo.dto.users.UserInfoDetailsResponse;
import com.example.demo.dto.users.ChangePasswordRequest;

/**
 * 회원 정보 관련 인터페이스
 * 
 * 회원가입, 내 정보 조회, 내 정보 수정, 비밀번호 변경, 회원탈퇴 기능 정의
 */
public interface UserService {

    /**
     * 회원 가입을 처리합니다.
     *
     * @param signupRequest 회원가입에 필요한 정보를 담은 DTO
     */
    void signup(SignupRequest signupRequest);


    /**
     * 특정 사용자의 프로필 정보(닉네임, 프로필 이미지 URL)를 조회합니다.
     *
     * @param userId 조회할 사용자의 고유 ID
     * @return 사용자의 프로필 정보를 담은 DTO
     */
    ProfileResponse getProfile(Long userId);


    /**
     * 사용자의 프로필 정보(닉네임, 프로필 이미지 URL)를 수정합니다.
     *
     * @param userId      수정할 사용자의 고유 ID
     * @param updateDto   수정할 프로필 정보를 담은 DTO
     * @return 수정된 사용자의 프로필 정보를 담은 DTO
     */
    ProfileResponse updateProfile(Long userId, ProfileUpdateRequest updateRequest);


    /**
     * 사용자의 이메일(로그인 ID)을 변경합니다.
     *
     * @param userId      사용자의 고유 ID
     * @param updateDto   새 이메일과 현재 비밀번호를 담은 DTO
     * @return 변경된 프로필 정보를 포함한 DTO
     */
    EmailResponse updateEmail(Long userId, EmailUpdateRequest updateRequest);


    /**
     * 사용자의 전화번호를 변경합니다.
     *
     * @param userId      사용자의 고유 ID
     * @param updateDto   새 전화번호와 본인 인증 정보를 담은 DTO
     * @return 변경된 프로필 정보를 포함한 DTO
     */
    PhoneNumberResponse updatePhoneNumber(Long userId, PhoneNumberUpdateRequest updateRequest);


    /**
     * 사용자의 비밀번호를 변경합니다.
     *
     * @param userId                   사용자의 고유 ID
     * @param changePasswordRequest    기존 비밀번호와 새 비밀번호를 담은 DTO
     */
    void changePassword(Long userId, ChangePasswordRequest updateRequest);

    
    /**
     * 회원 탈퇴를 처리합니다.
     *
     * @param userId 탈퇴할 사용자의 고유 ID
     */
    void withdraw(Long userId);


    /**
     * 유저의 모든 상세 정보 조회
     * @param userId
     * @return
     */
    UserInfoDetailsResponse getUserDetails(Long userId);
}