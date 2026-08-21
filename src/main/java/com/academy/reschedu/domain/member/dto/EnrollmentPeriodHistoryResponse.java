package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.EnrollmentPeriodHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [수강 히스토리] 수강 기간(수강료 납부 기간)이 언제, 누가, 어떻게 바꿨는지를 나타내는 변경 이력 한 건.
 */
public record EnrollmentPeriodHistoryResponse(
        LocalDateTime changedAt,
        String changedByName,
        LocalDate previousStartDate,
        LocalDate previousEndDate,
        LocalDate newStartDate,
        LocalDate newEndDate
) {
    public static EnrollmentPeriodHistoryResponse from(EnrollmentPeriodHistory history) {
        return new EnrollmentPeriodHistoryResponse(
                history.getCreatedAt(),
                history.getChangedBy() != null ? history.getChangedBy().getName() : null,
                history.getPreviousStartDate(),
                history.getPreviousEndDate(),
                history.getNewStartDate(),
                history.getNewEndDate()
        );
    }
}
