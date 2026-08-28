package com.example.demo;

import com.example.demo.domain.Gender;
import com.example.demo.domain.User;
import com.example.demo.domain.UserBlock;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.impl.SafetyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 중복 차단은 어느 경로로 들어와도 성공으로 끝나야 한다(FR-S-02, 멱등).
 *
 * 경로는 둘이다. 순차 중복은 existsBy가 걸러내고, 동시 중복은 유니크 제약이 걸러낸다.
 * 뒤쪽은 saveAndFlush가 DataIntegrityViolationException을 던지는 것으로 대신한다.
 */
class BlockDuplicateTest {

    private static final Long BLOCKER_ID = 1L;
    private static final Long BLOCKED_ID = 2L;

    private UserBlockRepository userBlockRepository;
    private SafetyServiceImpl service;

    @BeforeEach
    void setUp() {
        userBlockRepository = mock(UserBlockRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new SafetyServiceImpl(mock(ReportRepository.class), userBlockRepository, userRepository);

        when(userRepository.findById(BLOCKER_ID)).thenReturn(Optional.of(
                User.builder().id(BLOCKER_ID).nickname("a").gender(Gender.MALE).build()));
        when(userRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(
                User.builder().id(BLOCKED_ID).nickname("b").gender(Gender.FEMALE).build()));
    }

    @Test
    @DisplayName("이미 차단한 상대면 저장 없이 성공한다")
    void sequentialDuplicateSucceedsWithoutSaving() {
        when(userBlockRepository.existsByBlocker_IdAndBlocked_Id(BLOCKER_ID, BLOCKED_ID)).thenReturn(true);

        assertThatCode(() -> service.block(BLOCKER_ID, BLOCKED_ID)).doesNotThrowAnyException();

        verify(userBlockRepository, never()).saveAndFlush(any(UserBlock.class));
    }

    @Test
    @DisplayName("동시 차단으로 유니크 제약에 걸려도 성공으로 끝난다")
    void concurrentDuplicateSucceeds() {
        // 검사 시점엔 없었는데 저장 시점엔 경쟁 요청이 이미 넣어둔 상태
        when(userBlockRepository.existsByBlocker_IdAndBlocked_Id(BLOCKER_ID, BLOCKED_ID)).thenReturn(false);
        when(userBlockRepository.saveAndFlush(any(UserBlock.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_blocks_pair"));

        assertThatCode(() -> service.block(BLOCKER_ID, BLOCKED_ID)).doesNotThrowAnyException();
    }

    /**
     * block()에 트랜잭션이 붙으면 위 catch가 무효해진다. flush 실패가 세션을 rollback-only로 표시해
     * 커밋 단계에서 UnexpectedRollbackException이 터지고, 사용자에겐 500이 나간다.
     * "서비스가 트랜잭션 경계"라는 컨벤션만 보고 @Transactional을 도로 붙이는 것을 여기서 막는다.
     */
    @Test
    @DisplayName("block()에는 @Transactional이 없어야 한다")
    void blockMustNotBeTransactional() throws Exception {
        assertThat(SafetyServiceImpl.class.getMethod("block", Long.class, Long.class)
                .isAnnotationPresent(Transactional.class))
                .as("block()에 @Transactional을 붙이면 동시 중복 차단이 다시 500이 된다")
                .isFalse();
        assertThat(SafetyServiceImpl.class.isAnnotationPresent(Transactional.class))
                .as("클래스 레벨 @Transactional도 같은 이유로 안 된다")
                .isFalse();
    }
}
