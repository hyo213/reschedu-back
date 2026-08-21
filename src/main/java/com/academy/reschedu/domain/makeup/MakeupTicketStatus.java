package com.academy.reschedu.domain.makeup;

/**
 * [MakeupTicketStatus] 보강 티켓 상태 열거형
 *
 * - UNUSED  : 발급 후 미사용 (보강 신청 가능 상태)
 * - USED    : 보강 수업에 사용 완료
 * - EXPIRED : 유효 기한 초과로 만료 (사용 시점 검증에서 전환)
 */
public enum MakeupTicketStatus {
    UNUSED,
    USED,
    EXPIRED
}