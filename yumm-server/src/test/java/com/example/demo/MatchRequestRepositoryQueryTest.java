package com.example.demo;

import com.example.demo.domain.*;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파생 쿼리는 컨텍스트가 떠야 파싱된다. DemoApplicationTests는 DB_URL이 있을 때만 켜지므로
 * 평소 ./mvnw test에서는 아무도 파싱을 확인하지 않는다. H2 슬라이스로 그것만 확인한다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class MatchRequestRepositoryQueryTest {

    @Autowired private MatchRequestRepository matchRequestRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("findByUser_IdAndGroupIdIsNotNull은 그룹이 남은 행만, 상태와 무관하게 전부 돌려준다")
    void findsAllRowsWithGroupId() {
        User me = userRepository.save(user("me@yumm.local"));
        User other = userRepository.save(user("other@yumm.local"));

        // 어제 매칭된 행(그룹 살아 있음)
        MatchRequest stale = matchRequestRepository.save(row(me, LocalDate.now().minusDays(1), MatchStatus.MATCHED, "g-old"));
        // 오늘 재신청(그룹 없음)
        matchRequestRepository.save(row(me, LocalDate.now(), MatchStatus.WAITING, null));
        // 이미 나간 행(그룹 비워짐)
        matchRequestRepository.save(row(me, LocalDate.now().minusDays(2), MatchStatus.CANCELLED, null));
        // 남의 행
        matchRequestRepository.save(row(other, LocalDate.now().minusDays(1), MatchStatus.MATCHED, "g-old"));

        List<MatchRequest> mine = matchRequestRepository.findByUser_IdAndGroupIdIsNotNull(me.getId());

        assertThat(mine).extracting(MatchRequest::getId).containsExactly(stale.getId());
    }

    private static User user(String email) {
        return User.builder()
                .email(email).password("x").nickname("n")
                .gender(Gender.MALE).birthYear(1995).role(UserRole.ROLE_USER)
                .build();
    }

    private static MatchRequest row(User u, LocalDate date, MatchStatus status, String groupId) {
        return MatchRequest.builder()
                .user(u).region(Region.GANGNAM).mealDate(date).mealTime(MealTime.LUNCH)
                .genderPreference(GenderPreference.ANY).status(status).groupId(groupId)
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }
}
