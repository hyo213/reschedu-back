package com.academy.reschedu.domain.notification;

import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 로그인한 회원들의 SSE 연결을 메모리에 들고 있다가, Kafka 컨슈머가 받은 알림을 대상자에게 push한다.
 * 단일 인스턴스 전제(인스턴스가 여러 대면 Redis Pub/Sub 등으로 인스턴스 간 팬아웃이 추가로 필요하다).
 */
@Slf4j
@Component
public class NotificationSseService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    private record Connection(Long memberId, Long academyId, MemberRole role, SseEmitter emitter) {
    }

    public SseEmitter subscribe(Member member) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Long academyId = member.getAcademy() != null ? member.getAcademy().getId() : null;
        Connection connection = new Connection(member.getId(), academyId, member.getRole(), emitter);

        connections.add(connection);
        emitter.onCompletion(() -> connections.remove(connection));
        emitter.onTimeout(() -> connections.remove(connection));
        emitter.onError(e -> connections.remove(connection));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            connections.remove(connection);
        }
        return emitter;
    }

    public void push(NotificationEvent event) {
        for (Connection connection : connections) {
            if (!matches(event, connection)) {
                continue;
            }
            try {
                connection.emitter().send(SseEmitter.event().name("notification").data(event));
            } catch (IOException e) {
                connections.remove(connection);
            }
        }
    }

    private boolean matches(NotificationEvent event, Connection connection) {
        if (event.targetMemberId() != null) {
            return event.targetMemberId().equals(connection.memberId());
        }
        return event.targetAcademyId() != null
                && event.targetAcademyId().equals(connection.academyId())
                && event.targetRoles() != null
                && event.targetRoles().contains(connection.role());
    }

    /** 프록시/브라우저의 유휴 타임아웃으로 연결이 끊기지 않도록 주기적으로 빈 이벤트를 보낸다. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        for (Connection connection : connections) {
            try {
                connection.emitter().send(SseEmitter.event().name("ping").data(""));
            } catch (IOException e) {
                connections.remove(connection);
            }
        }
    }
}
