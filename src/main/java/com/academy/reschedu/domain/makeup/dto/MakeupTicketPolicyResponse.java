package com.academy.reschedu.domain.makeup.dto;

import com.academy.reschedu.domain.makeup.MakeupTicketPolicy;

/** null 필드는 "제한 없음"을 의미한다. */
public record MakeupTicketPolicyResponse(
        Integer maxOutstandingTickets,
        Integer monthlyIssueLimit,
        Integer defaultValidityDays
) {
    public static MakeupTicketPolicyResponse from(MakeupTicketPolicy policy) {
        if (policy == null) {
            return new MakeupTicketPolicyResponse(null, null, null);
        }
        return new MakeupTicketPolicyResponse(
                policy.getMaxOutstandingTickets(),
                policy.getMonthlyIssueLimit(),
                policy.getDefaultValidityDays()
        );
    }
}
