package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.AcademyStudent;
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
 * [MakeupTicket] 보강권(티켓) 엔티티. 학생이 정규 수업을 결석했을 때(본인 신청 또는 학원 휴무일로 인한
 * 자동 취소) 발급되며, 다른 정규 수업의 여석에 보강 신청(MakeupRequest)할 때 사용한다.
 * 라이프사이클: UNUSED(미사용) → USED(사용 완료) or EXPIRED(만료).
 * source가 MANUAL_GRANT(수동 지급)인 티켓은 특정 수업/날짜에 묶이지 않으므로 originClass/absentDate가 null이다.
 */
@Entity
@Table(
        name = "makeup_ticket",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_makeup_ticket_uuid", columnNames = "uuid")
        },
        indexes = {
                @Index(name = "idx_makeup_ticket_academy_student_status",
                        columnList = "academy_student_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MakeupTicket extends BaseEntity {

    // ─── 식별자 ────────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "makeup_ticket_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    // ─── 연관 관계 ──────────────────────────────────────────────────────────────

    /** 티켓을 발급받은 학생의 학원별 등록 장부 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_makeup_ticket_academy_student"))
    private AcademyStudent academyStudent;

    /** 결석한 원래 정규 수업. MANUAL_GRANT 티켓은 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_class_id",
            foreignKey = @ForeignKey(name = "fk_makeup_ticket_origin_class"))
    private RegularClass originClass;

    // ─── 티켓 정보 ──────────────────────────────────────────────────────────────

    /** 결석 처리된 날짜 (originClass의 특정 회차). MANUAL_GRANT 티켓은 null이다. */
    @Column(name = "absent_date")
    private LocalDate absentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MakeupTicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MakeupTicketSource source;

    /** 티켓 유효 기한. null이면 만료 없음("제한 없음"). */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    /** 원장/강사가 수동으로 지급할 때 남기는 사유(선택) — 예: "학원 이전 전 잔여분 이관". */
    @Column(name = "memo", length = 200)
    private String memo;

    // ─── 생성자 (Builder) ──────────────────────────────────────────────────────

    @Builder
    private MakeupTicket(AcademyStudent academyStudent,
                         RegularClass originClass,
                         LocalDate absentDate,
                         MakeupTicketSource source,
                         LocalDateTime expiredAt,
                         String memo) {
        this.academyStudent = academyStudent;
        this.originClass    = originClass;
        this.absentDate     = absentDate;
        this.source         = source;
        this.expiredAt      = expiredAt;
        this.memo           = memo;
        this.status         = MakeupTicketStatus.UNUSED;
    }

    // ─── 비즈니스 메서드 ────────────────────────────────────────────────────────

    /** 보강 수업 신청 시 티켓을 사용 처리한다. */
    public void use() {
        validateUsable();
        this.status = MakeupTicketStatus.USED;
    }

    /** 만료 처리. */
    public void expire() {
        if (this.status == MakeupTicketStatus.USED) {
            throw new IllegalStateException("이미 사용된 티켓은 만료 처리할 수 없습니다.");
        }
        this.status = MakeupTicketStatus.EXPIRED;
    }

    /** 티켓이 현재 사용 가능한 상태인지 검증한다(UNUSED이고 만료 기한이 지나지 않아야 함). */
    public void validateUsable() {
        if (this.status != MakeupTicketStatus.UNUSED) {
            throw new IllegalStateException(
                    String.format("사용할 수 없는 티켓입니다. (현재 상태: %s)", this.status)
            );
        }
        if (this.expiredAt != null && LocalDateTime.now().isAfter(this.expiredAt)) {
            this.status = MakeupTicketStatus.EXPIRED;
            throw new IllegalStateException("만료된 티켓입니다.");
        }
    }

    /** 지금 이 순간 실제로 사용 가능한지(상태 변경 없이 확인만 한다). */
    public boolean isCurrentlyValid() {
        if (this.status != MakeupTicketStatus.UNUSED) {
            return false;
        }
        return this.expiredAt == null || !LocalDateTime.now().isAfter(this.expiredAt);
    }

    /** 사용 취소 처리(보강 수업 취소 시 롤백). */
    public void cancelUse() {
        if (this.status != MakeupTicketStatus.USED) {
            throw new IllegalStateException("사용 완료된 티켓만 취소할 수 있습니다.");
        }
        this.status = MakeupTicketStatus.UNUSED;
    }
}
