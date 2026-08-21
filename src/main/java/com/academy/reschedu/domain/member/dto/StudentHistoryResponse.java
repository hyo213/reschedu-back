package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.regularclass.dto.ScheduleHistoryResponse;

import java.util.List;

/**
 * [수강 히스토리] 화면 전용 응답. 한 학생의 반 배정(요일/스케줄) 이력과 수강 기간(수강료 납부 기간)
 * 변경 이력을 한 번에 묶어서 내려준다.
 */
public record StudentHistoryResponse(
        List<ScheduleHistoryResponse> scheduleHistory,
        List<EnrollmentPeriodHistoryResponse> enrollmentPeriodHistory
) {
}
