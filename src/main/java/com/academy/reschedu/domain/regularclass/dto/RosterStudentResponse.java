package com.academy.reschedu.domain.regularclass.dto;

import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.regularclass.RegularClassSessionStudent;

import java.time.LocalDate;
import java.util.UUID;

public record RosterStudentResponse(
        UUID uuid,
        String name,
        String managementName,
        // 학생의 수강 기간 (AcademyStudent 기준, [수강생 관리] 화면에서 관리) — 표시 참고용
        LocalDate enrollmentStartDate,
        LocalDate enrollmentEndDate,
        // 원래 정규 편성이 아니라 보강 매칭으로 이 회차에 들어온 수강생인지 여부
        boolean viaMakeup
) {
    public static RosterStudentResponse from(RegularClassSessionStudent sessionStudent) {
        return from(sessionStudent.getAcademyStudent(), sessionStudent.isViaMakeup());
    }

    // 🎯 템플릿 로스터(RegularClassStudent) 등 회차(세션)와 무관한 맥락에서는 보강 여부 개념이 없으므로 false로 고정한다.
    public static RosterStudentResponse from(AcademyStudent academyStudent) {
        return from(academyStudent, false);
    }

    private static RosterStudentResponse from(AcademyStudent academyStudent, boolean viaMakeup) {
        return new RosterStudentResponse(
                academyStudent.getStudent().getUuid(),
                academyStudent.getStudent().getName(),
                academyStudent.getManagementName(),
                academyStudent.getEnrollmentStartDate(),
                academyStudent.getEnrollmentEndDate(),
                viaMakeup
        );
    }
}
