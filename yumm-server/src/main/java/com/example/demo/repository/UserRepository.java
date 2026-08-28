package com.example.demo.repository;
import com.example.demo.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
        
    // email로 회원 검색(로그인 ID)
    Optional<User> findByEmail(String email);

    // phone으로 회원 검색
    Optional<User> findByPhoneNumber(String phoneNumber);

}