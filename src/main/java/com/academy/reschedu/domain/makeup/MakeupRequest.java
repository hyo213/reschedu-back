package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.regularclass.RegularClass;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [MakeupRequest] 학부모가 보유한 보강권(MakeupTicket)으로 다른 정규 수업의 특정 날짜 회차에 보강
 * 참석을 신청하는 요청. 라이프사이클: PENDING(대기) → APPROVED(수락, 티켓 USED + 로스터 편입) or
 * REJECTED(거절, 티켓은 UNUSED 유지).
 */
@Entity
@Table(
        name = "makeup_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_makeup_request_uuid", columnNames = "uuid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MakeupRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "makeup_request_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_makeup_request_ticket"))
    private MakeupTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_regular_class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_makeup_request_target_class"))
    private RegularClass targetRegularClass;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MakeupRequestStatus status;

    /** 수락/거절을 처리한 원장/강사. 대기(PENDING) 상태에는 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_member_id",
            foreignKey = @ForeignKey(name = "fk_makeup_request_decided_by"))
    private Member decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Builder
    private MakeupRequest(MakeupTicket ticket, RegularClass targetRegularClass, LocalDate targetDate) {
        this.ticket = ticket;
        this.targetRegularClass = targetRegularClass;
        this.targetDate = targetDate;
        this.status = MakeupRequestStatus.PENDING;
    }

    /** 보강 신청을 수락한다(로스터 편입/티켓 사용 처리는 서비스 계층 담당). */
    public void approve(Member decider) {
        if (this.status != MakeupRequestStatus.PENDING) {
            throw new IllegalStateException("대기 중인 보강 신청만 수락할 수 있습니다.");
        }
        this.status = MakeupRequestStatus.APPROVED;
        this.decidedBy = decider;
        this.decidedAt = LocalDateTime.now();
    }

    /** 보강 신청을 거절한다(티켓은 소비되지 않는다). */
    public void reject(Member decider) {
        if (this.status != MakeupRequestStatus.PENDING) {
            throw new IllegalStateException("대기 중인 보강 신청만 거절할 수 있습니다.");
        }
        this.status = MakeupRequestStatus.REJECTED;
        this.decidedBy = decider;
        this.decidedAt = LocalDateTime.now();
    }
}
