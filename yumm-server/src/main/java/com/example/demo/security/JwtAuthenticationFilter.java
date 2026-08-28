package com.example.demo.security;

import com.example.demo.service.JwtRedisService;
import com.example.demo.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import org.springframework.lang.NonNull;
import java.util.List;
import java.util.Arrays;

/**
 * @extends OncePerRequestFilter : Spring Security 필터 중 하나
 * 
 * 모든 HTTP 요청이 들어올 때 가장 처음 실행되는 보안 필터
 * JwtAuthenticationFilter는 Spring Security 필터 체인에 등록되기 때문에 모든 요청마다 무조건 실행됨
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JwtUtils : 토큰 파싱, 유효성 검사를 담당하는 유틸 클래스(토큰 관련 예외 처리도 여기에서 담당)
     * jwtRedisService
     */ 
    private final JwtUtils jwtUtil;
    private final JwtRedisService jwtRedisService;

    // JWT 인증 필터 건너뛸 엔드포인트 작성(AccessToken을 들고 있는 경우만 필터링)
    private static final List<String> EXCLUDE_URLS = Arrays.asList(
            "/api/auth/login", // 로그인 (토큰 발급 전)
            "/api/user/signup",     // 회원가입 (토큰 발급 전)
            "/api/auth/refresh",    // Refresh Token으로 Access Token 재발급 (Access Token 아님)
            "/api/auth/logout",     // Refresh Token으로 로그아웃 (Access Token 아님)
            "/v3/api-docs",         // Swagger API
            "/v3/api-docs/**",      // Swagger API
            "/swagger-ui.html",     // Swagger API
            "/swagger-ui/**"        // Swagger API
    );

    
    public JwtAuthenticationFilter(JwtUtils jwtUtil, JwtRedisService jwtRedisService) {
        this.jwtUtil = jwtUtil;
        this.jwtRedisService = jwtRedisService;

    }

    /**
     * 모든 HTTP 요청마다 실행되는 인증 처리 메서드
     * filterChain.doFilter() 를 호출해서 다음 필터로 넘기지 않을 시 멈춤
     * 
     * == 이 필터의 주 역할 ==
     *  1. 요청 헤더 분석
     *  2. 토큰 파싱 및 유효성 검증
     *  3. 사용자 인증 정보 생성
     *  4. SecurityContext에 인증 정보 등록
     * 
     * @param HttpServletRequest : 클라이언트가 보낸 HTTP 요청 데이터 객체(URL, 헤더, 파라미터, Body 등)
     * @param HttpServletResponse : 서버가 클라이언트에게 돌려줄 응답을 제어하는 객체(상태코드, 응답 헤더, 바디 등)
     * @param FilterChain : 현재 필터 이후의 다음 필터 또는 Controller로 요청 전달하는 체인
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
                                    throws ServletException, IOException {
                            
        System.out.println("REQUEST URL CHECK 1 : " + request.getRequestURI());

        // 특정 경로에 대해 JWT 인증 필터 건너뛰기
        String requestURI = request.getRequestURI();
        if (EXCLUDE_URLS.stream().anyMatch(requestURI::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("REQUEST URL CHECK 2 : " + request.getRequestURI());
        
        // JWT 인증 필터 적용 여부 판단
        // 
        // Authorization 헤더에서 JWT 추출
        // ## 클라이언트는 HTTP 요청 헤더에 ** Authorization: Bearer <token> ** 형태로 전달해야함
        // Authorization 헤더가 없거나 "Bearer "로 시작하지 않으면,
        // 인증 불필요한 요청으로 간주하고 필터 그냥 통과시킴
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        
        // 요청에 JWT가 존재하므로, 본 필터에서 인증 처리 시작
        System.out.println("[JWT Filter] 필터 실행됨: " + request.getRequestURI());
   
        
        // 토큰 유효성 검사 로직
        try {
            // authHeader에서 'Bearer' 접두어를 제거하고 토큰 본문만 반환 
            String token = JwtUtils.extractTokenFrom(authHeader);

            // 토큰 유효성 검사
            jwtUtil.validation(token);

            // --- AccessToken 블랙리스트 확인 로직 (Redis 도입 시 추가) ---
            // 이 곳에 RedisService를 주입받아 isAccessTokenBlacklisted(token)을 호출하여
            // 토큰이 블랙리스트에 있다면 throw new JwtException("Blacklisted token"); 처리.
            // 예시:
            if (jwtRedisService.isAccessTokenBlacklisted(token)) {
                throw new JwtException("Blacklisted token");
            }
             

            /**
             * SecurityContext에 인증 객체 등록 (위의 토큰 유효성 검사 통과 시)
             * 
             * 인증 정보를 담은 UsernamePasswordAuthenticationToken 객체를
             * Spring Security의 SecurityContext에 등록
             * 
             * Principal 에 CustomUserDetails 객체가 들어가고
             * 컨트롤러 등에서 @AuthenticationPrincipal을 통해 인증 유저 객체에 접근 가능
             */
                
            //토큰에서 사용자 정보 추출
            Long userId = jwtUtil.getUserIdFromToken(token);
            String email = jwtUtil.getEmailFromToken(token);
            List<String> roles = jwtUtil.getRolesFromToken(token);

            // CustomUserDetails 생성
            CustomUserDetails userDetails = new CustomUserDetails(userId, email, roles);

            // 인증 객체 생성(Principal = userDetails, Credential = null, Authorities = 권한 목록)
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // 요청 정보 설정(ip, 세션 등)
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 인증 객체를 SecurityContext에 등록 (등록된 사용자는 인증된 사용자로 인식)
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException ex) {

            /**
             * 인증 실패 처리 (토큰이 만료되었거나 위조된 경우)
             *  - 처리 1. 현재 Thread에 바인딩된 인증 객체(Authentication) 제거
             *  - 처리 2. 클라이언트에서 accessToken을 재발급 받을 수 있도록 401 Unauthorized 응답
             */

            /**
             * SecurityContextHolder.clearContext() 상세 설명
             * 
             * 현재 Thread에 바인딩된 인증 객체(Authentication) 제거하여 인증되지 않은 사용자로 간주되도록 설정
             * 즉, 인증 실패 상태로 만들어, 이후 인가 필터에서 접근 차단이 이루어질 수 있게 합니다.
             */
            SecurityContextHolder.clearContext();
            
            /**
             * JwtAuthenticationException 상세 설명
             * 
             * JwtException 발생 시, Spring Security가 인식할 수 있도록 JwtAuthenticationException으로 래핑
             *  - 그냥 JwtException 으로 던지면 Spring Security 내부의 ExceptionTranslationFilter는
             *    이걸 인증 실패로 인식하지 못하기 때문에 (500 Internal Server Error여서 인증 실패로 인식 못함)
             *    "인증 실패" 문제가 발생했다는 것을 명시
             * 
             *  - 401 Unauthorized 응답은 클라이언트가
             *    accessToken 만료 시 refreshToken을 사용하여 자동 재인증할 수 있도록 유도
             * 
             *  - 결과적으로 클라이언트에게 401 Unauthorized 응답 전달
             */ 
            //throw new JwtAuthenticationException("Invalid or expired JWT token", ex);

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // <--- 여기서 401 상태 코드 설정
            response.setContentType("application/json"); // 응답 Content-Type 설정
            response.setCharacterEncoding("UTF-8"); // 인코딩 설정

            String errorMessage;
            if (ex.getCause() instanceof io.jsonwebtoken.ExpiredJwtException) {
                errorMessage = "{\"message\": \"JWT 토큰이 만료되었습니다.\"}";
            } else if ("Blacklisted token".equals(ex.getMessage())) {
                errorMessage = "{\"message\": \"블랙리스트에 등록된 토큰입니다.\"}";
            } else {
                errorMessage = "{\"message\": \"유효하지 않은 JWT 토큰입니다.\"}";
            }

            response.getWriter().write(errorMessage); // 응답 바디에 오류 메시지 작성
            return; // 필터 체인 진행을 여기서 중단
        } catch (Exception e) { // JwtException 외의 다른 예상치 못한 예외 처리
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\": \"서버 오류가 발생했습니다.\"}");
            return;
        }
    
        filterChain.doFilter(request, response); // 다음 필터 or 컨트롤러로 넘겨줌
    }
}

