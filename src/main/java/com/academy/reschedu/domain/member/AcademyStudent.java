package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.academy.Academy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"academy_id", "student_id"})
        }
)
public class AcademyStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    private String managementName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Member teacher;

    /** 담당 강사 인계(효력일 지정) 중일 때만 채워진다. 효력일 전까지 이전 강사에게도 목록에 계속 보이게 하는 용도. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_teacher_id")
    private Member previousTeacher;

    private LocalDate teacherHandoverEffectiveFrom;

    private boolean isApproved;

    private String schoolName;
    private String shuttlePickupLocation;
    private String shuttleDropoffLocation;
    private String discountType;

    /** 주당 수강 희망 횟수(실제 정규 수업 배정 횟수와는 별개). null이면 미입력. */
    private Integer weeklyFrequency;

    // @Lob 대신 text 컬럼을 명시해 oid 매핑을 피한다.
    @Column(columnDefinition = "text")
    private String memo;

    /** 수강 기간(수강료 납부 완료 기간). 둘 다 null이면 기간 관리 대상이 아니다. */
    private LocalDate enrollmentStartDate;
    private LocalDate enrollmentEndDate;

    public AcademyStudent(Academy academy, Student student, String managementName, Member teacher,
                          boolean isApproved, String schoolName, String shuttlePickupLocation,
                          String shuttleDropoffLocation, String discountType, String memo) {
        this.academy = academy;
        this.student = student;
        this.managementName = managementName;
        this.teacher = teacher;
        this.isApproved = isApproved;
        this.schoolName = schoolName;
        this.shuttlePickupLocation = shuttlePickupLocation;
        this.shuttleDropoffLocation = shuttleDropoffLocation;
        this.discountType = discountType;
        this.memo = memo;
    }

    public void approve() {
        this.isApproved = true;
    }

    /** 학부모 본인 자녀 정보 수정 화면 전용 — 원내 관리 필드(담당강사/셔틀/할인/메모)는 건드리지 않는다. */
    public void updateSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    /** 담당 강사만 즉시 바꾼다(다른 필드는 건드리지 않음). */
    public void updateTeacher(Member teacher) {
        this.teacher = teacher;
        this.previousTeacher = null;
        this.teacherHandoverEffectiveFrom = null;
    }

    /** 담당 강사 인계(효력일 지정). 새 강사는 즉시, 이전 강사는 effectiveFrom 전날까지 목록에 보인다. */
    public void scheduleTeacherHandover(Member previousTeacher, Member newTeacher, LocalDate effectiveFrom) {
        this.teacher = newTeacher;
        this.previousTeacher = previousTeacher;
        this.teacherHandoverEffectiveFrom = effectiveFrom;
    }

    /**
     * 수강 기간(수강료 납부 완료 기간) 등록/연장. [수강생 관리] 화면 전용 액션.
     */
    public void updateEnrollmentPeriod(LocalDate startDate, LocalDate endDate) {
        this.enrollmentStartDate = startDate;
        this.enrollmentEndDate = endDate;
    }

    /**
     * 주어진 날짜에 이 학생이 실제로 수강 중(수강 기간 내)인지 여부.
     */
    public boolean isActiveOn(LocalDate date) {
        if (enrollmentStartDate != null && date.isBefore(enrollmentStartDate)) {
            return false;
        }
        return enrollmentEndDate == null || !date.isAfter(enrollmentEndDate);
    }

    /**
     * 수강 기간이 완전히 지났는지 여부("수강기간 만료" 상태).
     */
    public boolean isExpired(LocalDate today) {
        return enrollmentEndDate != null && enrollmentEndDate.isBefore(today);
    }

    /**
     * 매달 20일을 기준으로 판단하는 "다가오는 청구월" 수강료 납부 여부.
     * - 오늘이 20일 이후면 다음 달이, 20일 이전이면 이번 달이 청구월이다.
     * - 기간이 설정되지 않은 학생(enrollmentEndDate == null)은 결제 추적 대상이 아니므로 항상 납부된 것으로 간주한다.
     */
    public boolean isPaidForUpcomingBillingMonth(LocalDate today) {
        if (enrollmentEndDate == null) {
            return true;
        }
        LocalDate billingMonthStart = today.getDayOfMonth() >= 20
                ? today.plusMonths(1).withDayOfMonth(1)
                : today.withDayOfMonth(1);
        return !enrollmentEndDate.isBefore(billingMonthStart);
    }

    public void updateAcademyStudentInfo(String managementName, Member teacher, String schoolName,
                                         String shuttlePickupLocation, String shuttleDropoffLocation,
                                         String discountType, String memo, Integer weeklyFrequency, LocalDate birthDate,
                                         String gender, String childPhone, String parentPhone) {
        this.managementName = managementName;
        this.teacher = teacher;
        this.schoolName = schoolName;
        this.shuttlePickupLocation = shuttlePickupLocation;
        this.shuttleDropoffLocation = shuttleDropoffLocation;
        this.discountType = discountType;
        this.memo = memo;
        this.weeklyFrequency = weeklyFrequency;

        if (this.student != null) {
            this.student.updateCoreInfo(birthDate, gender, childPhone);
            if (this.student.getParent() != null) {
                this.student.getParent().updatePhone(parentPhone);
            }
        }
    }
}