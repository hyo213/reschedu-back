package com.academy.reschedu.domain.regularclass.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 특정 주(week)의 정규 수업 회차(RegularClassSession) 하나를 나타낸다. startTime/endTime/maxCapacity는
 * 그 회차 생성 시점의 스냅샷이다. attendingStudents는 결석자를 뺀 참여 명단, absentStudents는 결석자
 * 명단(원장/강사에게만), myAbsentStudents는 학부모 본인 자녀의 결석 여부만 담는다.
 */
public record WeeklyOccurrenceResponse(
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        boolean holidayCancelled,
        int maxCapacity,
        List<RosterStudentResponse> attendingStudents,
        int absentCount,
        List<RosterStudentResponse> absentStudents,
        List<RosterStudentResponse> myAbsentStudents
) {
}
