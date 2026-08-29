package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    
    INVALID_GENDER(HttpStatus.NOT_FOUND, "유효한 성별이 아닙니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 사용자입니다."),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 유효하지 않습니다."),
    
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),

    MATCHING_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "매칭 대기시간이 초과되었습니다."),

    ALREADY_WAITING(HttpStatus.CONFLICT, "이미 진행 중인 매칭 신청이 있습니다."),

    MATCH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "매칭 신청 내역이 없습니다."),

    UNDERAGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "만 19세 미만은 서비스를 이용할 수 없습니다."),

    INVALID_MEAL_DATE(HttpStatus.BAD_REQUEST, "식사 날짜는 오늘 또는 내일만 선택할 수 있습니다."),

    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방이 존재하지 않습니다."),

    CHAT_ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "이 채팅방에 참여할 권한이 없습니다."),

    MESSAGE_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "메시지 전송이 실패했습니다."),

    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다."),

    WITHDRAWN_ACCOUNT(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다. 다시 이용하시려면 새로 가입해 주세요."),

    // 이름이 그대로 응답 JSON의 error 값이 된다(GlobalExceptionHandler). 웹이 이 문자열로 인증 창을 띄우므로 바꾸지 않는다.
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "매칭 신청 전에 이메일 인증이 필요합니다."),

    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다.");

    

    private final HttpStatus status;
    private final String message;
}
