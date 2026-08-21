package com.academy.reschedu.domain.makeup.dto;

import jakarta.validation.constraints.Min;

/** 각 필드를 null로 보내면 "제한 없음"으로 저장된다(프론트의 "제한 없음" 체크박스와 대응). */
public record MakeupTicketPolicyUpdateRequest(
        @Min(value = 1, message = "최대 보유 개수는 1개 이상이어야 합니다.")
        Integer maxOutstandingTickets,

        @Min(value = 1, message = "월 발급 제한은 1개 이상이어야 합니다.")
        Integer monthlyIssueLimit,

        @Min(value = 1, message = "유효 기간은 1일 이상이어야 합니다.")
        Integer defaultValidityDays
) {}
