package com.academy.reschedu.domain.regularclass.dto;

import com.academy.reschedu.domain.regularclass.RegularClassTimeSlot;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** 요일별 개별 시간대 한 건을 화면에 그대로 보여주기 위한 응답 형태. */
public record TimeSlotResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static TimeSlotResponse from(RegularClassTimeSlot slot) {
        return new TimeSlotResponse(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());
    }
}
