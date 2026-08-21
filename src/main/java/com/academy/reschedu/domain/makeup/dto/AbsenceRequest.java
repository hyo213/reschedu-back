package com.academy.reschedu.domain.makeup.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 학부모가 본인 자녀의 특정 정규 수업 "특정 날짜 회차"에 대해 결석을 신청할 때 사용하는 요청 DTO.
 * 다른 주차의 같은 요일 수업에는 영향을 주지 않고, 지정된 날짜 하루만 예외적으로 결석 처리된다.
 */
public record AbsenceRequest(
        @NotNull(message = "자녀(수강생) 정보는 필수입니다.")
        UUID studentUuid,

        @NotNull(message = "결석할 정규 수업 정보는 필수입니다.")
        UUID regularClassUuid,

        @NotNull(message = "결석 날짜는 필수입니다.")
        LocalDate absentDate,

        /**
         * 🎯 [보강권 전체 정책] 원장/강사가 결석을 대리 처리할 때만 의미가 있다 — 발급 제한을 초과한다는
         * MakeupTicketLimitExceededException을 한 번 받은 뒤, "그래도 발급하시겠습니까?" 확인을 거쳐
         * true로 재요청하면 제한을 무시하고 강행한다. 학부모 본인 신청은 이 값과 무관하게 항상 막힌다.
         * JSON에 없으면 false로 처리된다(기본 동작 변화 없음).
         */
        boolean overrideLimit
) {
}
