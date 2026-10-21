package com.example.demo.dto.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 참석 상호 체크 제출(FR-C-06). 나를 뺀 그룹원 전원에 대한 응답을 한 번에 받는다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckRequest {

    @NotEmpty(message = "체크할 그룹원이 없습니다.")
    private List<@Valid @NotNull Item> checks;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotNull(message = "체크 대상을 선택해 주세요.")
        private Long userId;

        @NotNull(message = "참석 여부를 선택해 주세요.")
        private Boolean attended;
    }
}
