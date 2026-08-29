package com.example.demo.dto.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "현재 비밀번호는 필수 입력 항목입니다.")
    private String oldPassword;

    // 가입과 같은 정책을 건다(FR-A-09). 없으면 빈 문자열도 BCrypt가 그대로 해시해 저장된다.
    @NotBlank(message = "새 비밀번호는 필수 입력 항목입니다.")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
    private String newPassword;
}
