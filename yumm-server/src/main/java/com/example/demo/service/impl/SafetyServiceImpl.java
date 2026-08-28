package com.example.demo.service.impl;

import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.UserBlock;
import com.example.demo.dto.safety.ReportRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SafetyServiceImpl implements SafetyService {

    private final ReportRepository reportRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void report(Long reporterId, ReportRequest request) {
        rejectIfSelf(reporterId, request.getReportedUserId());

        reportRepository.save(Report.builder()
                .reporter(findUser(reporterId))
                .reported(findUser(request.getReportedUserId()))
                .reason(request.getReason())
                .detail(request.getDetail())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * 차단은 멱등이다. 같은 상대를 여러 번 차단해도 결과가 같아야 클라이언트가 재시도를 겁내지 않는다.
     *
     * <p><b>여기에 @Transactional을 붙이면 안 된다.</b> "서비스가 트랜잭션 경계"라는 컨벤션의 예외다.
     * 트랜잭션이 있으면 아래 saveAndFlush의 제약 위반이 그 트랜잭션을 rollback-only로 표시하고,
     * 예외를 catch로 삼켜 정상 리턴해도 커밋 단계에서 UnexpectedRollbackException이 터진다.
     * 차단은 실제로 걸려 있는데 사용자에겐 500과 영문 내부 메시지가 나간다.
     * 트랜잭션을 두지 않으면 saveAndFlush가 자기 트랜잭션에서 실패하고 롤백 표시가 밖으로 새지 않아
     * catch가 제 역할을 한다. 쓰기가 한 건뿐이라 묶을 것도 없다.
     * (BlockDuplicateTest#blockMustNotBeTransactional이 이 조건을 지킨다)
     */
    @Override
    public void block(Long blockerId, Long blockedUserId) {
        rejectIfSelf(blockerId, blockedUserId);

        if (userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedUserId)) {
            return;
        }

        User blocker = findUser(blockerId);
        User blocked = findUser(blockedUserId);
        try {
            userBlockRepository.saveAndFlush(UserBlock.builder()
                    .blocker(blocker)
                    .blocked(blocked)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 같은 쌍을 동시에 두 번 저장한 경우. 유니크 제약이 잡아주고, 결과는 "차단됨"으로 같다.
            // ponytail: 검사-저장 사이의 좁은 경합만 여기서 흡수한다. 잠금을 걸 이유가 없다.
        }
    }

    /** 자기 자신은 신고할 수도 차단할 수도 없다. */
    private void rejectIfSelf(Long actorId, Long targetId) {
        if (actorId.equals(targetId)) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
