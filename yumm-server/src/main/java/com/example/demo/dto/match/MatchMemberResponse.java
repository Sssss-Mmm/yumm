package com.example.demo.dto.match;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@Jacksonized
public class MatchMemberResponse implements java.io.Serializable {

    private final Long userId;
    private final String nickname;
    private final String profileImageUrl;

    public static MatchMemberResponse from(MatchRequest request) {
        User user = request.getUser();
        return MatchMemberResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
