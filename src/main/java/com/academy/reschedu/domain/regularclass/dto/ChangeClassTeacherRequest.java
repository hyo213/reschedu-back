package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 원장/강사 전용: 정규 수업의 담당 강사를 특정 날짜부터 바꾼다.
 * RegularClass.teacher를 직접 덮어쓰지 않는다 — [요일=독립 강의 원칙] 요일·시간이 같아도 강사가 다르면
 * 물리적으로 다른 수업이므로, 대신 effectiveFrom부터 이 반의 현재 로스터 전원을 새 강사의 같은 요일·시간
 * 반으로 이관한다(RegularClassService.changeClassTeacher 참고).
 */
public record ChangeClassTeacherRequest(
        @NotNull(message = "새로 배정할 강사는 필수입니다.")
        UUID newTeacherUuid,

        @NotNull(message = "적용 시작일은 필수입니다.")
        LocalDate effectiveFrom
) {
}
