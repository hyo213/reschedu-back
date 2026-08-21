package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [MakeupTicketPolicy] 학원별 보강권 전체 정책(원장 전용 설정) — 학원당 정확히 한 행. 세 항목 모두
 * null이면 "제한 없음": maxOutstandingTickets(학생당 최대 보유 미사용 개수), monthlyIssueLimit(학생당
 * 월 발급 최대 개수), defaultValidityDays(발급 시 기본 유효 기간). STUDENT_ABSENCE/MANUAL_GRANT에만
 * 적용되고 ACADEMY_HOLIDAY는 제한과 무관하게 항상 발급된다.
 */
@Entity
@Table(
        name = "makeup_ticket_policy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_makeup_ticket_policy_academy", columnNames = "academy_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MakeupTicketPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "makeup_ticket_policy_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_makeup_ticket_policy_academy"))
    private Academy academy;

    @Column(name = "max_outstanding_tickets")
    private Integer maxOutstandingTickets;

    @Column(name = "monthly_issue_limit")
    private Integer monthlyIssueLimit;

    @Column(name = "default_validity_days")
    private Integer defaultValidityDays;

    @Builder
    private MakeupTicketPolicy(Academy academy, Integer maxOutstandingTickets, Integer monthlyIssueLimit, Integer defaultValidityDays) {
        this.academy = academy;
        this.maxOutstandingTickets = maxOutstandingTickets;
        this.monthlyIssueLimit = monthlyIssueLimit;
        this.defaultValidityDays = defaultValidityDays;
    }

    public void update(Integer maxOutstandingTickets, Integer monthlyIssueLimit, Integer defaultValidityDays) {
        this.maxOutstandingTickets = maxOutstandingTickets;
        this.monthlyIssueLimit = monthlyIssueLimit;
        this.defaultValidityDays = defaultValidityDays;
    }
}
