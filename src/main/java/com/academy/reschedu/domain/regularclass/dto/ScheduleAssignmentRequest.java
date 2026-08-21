package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * [수강생 관리] 화면에서 학생을 특정 정규 수업에 기간을 지정해 배정할 때 사용하는 요청 DTO.
 * startDate/endDate를 모두 비워두면 기존 방식과 동일하게 "항상 유효한" 배정이 된다.
 */
public record ScheduleAssignmentRequest(
        @NotNull(message = "수강생 정보는 필수입니다.")
        UUID studentUuid,

        @NotNull(message = "배정할 정규 수업 정보는 필수입니다.")
        UUID regularClassUuid,

        LocalDate startDate,
        LocalDate endDate
) {
}
