package com.academy.reschedu.domain.makeup.dto;

import com.academy.reschedu.domain.regularclass.dto.RosterStudentResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 보강 신청/매칭 화면의 특정 날짜 여석 한 건. 학부모는 teacherName이 null이고 attendingStudents가
 * 빈 리스트로 채워지며, 원장/강사는 강사명과 실제 매칭/편성 학생 명단까지 채워진다.
 */
public record MakeupSlotResponse(
        UUID regularClassUuid,
        String title,
        String teacherName,
        String roomNumber,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int maxCapacity,
        int currentCount,
        int remainingSeats,
        List<RosterStudentResponse> attendingStudents
) {
}
