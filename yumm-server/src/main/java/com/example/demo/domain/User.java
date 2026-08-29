package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity // JPA 엔티티(=DB 테이블과 매핑) 
@Table(name = "users")
@Getter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 회원 고유 식별 번호 (auto-incremental)

    @Column(nullable = false, unique = true, length = 100) 
    private String email; // 로그인 ID 겸 email 주소

    @Column(nullable = false)   // 비밀번호 길이, 해시 알고리즘 체크 후 수정할 것
    private String password; // 비밀번호 (해시 처리하여 저장)
    
    @Column(nullable = false, length = 50)
    private String nickname; // 사용자 닉네임

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender; // 성별(불변값)

    @Column(nullable = false)
    private int birthYear; // 출생연도(불변값). 성인 판정(FR-A-04/BR-08)의 근거라 나이 대신 연도로 저장한다

    @Column(length = 500)
    private String profileImageUrl; // 프로필 이미지 URL

    @Enumerated(EnumType.STRING) // Enum의 이름을 DB에 문자열로 저장하도록 지시
    @Column(nullable = false, length = 20) // DB 컬럼 설정
    private UserRole role; // 사용자 권한 (ex: ROLE_USER, ROLE_ADMIN)

    // 기존 가입자에게는 받은 기록이 없으므로 nullable로 둔다. NULL = 동의 기록 없음.
    private LocalDateTime termsAgreedAt; // 이용약관 동의 시각(가입 시점)



    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임은 필수 입력 항목입니다.");
        }
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updatePassword(String newHashedPassword) {
        if (newHashedPassword == null || newHashedPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호를 입력해 주세요.");
        }
        this.password = newHashedPassword;
    }

    public void updateEmail(String newEmail) {
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수 입력 항목입니다.");
        }
            this.email = newEmail;
    }
    
}
