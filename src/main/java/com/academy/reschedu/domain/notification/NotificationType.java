package com.academy.reschedu.domain.notification;

/** 실시간 알림(SSE 토스트) 종류. */
public enum NotificationType {
    /** 보강권이 새로 발급됨 — 학부모 대상 */
    MAKEUP_TICKET_ISSUED,
    /** 휴무일 지정 취소로 이미 확정된 보강 매칭이 취소됨 — 학부모 대상 */
    MAKEUP_MATCH_CANCELLED,
    /** 강사 가입 승인 대기 — 원장 대상 */
    TEACHER_SIGNUP_PENDING,
    /** 수강생(자녀) 등록 승인 대기 — 원장/강사 대상 */
    STUDENT_ENROLLMENT_PENDING
}
