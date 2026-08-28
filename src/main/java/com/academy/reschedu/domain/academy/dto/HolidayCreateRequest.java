package com.academy.reschedu.domain.academy.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HolidayCreateRequest(
        @NotNull(message = "휴무일 날짜는 필수 입력 값입니다.")
        LocalDate date,

        // 휴무 사유 (선택 입력)
        String reason,

        // 보강권 자동 발급 여부 (선택 입력, 생략 시 true)
        Boolean issueMakeupTickets
) {
}
