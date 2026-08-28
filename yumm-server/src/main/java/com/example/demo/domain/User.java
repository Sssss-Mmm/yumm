package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private int age; // 나이(불변값)

    @Column(nullable = false, length = 20)
    private String phoneNumber; // 핸드폰 번호

    @Column(length = 500)
    private String profileImageUrl; // 프로필 이미지 URL

    @Enumerated(EnumType.STRING) // Enum의 이름을 DB에 문자열로 저장하도록 지시
    @Column(nullable = false, length = 20) // DB 컬럼 설정
    private UserRole role; // 사용자 권한 (ex: ROLE_USER, ROLE_ADMIN)



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
    
    public void updatePhoneNumber(String newPhoneNumber) {
        if (newPhoneNumber == null || newPhoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("핸드폰 번호는 필수 입력 항목입니다.");
        }
        this.phoneNumber = newPhoneNumber;
    }

}
