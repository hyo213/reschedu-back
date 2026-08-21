package com.academy.reschedu.domain.makeup;

/**
 * [보강권 전체 정책] 원장/강사가 결석 대리 처리 또는 수동 지급을 시도했는데 정책상 발급 제한(최대 보유
 * 개수/월 발급 제한)을 초과하는 경우에만 던져진다. 학부모의 결석 신청은 이 예외 대신 그냥
 * IllegalStateException으로 막는다(확인/취소로 강행할 수 없음) — 원장/강사만 "그래도 발급하시겠습니까?"
 * 확인 후 overrideLimit=true로 재요청해 강행할 수 있다. 컨트롤러에서 별도 HTTP 상태(409)로 구분해
 * 응답해, 프론트가 일반 오류(alert)와 이 경우(confirm)를 구별할 수 있게 한다.
 */
public class MakeupTicketLimitExceededException extends RuntimeException {
    public MakeupTicketLimitExceededException(String message) {
        super(message);
    }
}
