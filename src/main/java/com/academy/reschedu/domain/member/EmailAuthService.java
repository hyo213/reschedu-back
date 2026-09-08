package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.member.event.AuthCodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "email-auth:";
    private static final Duration CODE_TTL = Duration.ofMinutes(3);

    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 1. 인증 코드 발송 로직
     */
    public void sendAuthCode(String email) {
        // 6자리 난수 생성 (예: 123456) — 예측 가능한 java.util.Random 대신 SecureRandom을 쓴다.
        String authCode = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        // Redis에 유효시간 3분짜리 인증 코드 저장 — 만료 처리를 코드로 직접 하지 않고 TTL에 맡긴다.
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + email);
        bucket.set(authCode, CODE_TTL);

        // 🎯 실제 SMTP 메일 발송은 요청-응답 경로에서 빼서 Kafka 이벤트로 비동기 처리한다.
        // 스프링 이벤트로 한 번 감싸 발행하고, 실제 Kafka 발행은 AuthCodeEventProducer가 담당한다.
        eventPublisher.publishEvent(new AuthCodeRequestedEvent(email, authCode));
    }

    /**
     * 2. 인증 코드 검증 로직
     */
    public boolean verifyAuthCode(String email, String code) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + email);
        String storedCode = bucket.get();

        // Redis TTL로 만료를 관리하므로, 요청 자체가 없었던 경우와 만료돼 이미 사라진 경우를
        // 코드에서 따로 구분하지 않는다(둘 다 키가 없는 상태로 동일하게 관측된다).
        if (storedCode == null) {
            throw new IllegalArgumentException("인증 요청 내역이 없거나 만료되었습니다. 다시 요청해 주세요.");
        }

        // 유저가 입력한 코드와 저장된 코드 일치 여부 리턴
        return storedCode.equals(code);
    }
}
