package com.example.demo.dto.users;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhoneNumberUpdateRequest {
    private String newPhoneNumber;
    private String currentPassword;
} 