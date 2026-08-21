package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.Student;

import java.util.UUID;

/**
 * 학부모 본인의 자녀 목록 조회용 (결석 신청 시 자녀 선택 등에 사용).
 */
public record MyChildResponse(
        UUID uuid,
        String name
) {
    public static MyChildResponse from(Student student) {
        return new MyChildResponse(student.getUuid(), student.getName());
    }
}
