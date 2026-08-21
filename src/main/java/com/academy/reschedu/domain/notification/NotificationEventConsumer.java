package com.academy.reschedu.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트를 구독해 현재 서버에 연결된 대상자의 SSE 커넥션으로 push한다.
 * SSE 커넥션은 인스턴스 메모리에만 있으므로, 여러 인스턴스로 수평 확장됐을 때도 이벤트가 모든
 * 인스턴스에 브로드캐스트되어야 한다 — 그래서 groupId를 인스턴스마다 유니크하게 둔다(같은 groupId를
 * 공유하면 Kafka가 파티션을 인스턴스끼리 나눠 갖기 때문에, 대상자의 SSE 커넥션이 없는 인스턴스가
 * 그 이벤트를 대신 소비해버려 알림이 유실될 수 있다). auto.offset.reset=latest로 재시작 시점 이전의
 * 과거 이벤트까지 한꺼번에 재생하지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationSseService sseService;

    @KafkaListener(
            topics = NotificationEventProducer.TOPIC,
            groupId = "reschedu-notification-${random.uuid}",
            properties = "auto.offset.reset=latest"
    )
    public void handle(NotificationEvent event) {
        sseService.push(event);
        log.info("알림 push 완료: type={}, member={}, academy={}", event.type(), event.targetMemberId(), event.targetAcademyId());
    }
}
