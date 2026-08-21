package com.academy.reschedu.domain.makeup.dto;

import com.academy.reschedu.domain.makeup.MakeupRequest;
import com.academy.reschedu.domain.makeup.MakeupRequestStatus;
import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.regularclass.RegularClass;
import com.academy.reschedu.domain.regularclass.RegularClassTimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 🚨 이 신청에 사용된 티켓이 MANUAL_GRANT(수동 지급)로 발급된 경우, 그 티켓은 특정 수업/날짜에
 * 묶여 있지 않으므로 originClassUuid/originClassTitle/absentDate가 모두 null일 수 있다.
 */
public record MakeupRequestResponse(
        UUID uuid,
        UUID studentUuid,
        String studentName,
        UUID originClassUuid,
        String originClassTitle,
        LocalDate absentDate,
        UUID targetRegularClassUuid,
        String targetClassTitle,
        String targetTeacherName,
        LocalDate targetDate,
        LocalTime targetStartTime,
        LocalTime targetEndTime,
        MakeupRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt
) {
    public static MakeupRequestResponse from(MakeupRequest request) {
        MakeupTicket ticket = request.getTicket();
        AcademyStudent academyStudent = ticket.getAcademyStudent();
        String managementName = academyStudent.getManagementName();
        String studentName = managementName != null && !managementName.isBlank()
                ? managementName
                : academyStudent.getStudent().getName();

        RegularClass origin = ticket.getOriginClass();
        RegularClass target = request.getTargetRegularClass();
        RegularClassTimeSlot targetTimeSlot = target.getTimeSlotFor(request.getTargetDate().getDayOfWeek())
                .orElseThrow(() -> new IllegalStateException(
                        "대상 반은 " + request.getTargetDate().getDayOfWeek() + "요일에 시간이 지정되어 있지 않습니다."));

        return new MakeupRequestResponse(
                request.getUuid(),
                academyStudent.getStudent().getUuid(),
                studentName,
                origin != null ? origin.getUuid() : null,
                origin != null ? origin.getTitle() : null,
                ticket.getAbsentDate(),
                target.getUuid(),
                target.getTitle(),
                target.getTeacher().getName(),
                request.getTargetDate(),
                targetTimeSlot.getStartTime(),
                targetTimeSlot.getEndTime(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getDecidedAt()
        );
    }
}
