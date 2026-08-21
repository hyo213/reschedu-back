package com.academy.reschedu.domain.member.event;

/**
 * 회원가입 이메일 인증 코드 발송을 비동기로 처리하기 위한 Kafka 이벤트 페이로드.
 * 발행 시점에는 DB 저장(EmailAuth)까지만 동기로 끝내고, 실제 메일 전송(SMTP 왕복 지연/장애 가능성이 있는
 * 부가 작업)은 이 이벤트를 구독하는 컨슈머가 별도로 처리한다.
 */
public record AuthCodeRequestedEvent(String email, String authCode) {
}
