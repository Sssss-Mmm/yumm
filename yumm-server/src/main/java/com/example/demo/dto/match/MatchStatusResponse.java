package com.example.demo.dto.match;

import com.example.demo.domain.MatchRequest;
import com.example.demo.domain.MatchStatus;
import com.example.demo.domain.MealTime;
import com.example.demo.domain.Region;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@Jacksonized
public class MatchStatusResponse implements java.io.Serializable {

    /** WAITING / MATCHED / CANCELLED / TIMEOUT. TIMEOUT은 저장된 상태가 아니라 만료시각으로 계산된다. */
    private final String status;

    private final String groupId;
    private final Region region;
    private final LocalDate mealDate;
    private final MealTime mealTime;
    private final LocalDateTime expiresAt;

    /** 매칭 완료 시 나를 포함한 그룹 전원. 대기 중이면 빈 목록. */
    private final List<MatchMemberResponse> members;

    public static MatchStatusResponse of(MatchRequest request, List<MatchRequest> groupMembers, LocalDateTime now) {
        return MatchStatusResponse.builder()
                .status(resolveStatus(request, now))
                .groupId(request.getGroupId())
                .region(request.getRegion())
                .mealDate(request.getMealDate())
                .mealTime(request.getMealTime())
                .expiresAt(request.getExpiresAt())
                .members(groupMembers.stream().map(MatchMemberResponse::from).toList())
                .build();
    }

    private static String resolveStatus(MatchRequest request, LocalDateTime now) {
        if (request.isExpired(now)) {
            return "TIMEOUT";
        }
        return request.getStatus().name();
    }

    /** 아직 그룹이 없는 상태(대기/취소/타임아웃)의 응답 */
    public static MatchStatusResponse waiting(MatchRequest request, LocalDateTime now) {
        return of(request, List.of(), now);
    }

    public boolean isMatched() {
        return MatchStatus.MATCHED.name().equals(status);
    }
}
