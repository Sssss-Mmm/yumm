package com.example.demo.service;

/**
 * 알림 메일 발송(FR-N-01 외).
 *
 * <p>구현은 실패를 삼키고 로그만 남긴다 — 알림은 부가 기능이라 발송 실패가 호출한 쪽의
 * 트랜잭션을 깨서는 안 된다. 발송 성공을 확인해야 하는 기능(예: 이메일 인증)이 생기면
 * 그때 결과를 돌려주는 메서드를 따로 연다.
 */
public interface EmailService {

    /** 평문 메일 한 통. 실패해도 예외를 던지지 않는다. */
    void send(String to, String subject, String body);
}
