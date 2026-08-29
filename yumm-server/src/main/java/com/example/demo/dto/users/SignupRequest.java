package com.example.demo.dto.users;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    // 형식까지 본다. 받을 수 없는 주소로 가입하면 인증 코드가 갈 곳이 없어 영구히 매칭 불가인데
    // 유니크 제약 때문에 그 주소를 점유한다(FR-A-01/FR-A-03). 길이는 users.email 컬럼(100)에 맞춘다.
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    // FR-A-09: 8자 이상, 영문·숫자 조합. 비밀번호 변경(ChangePasswordRequest)과 같은 정책을 공유한다.
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
    private String password;

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    // 값이 enum 밖이면 서비스가 INVALID_GENDER(400)로 거른다. 여기서는 누락만 막는다.
    @NotBlank(message = "성별은 필수 입력 항목입니다.")
    private String gender;

    @NotNull(message = "출생연도는 필수 입력 항목입니다.")
    private Integer birthYear;

    private String profileImageUrl;

    // ponytail: primitive라 필드가 빠진 요청은 false로 떨어져 그대로 거부된다. @NotNull이 따로 필요 없다.
    @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
    private boolean agreedToTerms;
}
