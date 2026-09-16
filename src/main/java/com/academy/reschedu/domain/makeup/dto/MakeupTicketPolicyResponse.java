package com.academy.reschedu.domain.makeup.dto;

import com.academy.reschedu.domain.makeup.MakeupTicketPolicy;

/** 세 개수 필드는 null이면 "제한 없음"을 의미한다. 정책 행이 아예 없으면 allowUseAfterEnrollmentExpired는 기본값 true. */
public record MakeupTicketPolicyResponse(
        Integer maxOutstandingTickets,
        Integer monthlyIssueLimit,
        Integer defaultValidityDays,
        boolean allowUseAfterEnrollmentExpired
) {
    public static MakeupTicketPolicyResponse from(MakeupTicketPolicy policy) {
        if (policy == null) {
            return new MakeupTicketPolicyResponse(null, null, null, true);
        }
        return new MakeupTicketPolicyResponse(
                policy.getMaxOutstandingTickets(),
                policy.getMonthlyIssueLimit(),
                policy.getDefaultValidityDays(),
                policy.isAllowUseAfterEnrollmentExpired()
        );
    }
}
