package com.academy.reschedu.domain.academy.dto;

import com.academy.reschedu.domain.academy.AcademyHoliday;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
        UUID uuid,
        LocalDate date,
        String reason,
        // 휴무일 등록 시점에 자동 발급된 보강권 개수. 목록 조회 시에는 계산하지 않으므로 null.
        Integer issuedTicketCount
) {
    public static HolidayResponse of(AcademyHoliday holiday, Integer issuedTicketCount) {
        return new HolidayResponse(holiday.getUuid(), holiday.getDate(), holiday.getReason(), issuedTicketCount);
    }
}
