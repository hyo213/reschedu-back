package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 정규 수업 등록/수정 시 요일별로 입력하는 개별 시간대 한 건.
 * 예: {dayOfWeek: MONDAY, startTime: 15:00, endTime: 16:00}
 */
public record TimeSlotRequest(
        @NotNull(message = "요일은 필수 입력 값입니다.")
        DayOfWeek dayOfWeek,

        @NotNull(message = "시작 시간은 필수 입력 값입니다.")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수 입력 값입니다.")
        LocalTime endTime
) {
}
