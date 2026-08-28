package com.example.demo.dto.users;

import com.example.demo.domain.Gender;
import com.example.demo.domain.User;
import com.example.demo.domain.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoDetailsResponse {
    private final String email;
    private final String nickname;
    private final Gender gender;
    private final Integer age;
    private final String phoneNumber;
    private final String profileImageUrl;
    private final UserRole role;

    public static UserInfoDetailsResponse from(User user) {
        return UserInfoDetailsResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .gender(user.getGender())
                .age(user.getAge())
                .phoneNumber(user.getPhoneNumber())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole())
                .build();
    }
} 