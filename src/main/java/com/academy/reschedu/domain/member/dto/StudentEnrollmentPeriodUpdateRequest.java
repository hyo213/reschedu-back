package com.academy.reschedu.domain.member.dto;

import java.time.LocalDate;

/**
 * [수강생 관리] 화면에서 수강 기간(수강료 납부 기간)을 등록/연장할 때 사용하는 요청 DTO.
 * 둘 다 null이면 기간 관리 대상에서 제외된다(미결제/만료 표시를 하지 않음).
 */
public record StudentEnrollmentPeriodUpdateRequest(
        LocalDate enrollmentStartDate,
        LocalDate enrollmentEndDate
) {
}
