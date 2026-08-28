package com.example.demo.repository;
import com.example.demo.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
        
    // email로 회원 검색(로그인 ID)
    Optional<User> findByEmail(String email);

    // phone으로 회원 검색
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * 사용자 행을 쓰기 잠금한 채로 읽는다(1인 1신청, BR-01).
     * 매칭 신청은 "대기 행이 있나 확인 → 저장"이라 확인과 저장 사이에 다른 요청이 끼면
     * 같은 사용자의 WAITING 행이 여러 개 생기고 혼자짜리 3인 그룹이 만들어진다.
     * 신청 진입에서 이 조회로 사용자 행을 잠가 같은 사용자의 신청만 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

}