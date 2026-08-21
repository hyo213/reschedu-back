package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * [EnrollmentPeriodHistory] 수강 기간(수강료 납부 기간) 변경 이력.
 *
 * AcademyStudent.enrollmentStartDate/EndDate는 변경 시 현재 값을 덮어쓸 뿐 과거 값을 남기지 않으므로,
 * [수강 히스토리] 화면에서 "언제 누가 얼마나 연장/변경했는지"를 보여주기 위해 변경 시점마다 스냅샷을
 * 별도로 기록한다. BaseEntity의 createdAt이 곧 "변경된 시각"이다.
 */
@Entity
@Table(name = "enrollment_period_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentPeriodHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_period_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollment_period_history_academy_student"))
    private AcademyStudent academyStudent;

    /** 변경을 수행한 원장/강사. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_member_id",
            foreignKey = @ForeignKey(name = "fk_enrollment_period_history_changed_by"))
    private Member changedBy;

    @Column(name = "previous_start_date")
    private LocalDate previousStartDate;

    @Column(name = "previous_end_date")
    private LocalDate previousEndDate;

    @Column(name = "new_start_date")
    private LocalDate newStartDate;

    @Column(name = "new_end_date")
    private LocalDate newEndDate;

    @Builder
    private EnrollmentPeriodHistory(AcademyStudent academyStudent, Member changedBy,
                                     LocalDate previousStartDate, LocalDate previousEndDate,
                                     LocalDate newStartDate, LocalDate newEndDate) {
        this.academyStudent = academyStudent;
        this.changedBy = changedBy;
        this.previousStartDate = previousStartDate;
        this.previousEndDate = previousEndDate;
        this.newStartDate = newStartDate;
        this.newEndDate = newEndDate;
    }
}
