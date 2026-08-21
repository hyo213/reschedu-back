package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * [수강생 관리] 담당 강사 인계(효력일 지정). 미배정 상태에서 처음 지정하는 경우도 같은 요청으로 처리되며,
 * 이 경우 previousTeacher가 없을 뿐 나머지 흐름(AcademyStudent.scheduleTeacherHandover)은 동일하다.
 */
public record StudentTeacherHandoverRequest(
        @NotNull(message = "새 담당 강사는 필수입니다.")
        UUID newTeacherUuid,

        @NotNull(message = "적용 시작일은 필수입니다.")
        LocalDate effectiveFrom
) {}
