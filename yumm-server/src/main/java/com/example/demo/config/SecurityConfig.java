package com.example.demo.config;

import com.example.demo.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpMethod; 
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;


@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 인증없이 접근 가능한 URL 패턴 정의
    private static final String[] PUBLIC_URLS = {
        "/api/auth/**",     // 인증 관련 엔드포인트 (로그인, 토큰 재발급 등)
        "/api/user/signup", // 회원가입 엔드포인트

        // WebSocket 핸드셰이크. 실제 인증은 STOMP CONNECT 프레임에서
        // StompAuthChannelInterceptor가 처리한다 (핸드셰이크에는 Authorization 헤더가 없음)
        "/ws/**",

        // Swagger 관련 경로
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/swagger-resources/**",
        "/webjars/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .parentAuthenticationManager(null)
                .build();
    }

    // CORS 설정 Bean 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 개발 서버는 5173에서 뜨고, Vite 프록시가 Origin 헤더를 그대로 넘기므로 서버는
        // 이걸 교차 출처로 본다. localhost / 127.0.0.1 / [::1]은 같은 루프백인데 Origin
        // 문자열은 서로 다르다 — 브라우저가 어느 쪽 주소로 열렸느냐에 따라 갈리므로 셋 다 넣는다.
        // allowCredentials(true)와 함께라면 "*"는 못 쓰고 패턴을 써야 한다.
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); 

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    } 

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS 설정 활성화
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF 비활성화
            .csrf(csrf -> csrf.disable())
            // 세션 관리: JWT이므로 STATELESS로 설정
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 폼 로그인/HTTP Basic 인증 비활성화: 커스텀 인증 필터를 사용
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // OPTIONS 메서드에 대한 모든 요청을 가장 먼저 허용 (CORS 프리플라이트)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
                // 공개 URLS에 정의된 경로들은 인증 없이 접근 허용
                .requestMatchers(PUBLIC_URLS).permitAll()
                // 그 외 모든 요청은 인증된 사용자만 접근 허용
                .anyRequest().authenticated()
            )
            // 예외 처리 설정: 인증되지 않은 접근 시 401 UNAUTHORIZED 반환
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            // 커스텀 JWT 인증 필터 등록: UsernamePasswordAuthenticationFilter 이전에 실행
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}