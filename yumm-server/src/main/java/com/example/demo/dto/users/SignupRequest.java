package com.example.demo.dto.users;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    private String email;
    private String password;
    private String nickname;
    private String gender;
    private Integer age;
    private String phoneNumber;
    private String profileImageUrl;
} 