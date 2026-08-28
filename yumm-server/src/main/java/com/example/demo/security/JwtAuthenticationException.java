// src/main/java/com/example/demo/security/JwtAuthenticationException.java

package com.example.demo.security;

import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationException extends AuthenticationException {
    public JwtAuthenticationException(String msg, Throwable cause) {
        super(msg, cause);
    }
    public JwtAuthenticationException(String msg) {
        super(msg);
    }
}
