package com.example.demo.dto.users;

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
}
