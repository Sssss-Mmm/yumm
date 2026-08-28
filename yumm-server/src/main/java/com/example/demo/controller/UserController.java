package com.example.demo.controller;

import com.example.demo.service.UserService;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.common.ApiResponse;
import com.example.demo.dto.users.SignupRequest;
import com.example.demo.dto.users.EmailResponse;
import com.example.demo.dto.users.EmailUpdateRequest;
import com.example.demo.dto.users.PhoneNumberResponse;
import com.example.demo.dto.users.PhoneNumberUpdateRequest;
import com.example.demo.dto.users.ProfileResponse;
import com.example.demo.dto.users.ProfileUpdateRequest;
import com.example.demo.dto.users.UserInfoDetailsResponse;
import com.example.demo.dto.users.ChangePasswordRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user") 
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    
    /**
     * 사용자 회원가입 API.
     * 새로운 사용자 계정을 생성하고 시스템에 등록합니다.
     *
     * @param signupRequest 회원가입에 필요한 사용자 정보를 담은 DTO (이메일, 비밀번호, 닉네임 등).
     * @return 회원가입 성공 메시지를 포함하는 응답.
     */
    @PostMapping("/signup")
    @Operation(summary = "사용자 회원가입", description = "회원가입 요청 정보를 받아 새 사용자를 등록합니다.")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody SignupRequest signupRequest) {
        
        userService.signup(signupRequest);
        
        return ApiResponse.created("회원가입이 성공적으로 완료되었습니다.");
    }
    

    /**
     * 회원 프로필 조회 API.
     * 현재 로그인된 사용자의 프로필 정보(닉네임, 프로필 이미지 URL)를 조회합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @return 조회된 사용자 프로필 정보를 담은 DTO.
     */
    @GetMapping("/profile")
    @Operation(summary = "회원 프로필 조회", description = "현재 로그인된 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
    
        ProfileResponse profileResponse = userService.getProfile(userDetails.getId());
    
        return ApiResponse.ok("프로필 조회 성공", profileResponse);
    }


    /**
     * 회원 프로필 수정 API.
     * 현재 로그인된 사용자의 프로필 정보(닉네임, 프로필 이미지 URL)를 업데이트합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param updateRequest 업데이트할 닉네임과 프로필 이미지 URL 정보를 담은 DTO.
     * @return 업데이트된 사용자 프로필 정보를 담은 DTO.
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                      @RequestBody ProfileUpdateRequest updateRequest) {
        ProfileResponse profileResponse = userService.updateProfile(userDetails.getId(), updateRequest);

        return ApiResponse.ok("프로필 수정 완료", profileResponse);
    }


    /**
     * 회원 이메일(로그인 ID) 변경 API.
     * 현재 로그인된 사용자의 이메일 주소를 변경합니다. 변경 후에는 기존 토큰이 무효화될 수 있습니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param updateRequest 변경할 새로운 이메일 주소와 현재 비밀번호를 담은 DTO.
     * @return 업데이트된 사용자 이메일 정보를 담은 DTO.
     */
    @PutMapping("/email")
    @Operation(summary = "사용자 이메일 변경", description = "현재 비밀번호 인증 후 이메일을 변경합니다.")
    public ResponseEntity<ApiResponse<EmailResponse>> updateEmail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                  @RequestBody EmailUpdateRequest updateRequest) {
        EmailResponse emailResponse = userService.updateEmail(userDetails.getId(), updateRequest);

        return ApiResponse.ok("이메일이 성공적으로 변경되었습니다.", emailResponse);
    }


    /**
     * 회원 전화번호 변경 API.
     * 현재 로그인된 사용자의 전화번호를 변경합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param updateRequest 변경할 새로운 전화번호와 현재 비밀번호(또는 인증 코드)를 담은 DTO.
     * @return 업데이트된 사용자 전화번호 정보를 담은 DTO.
     */
    @PutMapping("/phone-number")
    @Operation(summary = "사용자 전화번호 변경", description = "현재 비밀번호 인증 후 전화번호를 변경합니다.")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> updatePhoneNumber(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                              @RequestBody PhoneNumberUpdateRequest updateRequest) {
        PhoneNumberResponse pNumResponse = userService.updatePhoneNumber(userDetails.getId(), updateRequest);

        return ApiResponse.ok("전화번호가 성공적으로 변경되었습니다.", pNumResponse);
    }


    /**
     * 사용자 상세 정보 조회 API (개발/관리용).
     * 현재 로그인한 사용자의 모든 상세 프로필 정보(전화번호, 이메일 등 민감 정보 포함)를 반환합니다.
     * 이 API는 민감 정보를 포함하므로 주의하여 사용해야 합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @return 조회된 사용자 상세 정보를 담은 DTO.
     */
    @GetMapping("/me/details")
    @Operation(summary = "사용자 상세 정보 조회", description = "현재 로그인한 사용자의 모든 상세 프로필 정보를 반환합니다. 전화번호, 이메일 등 민감 정보 포함.")
    public ResponseEntity<ApiResponse<UserInfoDetailsResponse>> getUserDetails(@AuthenticationPrincipal CustomUserDetails userDetails) {

        UserInfoDetailsResponse userDetailsResponse = userService.getUserDetails(userDetails.getId());

        return ApiResponse.ok("사용자 상세 정보 조회 성공", userDetailsResponse);
    }


    /**
     * 비밀번호 변경 API.
     * 현재 로그인된 사용자의 비밀번호를 새로운 비밀번호로 변경합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param updateRequest 현재 비밀번호와 새로운 비밀번호 정보를 담은 DTO.
     * @return 비밀번호 변경 성공 메시지를 포함하는 응답.
     */
    @PutMapping("/password")
    @Operation(summary = "사용자 비밀번호 변경", description = "현재 비밀번호와 새로운 비밀번호를 입력받아 비밀번호를 변경합니다.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal CustomUserDetails userDetails, 
                                                            @RequestBody ChangePasswordRequest updateRequest) {
        userService.changePassword(userDetails.getId(), updateRequest);

        return ApiResponse.ok("비밀번호가 성공적으로 변경되었습니다.");                           
    }

    
    /**
     * 회원 탈퇴 API.
     * 현재 로그인된 사용자의 계정을 시스템에서 삭제합니다. 이 작업은 되돌릴 수 없습니다.
     * 회원 탈퇴 시, 관련 모든 데이터(예: Refresh Token)가 무효화됩니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @return 회원 탈퇴 성공 메시지를 포함하는 응답.
     */
    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 처리합니다. 사용자의 모든 데이터가 삭제됩니다.")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
    
        userService.withdraw(userDetails.getId());
    
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.");
    }
}