package com.academy.reschedu.domain.makeup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 원장/강사가 보강 매칭 센터에서 특정 학생에게 보강권을 수동으로 지급할 때 사용하는 요청 DTO.
 * 앱 도입 전 보유하고 있던 잔여 보강권을 등록하거나, 필요에 따라 추가 지급할 때 쓰인다.
 */
public record ManualTicketGrantRequest(
        @NotNull(message = "보강권을 지급할 수강생 정보는 필수입니다.")
        UUID studentUuid,

        @NotNull(message = "지급할 보강권 개수는 필수입니다.")
        @Min(value = 1, message = "지급할 보강권 개수는 1개 이상이어야 합니다.")
        @Max(value = 50, message = "한 번에 지급할 수 있는 보강권은 50개 이하여야 합니다.")
        Integer quantity,

        /** 지급 사유(선택) — 예: "학원 이전 전 잔여분 이관" */
        String memo,

        /**
         * 이번 지급 건에만 적용할 유효기간(일) 오버라이드. null이면 "기한 제한 없음"(만료되지 않음)을 뜻한다
         * — 프론트에서 [보강권 전체 설정]의 기본 유효기간을 입력값으로 미리 채워주되, "기한 제한 없음"
         * 체크박스를 체크하면 이 필드를 null로 보낸다.
         */
        @Min(value = 1, message = "유효기간은 1일 이상이어야 합니다.")
        Integer validityDays,

        /**
         * 🎯 [보강권 전체 정책] 발급 제한(최대 보유 개수/월 발급 제한)을 초과한다는
         * MakeupTicketLimitExceededException을 한 번 받은 뒤, "그래도 지급하시겠습니까?" 확인을 거쳐
         * true로 재요청하면 제한을 무시하고 강행한다. JSON에 없으면 false로 처리된다.
         */
        boolean overrideLimit
) {
}
