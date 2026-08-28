package com.example.demo.dto.users;

import com.example.demo.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel; // <--- 이 라인을 추가해야 합니다.
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@Jacksonized
public class ProfileResponse implements java.io.Serializable {

    private final String nickname;
    private final String profileImageUrl;

    public static ProfileResponse from(User user) {
        return ProfileResponse.builder()
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}

