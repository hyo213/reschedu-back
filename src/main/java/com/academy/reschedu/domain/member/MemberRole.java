package com.academy.reschedu.domain.member;

/**
 * [MemberRole] 회원 역할 열거형
 *
 * - ADMIN   : 학원 관리자. 전체 데이터 접근 및 설정 권한
 * - TEACHER : 강사. 담당 반 조회 및 보강 관리 권한
 * - PARENT : 학부모. 수강생의 보강 티켓 조회 및 신청 권한
 */
public enum MemberRole {
    ADMIN,
    TEACHER,
    PARENT
}