package com.academy.reschedu.domain.academy;

import com.academy.reschedu.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * [AcademyHoliday] 학원 휴무일
 *
 * 이 날짜에 예정되어 있던 모든 정규 수업은 자동 취소되며, issueMakeupTickets가 true면 등록 시점에
 * 해당 수업들의 수강생 전원에게 보강권이 자동 발급된다(발급 로직은 MakeupTicketService가 담당).
 */
@Entity
@Table(
        name = "academy_holiday",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_academy_holiday_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uq_academy_holiday_date", columnNames = {"academy_id", "date"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyHoliday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "academy_holiday_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_academy_holiday_academy"))
    private Academy academy;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** 휴무 사유 (예: "추석 연휴", "학원 자체 정기 휴무") — 선택 입력 */
    @Column(name = "reason", length = 100)
    private String reason;

    /** 보강권 자동 발급 여부 */
    @Column(name = "issue_makeup_tickets", nullable = false)
    private boolean issueMakeupTickets;

    @Builder
    private AcademyHoliday(Academy academy, LocalDate date, String reason, boolean issueMakeupTickets) {
        this.academy = academy;
        this.date = date;
        this.reason = reason;
        this.issueMakeupTickets = issueMakeupTickets;
    }

    /** 휴무 사유 수정 (원장 전용, 날짜/학원은 변경하지 않는다 — 날짜를 바꾸려면 취소 후 재등록한다). */
    public void updateReason(String reason) {
        this.reason = reason;
    }
}
