package com.example.demo.dto.users;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    private String email;
    private String password;
    private String nickname;
    private String gender;

    @NotNull(message = "출생연도는 필수 입력 항목입니다.")
    private Integer birthYear;

    private String profileImageUrl;

    // ponytail: primitive라 필드가 빠진 요청은 false로 떨어져 그대로 거부된다. @NotNull이 따로 필요 없다.
    @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
    private boolean agreedToTerms;
}
