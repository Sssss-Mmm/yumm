package com.example.demo.dto.users;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailUpdateRequest {
    private String newEmail;
    private String currentPassword;
} 