package com.academy.reschedu.domain.regularclass.dto;

import com.academy.reschedu.domain.regularclass.RegularClassStudent;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * [수강 히스토리] 한 학생이 특정 정규 수업에 배정되어 있던 한 기간(과거/현재/예정 모두 포함)을 나타낸다.
 * 같은 반이라도 기간이 다르면 별도의 행으로 나타난다 — 이 목록 자체가 요일/반 변경 이력이다.
 * 요일마다 시간이 다를 수 있으므로 요일별 시간대를 timeSlots에 개별로 담는다.
 */
public record ScheduleHistoryResponse(
        UUID regularClassUuid,
        String title,
        UUID teacherUuid,
        String teacherName,
        List<TimeSlotResponse> timeSlots,
        String roomNumber,
        LocalDate startDate,
        LocalDate endDate,
        boolean active
) {
    private static final Comparator<TimeSlotResponse> BY_DAY_OF_WEEK =
            Comparator.comparing((TimeSlotResponse t) -> t.dayOfWeek().getValue());

    public static ScheduleHistoryResponse from(RegularClassStudent enrollment) {
        var regularClass = enrollment.getRegularClass();
        List<TimeSlotResponse> timeSlots = regularClass.getTimeSlots().stream()
                .map(TimeSlotResponse::from)
                .sorted(BY_DAY_OF_WEEK)
                .toList();
        return new ScheduleHistoryResponse(
                regularClass.getUuid(),
                regularClass.getTitle(),
                regularClass.getTeacher().getUuid(),
                regularClass.getTeacher().getName(),
                timeSlots,
                regularClass.getRoomNumber(),
                enrollment.getStartDate(),
                enrollment.getEndDate(),
                enrollment.isActiveOn(LocalDate.now())
        );
    }
}
