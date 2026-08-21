package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.AcademyStudent;

/** 자녀 한 명이 등록된 학원 한 곳의 상태 — [내 계정 정보] 자녀 수정 화면에서 학원별 승인 여부를 보여줄 때 쓴다. */
public record ChildAcademyRegistration(
        Long academyId,
        String academyName,
        boolean isApproved
) {
    public static ChildAcademyRegistration from(AcademyStudent registration) {
        return new ChildAcademyRegistration(
                registration.getAcademy().getId(),
                registration.getAcademy().getName(),
                registration.isApproved()
        );
    }
}
