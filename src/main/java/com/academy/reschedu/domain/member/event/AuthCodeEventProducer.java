package com.academy.reschedu.domain.member.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCodeEventProducer {

    public static final String TOPIC = "auth-code-requested";

    private final KafkaTemplate<String, AuthCodeRequestedEvent> kafkaTemplate;

    /**
     * EmailAuthService.sendAuthCode()가 발행하는 스프링 애플리케이션 이벤트를 받아 Kafka로 발행한다.
     * AFTER_COMMIT에서만 발행해야 하는 이유: 커밋 전에 발행하면 트랜잭션이 롤백되더라도 이미 Kafka로
     * 나가버린 이벤트는 되돌릴 수 없어, 존재하지도 않는 인증 코드로 메일이 발송되는 dual-write 문제가 생긴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
