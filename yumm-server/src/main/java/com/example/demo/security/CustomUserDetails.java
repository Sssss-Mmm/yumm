package com.example.demo.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email; 
    private final Collection<? extends GrantedAuthority> authorities;
    
    public CustomUserDetails(Long id, String email, List<String> roles) {
        this.id = id;
        this.email = email;
        this.authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // role을 기반으로 권한 반환
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // JWT 인증에서는 비밀번호를 사용하지 않으므로 null 반환
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    public Long getId() {
        return id;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
    
}
