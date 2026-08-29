package com.example.demo.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 재시도까지 남은 시간(초). 429 계열에서만 채운다.
     * null이면 응답 본문에 실리지 않는다(GlobalExceptionHandler).
     */
    private final Long retryAfterSeconds;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * 대기 시간을 알려줘야 하는 오류용. 화면이 남은 초를 카운트다운할 수 있게 응답에 그대로 실린다.
     *
     * @param errorCode         오류 코드
     * @param retryAfterSeconds 재시도까지 남은 초
     */
    public CustomException(ErrorCode errorCode, Long retryAfterSeconds) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
