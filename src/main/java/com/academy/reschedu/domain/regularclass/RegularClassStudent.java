package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.AcademyStudent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * [RegularClassStudent] 정규 시간표 ↔ 수강생 편성 명단(조인 엔티티). RegularClass에 어떤 학생이
 * 편성되어 있는지를 나타낸다(AcademyStudent 참조). startDate/endDate는 이 반에 배정되어 있던 기간이며,
 * 둘 다 null이면 기간 제약 없이 항상 활성이다. 같은 (반, 학생) 조합이 기간을 달리해 여러 행으로 쌓일 수
 * 있고, 이 이력이 수강 히스토리의 데이터 소스가 된다. 겹치는 기간 중복 배정은 서비스 계층에서 검증한다.
 */
@Entity
@Table(name = "regular_class_student")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegularClassStudent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regular_class_student_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regular_class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_student_class"))
    private RegularClass regularClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_student_academy_student"))
    private AcademyStudent academyStudent;

    /** 이 반 배정이 유효한 기간(선택). 둘 다 null이면 기간 제약 없이 항상 유효하다. */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public RegularClassStudent(RegularClass regularClass, AcademyStudent academyStudent) {
        this.regularClass = regularClass;
        this.academyStudent = academyStudent;
    }

    public RegularClassStudent(RegularClass regularClass, AcademyStudent academyStudent, LocalDate startDate, LocalDate endDate) {
        this.regularClass = regularClass;
        this.academyStudent = academyStudent;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 주어진 날짜에 이 반 배정이 유효한지 여부. */
    public boolean isActiveOn(LocalDate date) {
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !date.isAfter(endDate);
    }

    /** 배정을 종료 처리한다(삭제하지 않고 endDate만 채워 히스토리로 남긴다). */
    public void endOn(LocalDate endDate) {
        this.endDate = endDate;
    }
}
