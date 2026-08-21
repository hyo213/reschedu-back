package com.academy.reschedu.domain.makeup;

/**
 * [MakeupTicketSource] 보강권 발급 사유
 *
 * - STUDENT_ABSENCE : 학부모가 특정 정규 수업 회차에 대해 결석 신청하여 발급
 * - ACADEMY_HOLIDAY  : 학원 휴무일 지정으로 인해 해당 날짜 수업이 취소되어 자동 발급
 * - MANUAL_GRANT     : 원장/강사가 보강 매칭 센터에서 특정 학생에게 직접 수동으로 지급
 */
public enum MakeupTicketSource {
    STUDENT_ABSENCE,
    ACADEMY_HOLIDAY,
    MANUAL_GRANT
}
