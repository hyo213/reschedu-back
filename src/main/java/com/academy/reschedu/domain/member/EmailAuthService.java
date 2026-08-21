package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.member.event.AuthCodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final EmailAuthRepository emailAuthRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 1. 인증 코드 발송 로직
     */
    @Transactional
    public void sendAuthCode(String email) {
        // 6자리 난수 생성 (예: 123456)
        String authCode = String.format("%06d", new Random().nextInt(1000000));

        // DB에 유효시간 3분짜리 인증 객체 저장 (Redis 대용)
        EmailAuth emailAuth = new EmailAuth(email, authCode, 3);
        emailAuthRepository.save(emailAuth);

        // 🎯 실제 SMTP 메일 발송은 요청-응답 경로에서 빼서 Kafka 이벤트로 비동기 처리한다.
        // 이 시점엔 트랜잭션이 아직 커밋 전이므로 스프링 이벤트만 발행하고, 실제 Kafka 발행은
        // AuthCodeEventProducer가 AFTER_COMMIT 시점에 한다 (커밋 실패 시 메일이 나가지 않도록).
        eventPublisher.publishEvent(new AuthCodeRequestedEvent(email, authCode));
    }

    /**
     * 2. 인증 코드 검증 로직
     */
    @Transactional(readOnly = true)
    public boolean verifyAuthCode(String email, String code) {
        // DB에서 해당 이메일의 가장 최근 인증 코드 가져오기
        EmailAuth emailAuth = emailAuthRepository.findFirstByEmailOrderByExpiredAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 존재하지 않습니다."));

        // 시간 만료 여부 체크
        if (emailAuth.isExpired()) {
            throw new IllegalStateException("인증 시간이 만료되었습니다. 다시 요청해 주세요.");
        }

        // 유저가 입력한 코드와 DB 일치 여부 리턴
        return emailAuth.getAuthCode().equals(code);
    }
}