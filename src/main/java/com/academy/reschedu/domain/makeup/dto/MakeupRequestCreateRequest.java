package com.academy.reschedu.domain.makeup.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 학부모가 본인 자녀의 보강권으로 다른 정규 수업의 특정 날짜 회차에 보강 참석을 신청할 때 사용하는 요청 DTO.
 */
public record MakeupRequestCreateRequest(
        @NotNull(message = "자녀(수강생) 정보는 필수입니다.")
        UUID studentUuid,

        @NotNull(message = "보강 신청할 정규 수업 정보는 필수입니다.")
        UUID targetRegularClassUuid,

        @NotNull(message = "보강 신청할 날짜는 필수입니다.")
        LocalDate targetDate
) {
}
