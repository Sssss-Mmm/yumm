package com.example.demo.dto.users;

import com.example.demo.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailResponse {
    private final String email;

    public static EmailResponse from(User user) {
        return EmailResponse.builder()
                .email(user.getEmail())
                .build();
    }
} 