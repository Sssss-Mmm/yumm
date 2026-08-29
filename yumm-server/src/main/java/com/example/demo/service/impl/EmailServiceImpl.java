package com.example.demo.service.impl;

import com.example.demo.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SMTP 발송. 접속 정보는 {@code spring.mail.*}(전부 환경변수 참조)에서 온다.
 *
 * ponytail: 평문 본문 한 종류만 보낸다. HTML 템플릿은 첫 소비자가 요구할 때 넣는다.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String from,
                            @Value("${spring.mail.host:}") String host) {
        this.mailSender = mailSender;
        this.from = from;
        // SMTP_HOST가 없는 로컬에서는 발송을 시도조차 하지 않는다. 시도하면 매 알림마다
        // localhost:25 접속 실패 스택트레이스가 쌓인다.
        this.enabled = StringUtils.hasText(host);
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.debug("[메일] SMTP 미설정, 발송 생략 subject={}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // 예외를 밖으로 내보내지 않는다. 알림 실패가 호출자의 트랜잭션을 되돌리면 안 된다.
            // 예외 메시지와 스택트레이스는 남기지 않는다 - SMTP 서버 응답에 계정 정보가 섞여
            // 나올 수 있다(NFR-05). 원인 구분은 예외 타입으로 한다
            // (MailAuthenticationException=자격증명, MailSendException=연결/수신 거부).
            log.warn("[메일] 발송 실패 subject={} cause={}", subject, e.getClass().getSimpleName());
        }
    }
}
