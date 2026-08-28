package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 전용 스케줄러. 지우면 매칭 편성이 STOMP 브로커 스레드풀로 되돌아간다.
 *
 * <p>WebSocketConfig의 {@code @EnableWebSocketMessageBroker}가 {@code messageBrokerTaskScheduler}
 * (TaskScheduler 타입, 스레드명 "MessageBroker-")를 등록한다. 이 빈 때문에 부트의
 * TaskSchedulingAutoConfiguration이 {@code @ConditionalOnMissingBean(TaskScheduler.class)}로 물러나고,
 * 그러면 컨텍스트에 TaskScheduler가 하나뿐이라 TaskSchedulerRouter가 그걸 집어 쓴다.
 * 결과적으로 30초짜리 매칭 편성 트랜잭션(MatchScheduler)이 STOMP 하트비트와 같은 풀에서 돌게 된다.
 * 하트비트가 편성에 밀리면 멀쩡한 웹소켓 연결이 끊긴다.
 *
 * <p>빈 이름이 {@code taskScheduler}여야 한다. TaskScheduler가 둘이 되면 타입 조회가
 * NoUniqueBeanDefinitionException으로 실패하고, TaskSchedulerRouter는 그때
 * {@code getBean("taskScheduler")}로 되짚는다. 이름을 바꾸면 그 폴백이 깨진다.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // ponytail: @Scheduled은 MatchScheduler 하나뿐이고 fixedDelay라 직렬로 돈다. 스레드 1개면 된다.
        // @Scheduled이 늘어 서로 밀리기 시작하면 그때 올린다.
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
}
