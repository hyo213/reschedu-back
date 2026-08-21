package com.academy.reschedu.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    public static final String TOPIC = "app-notification";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /**
     * 알림을 발생시킨 트랜잭션이 커밋된 뒤에만 Kafka로 발행한다(AFTER_COMMIT). 커밋 전에 발행하면
     * 롤백된 보강권 발급 건에 대해서도 알림이 나가버리는 dual-write 문제가 생긴다.
     * key는 대상 단위(회원 1명 또는 학원+역할)로 둬서, 같은 대상에게 보내는 알림은 순서가 뒤섞이지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationEvent event) {
        String key = event.targetMemberId() != null
                ? "member-" + event.targetMemberId()
                : "academy-" + event.targetAcademyId();

        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("알림 이벤트 발행 실패: type={}, key={}", event.type(), key, ex);
                    }
                });
    }
}
