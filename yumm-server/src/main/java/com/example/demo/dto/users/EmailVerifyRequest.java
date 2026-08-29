package com.example.demo.dto.users;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 인증 코드 확인 요청(FR-A-03). 코드는 6자리 숫자다. */
@Getter
@NoArgsConstructor
public class EmailVerifyRequest {

    @NotBlank(message = "인증 코드를 입력해 주세요.")
    private String code;
}
