package com.academy.reschedu.domain.member.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCodeEventProducer {

    public static final String TOPIC = "auth-code-requested";

    private final KafkaTemplate<String, AuthCodeRequestedEvent> kafkaTemplate;

    /**
     * EmailAuthService.sendAuthCode()가 발행하는 스프링 애플리케이션 이벤트를 받아 Kafka로 발행한다.
     * 인증 코드는 DB가 아니라 Redis에 저장되고(트랜잭션 롤백 대상이 아니라 저장 시점에 이미 확정),
     * 커밋 시점까지 발행을 미룰 이유가 없어 일반 EventListener를 쓴다(AFTER_COMMIT 불필요).
     */
    @EventListener
    public void publish(AuthCodeRequestedEvent event) {
        // 🎯 key를 이메일로 둬서, 같은 사용자가 재요청을 연달아 보내도 같은 파티션에 순서대로 쌓이게 한다
        // (컨슈머가 여러 개로 늘어나도 한 사용자의 인증 코드 발송 순서는 뒤섞이지 않는다).
        kafkaTemplate.send(TOPIC, event.email(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("인증 코드 이벤트 발행 실패: email={}", event.email(), ex);
                    }
                });
    }
}
