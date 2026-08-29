package com.example.demo.controller;

import com.example.demo.service.UserService;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.common.ApiResponse;
import com.example.demo.dto.users.SignupRequest;
import jakarta.validation.Valid;
import com.example.demo.dto.users.EmailResponse;
import com.example.demo.dto.users.EmailUpdateRequest;
import com.example.demo.dto.users.EmailVerifyRequest;
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
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest signupRequest) {
        
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
     * @param authHeader  이 요청의 Authorization 헤더. 여기 실린 Access Token을 즉시 블랙리스트에 넣는다.
     * @return 업데이트된 사용자 이메일 정보를 담은 DTO.
     */
    @PutMapping("/email")
    @Operation(summary = "사용자 이메일 변경", description = "현재 비밀번호 인증 후 이메일을 변경합니다. 변경 즉시 기존 토큰은 무효가 되어 재로그인이 필요합니다.")
    public ResponseEntity<ApiResponse<EmailResponse>> updateEmail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                  @RequestBody EmailUpdateRequest updateRequest,
                                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        EmailResponse emailResponse = userService.updateEmail(userDetails.getId(), updateRequest, bearerToken(authHeader));

        return ApiResponse.ok("이메일이 성공적으로 변경되었습니다.", emailResponse);
    }


    /**
     * 이메일 인증 코드 발송 API (FR-A-03).
     * 로그인한 사용자의 가입 이메일로 인증 코드를 보냅니다. 재발송하면 이전 코드는 무효가 됩니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @return 발송 완료 메시지를 포함하는 응답.
     */
    @PostMapping("/verify-email")
    @Operation(summary = "이메일 인증 코드 발송", description = "로그인한 사용자의 가입 이메일로 인증 코드를 발송합니다. 재발송 시 이전 코드는 무효가 됩니다. 쿨다운 중이면 429(EMAIL_VERIFICATION_COOLDOWN)와 함께 남은 초를 retryAfterSeconds로 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerification(@AuthenticationPrincipal CustomUserDetails userDetails) {

        userService.sendEmailVerification(userDetails.getId());

        return ApiResponse.ok("인증 코드를 보냈습니다. 메일함을 확인해 주세요.");
    }


    /**
     * 이메일 인증 코드 확인 API (FR-A-03).
     * 발송된 코드를 검증하고 인증을 완료합니다. 인증을 마쳐야 매칭을 신청할 수 있습니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param verifyRequest 사용자가 입력한 인증 코드를 담은 DTO.
     * @return 인증 완료 메시지를 포함하는 응답.
     */
    @PostMapping("/verify-email/confirm")
    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 확인하고 이메일 인증을 완료합니다. 오입력이 5회를 넘으면 해당 코드는 무효가 되고 429(TOO_MANY_VERIFICATION_ATTEMPTS)를 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> confirmEmailVerification(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                      @Valid @RequestBody EmailVerifyRequest verifyRequest) {

        userService.confirmEmailVerification(userDetails.getId(), verifyRequest.getCode());

        return ApiResponse.ok("이메일 인증이 완료되었습니다.");
    }


    /**
     * 사용자 상세 정보 조회 API (개발/관리용).
     * 현재 로그인한 사용자의 모든 상세 프로필 정보(이메일 등 민감 정보 포함)를 반환합니다.
     * 이 API는 민감 정보를 포함하므로 주의하여 사용해야 합니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @return 조회된 사용자 상세 정보를 담은 DTO.
     */
    @GetMapping("/me/details")
    @Operation(summary = "사용자 상세 정보 조회", description = "현재 로그인한 사용자의 모든 상세 프로필 정보를 반환합니다. 이메일 등 민감 정보 포함.")
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
     * @param authHeader  이 요청의 Authorization 헤더. 여기 실린 Access Token을 즉시 블랙리스트에 넣는다.
     * @return 비밀번호 변경 성공 메시지를 포함하는 응답.
     */
    @PutMapping("/password")
    @Operation(summary = "사용자 비밀번호 변경", description = "현재 비밀번호와 새로운 비밀번호를 입력받아 비밀번호를 변경합니다. 변경 즉시 기존 토큰은 무효가 되어 재로그인이 필요합니다.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal CustomUserDetails userDetails, 
                                                            @Valid @RequestBody ChangePasswordRequest updateRequest,
                                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        userService.changePassword(userDetails.getId(), updateRequest, bearerToken(authHeader));

        return ApiResponse.ok("비밀번호가 성공적으로 변경되었습니다.");                           
    }

    
    /**
     * 회원 탈퇴 API.
     * 현재 로그인된 사용자의 계정을 시스템에서 삭제합니다. 이 작업은 되돌릴 수 없습니다.
     * 회원 탈퇴 시, 관련 모든 데이터(예: Refresh Token)가 무효화됩니다.
     *
     * @param userDetails 현재 인증된 사용자의 CustomUserDetails 객체에서 ID를 추출하기 위함.
     * @param authHeader  이 요청의 Authorization 헤더. 여기 실린 Access Token을 즉시 블랙리스트에 넣는다.
     * @return 회원 탈퇴 성공 메시지를 포함하는 응답.
     */
    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 처리합니다. 사용자의 모든 데이터가 삭제됩니다.")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {

        userService.withdraw(userDetails.getId(), bearerToken(authHeader));
    
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.");
    }


    /**
     * Authorization 헤더에서 Access Token만 떼어낸다.
     * 필터를 통과해 여기까지 왔으므로 헤더는 정상이지만, 형식이 어긋나도 요청 자체는 진행한다.
     */
    private static String bearerToken(String authHeader) {
        return (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring("Bearer ".length()) : null;
    }
}