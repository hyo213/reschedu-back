package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * [수강생 관리] 화면의 "요일 변경" 요청 DTO. 예: 8월까지 월수금반이던 학생을 9월 1일부터 월목반으로 옮긴다.
 * fromRegularClassUuid를 지정하면 그 반의 기존 활성 배정을 (effectiveFrom - 1일)로 종료 처리한 뒤,
 * toRegularClassUuid에 effectiveFrom부터 새로 배정한다 — 두 동작이 한 트랜잭션으로 원자적으로 처리된다.
 * fromRegularClassUuid가 없으면(학생이 아직 아무 반에도 없던 경우) 새 배정만 수행한다.
 */
public record ScheduleChangeRequest(
        @NotNull(message = "수강생 정보는 필수입니다.")
        UUID studentUuid,

        UUID fromRegularClassUuid,

        @NotNull(message = "새로 배정할 정규 수업 정보는 필수입니다.")
        UUID toRegularClassUuid,

        @NotNull(message = "적용 시작일은 필수입니다.")
        LocalDate effectiveFrom
) {
}
