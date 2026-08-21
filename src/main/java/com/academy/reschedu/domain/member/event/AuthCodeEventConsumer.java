package com.academy.reschedu.domain.member.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 인증 코드 발송 이벤트를 구독해 실제 SMTP 메일 전송을 처리한다.
 * 이 부분이 요청-응답 경로에서 빠지므로, 메일 서버가 느리거나 잠깐 장애가 나도 회원가입 API 응답 속도에는
 * 영향을 주지 않는다. (운영 환경이라면 여기서 예외가 반복될 때 대비한 재시도/DLT 설정을 추가로 둔다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCodeEventConsumer {

    private final JavaMailSender mailSender;

    @KafkaListener(topics = AuthCodeEventProducer.TOPIC, groupId = "reschedu")
    public void handle(AuthCodeRequestedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(event.email());
        message.setSubject("[ReschEdu] 회원가입 이메일 인증 코드입니다.");
        message.setText("안녕하세요. 학원 스케줄링 시스템 ReschEdu입니다.\n\n" +
                "인증 코드 6자리를 회원가입 창에 입력해 주세요.\n" +
                "▶ 인증 코드: " + event.authCode() + "\n\n" +
                "본 코드는 3분간 유효합니다.");

        mailSender.send(message);
        log.info("인증 코드 메일 발송 완료: email={}", event.email());
    }
}
