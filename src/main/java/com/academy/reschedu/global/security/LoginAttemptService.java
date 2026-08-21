package com.academy.reschedu.global.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 브루트포스 방어용 실패 횟수 카운터.
 * 인스턴스 메모리에만 저장되므로 서버 재시작/다중 인스턴스 환경에서는 유지되지 않는다
 * (다중 인스턴스 배포 시 Redis 등 공유 저장소로 교체 필요).
 */
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Attempt> attemptsByLoginId = new ConcurrentHashMap<>();

    private record Attempt(int failureCount, Instant lockedUntil) {}

    public void checkNotLocked(String loginId) {
        Attempt attempt = attemptsByLoginId.get(loginId);
        if (attempt != null && attempt.lockedUntil() != null && Instant.now().isBefore(attempt.lockedUntil())) {
            throw new IllegalStateException("로그인 시도 횟수를 초과했습니다. 5분 후 다시 시도해주세요.");
        }
    }

    public void recordFailure(String loginId) {
        attemptsByLoginId.compute(loginId, (key, prev) -> {
            int failureCount = (prev == null ? 0 : prev.failureCount()) + 1;
            Instant lockedUntil = failureCount >= MAX_ATTEMPTS ? Instant.now().plus(LOCKOUT_DURATION) : null;
            return new Attempt(failureCount, lockedUntil);
        });
    }

    public void recordSuccess(String loginId) {
        attemptsByLoginId.remove(loginId);
    }
}
