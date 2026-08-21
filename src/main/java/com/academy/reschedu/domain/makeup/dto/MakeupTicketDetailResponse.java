package com.academy.reschedu.domain.makeup.dto;

import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.makeup.MakeupTicketSource;
import com.academy.reschedu.domain.makeup.MakeupTicketStatus;
import com.academy.reschedu.domain.regularclass.RegularClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 보강권 상세/이력 한 건 — "원래 어떤 수업의 어떤 날짜"로 인해 이 티켓이 발급되었는지, 지금 상태·유효
 * 기한이 어떻게 되는지를 알려준다. [보강권 관리](원장/강사)와 학부모의 보강권 이력 화면에서 공용으로 쓴다.
 * 🚨 source가 MANUAL_GRANT(수동 지급)인 티켓은 특정 수업/날짜에 묶여 있지 않으므로
 * classTitle/teacherName/absentDate가 모두 null일 수 있다 — 대신 memo에 지급 사유가 담긴다.
 */
public record MakeupTicketDetailResponse(
        UUID ticketUuid,
        LocalDate absentDate,
        String classTitle,
        String teacherName,
        MakeupTicketSource source,
        MakeupTicketStatus status,
        LocalDateTime expiredAt,
        String memo
) {
    public static MakeupTicketDetailResponse from(MakeupTicket ticket) {
        RegularClass origin = ticket.getOriginClass();
        return new MakeupTicketDetailResponse(
                ticket.getUuid(),
                ticket.getAbsentDate(),
                origin != null ? origin.getTitle() : null,
                origin != null ? origin.getTeacher().getName() : null,
                ticket.getSource(),
                ticket.getStatus(),
                ticket.getExpiredAt(),
                ticket.getMemo()
        );
    }
}
