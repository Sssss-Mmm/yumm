package com.example.demo;

import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 가입 시 출생연도 검증과 성인 판정(FR-A-04 / BR-08)은 Spring 컨텍스트 없이 순수하게 검증한다. */
class SignupBirthYearTest {

    private static final int CURRENT_YEAR = 2026;

    private static ErrorCode errorCodeOf(int birthYear) {
        try {
            UserServiceImpl.validateAdultBirthYear(birthYear, CURRENT_YEAR);
            return null;
        } catch (CustomException e) {
            return e.getErrorCode();
        }
    }

    @Test
    @DisplayName("올해 - 출생연도가 20 이상이면 가입할 수 있다")
    void adultPasses() {
        assertThatCode(() -> UserServiceImpl.validateAdultBirthYear(2006, CURRENT_YEAR)).doesNotThrowAnyException();
        assertThatCode(() -> UserServiceImpl.validateAdultBirthYear(1990, CURRENT_YEAR)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경계값: 2006년생은 통과하고 2007년생은 생일이 안 지났다고 보아 막힌다")
    void boundary() {
        assertThat(errorCodeOf(2006)).isNull();
        assertThat(errorCodeOf(2007)).isEqualTo(ErrorCode.UNDERAGE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("명백한 미성년자는 막힌다")
    void minorBlocked() {
        assertThat(errorCodeOf(2015)).isEqualTo(ErrorCode.UNDERAGE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("미래 연도와 터무니없는 과거 연도, null은 입력값 오류로 막힌다")
    void outOfRange() {
        assertThat(errorCodeOf(CURRENT_YEAR + 1)).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(errorCodeOf(1899)).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> UserServiceImpl.validateAdultBirthYear(null, CURRENT_YEAR))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
