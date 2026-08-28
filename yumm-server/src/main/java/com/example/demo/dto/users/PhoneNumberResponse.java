package com.example.demo.dto.users;

import com.example.demo.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhoneNumberResponse {
    private final String phoneNumber;

    public static PhoneNumberResponse from(User user) {
        return PhoneNumberResponse.builder()
                .phoneNumber(user.getPhoneNumber())
                .build();
    }
} 