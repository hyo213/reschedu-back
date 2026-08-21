package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.AcademyStudent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [RegularClassSessionStudent] 특정 날짜 회차(RegularClassSession)의 수강생 명단 스냅샷.
 *
 * RegularClassStudent(템플릿 로스터)와 별개로, "이 날짜에 실제로 유효했던 수강생이 누구였는지"를
 * 세션 생성 시점에 그대로 옮겨 담는다. 이후 학생의 수강 기간이 바뀌거나 템플릿 로스터가 바뀌면
 * RegularClassService가 이미 생성된 세션들에 대해 이 스냅샷을 다시 동기화한다.
 */
@Entity
@Table(
        name = "regular_class_session_student",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_regular_class_session_student", columnNames = {"session_id", "academy_student_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegularClassSessionStudent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regular_class_session_student_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_session_student_session"))
    private RegularClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_session_student_academy_student"))
    private AcademyStudent academyStudent;

    /** 원래 이 반에 정규 편성된 것이 아니라, 보강 매칭으로 이 회차에 들어온 수강생인지 여부. */
    // 🚨 columnDefinition으로 DEFAULT false를 명시해야 기존 로우가 있는 상태에서도(ddl-auto: update)
    // NOT NULL 컬럼 추가가 실패하지 않는다 — 기존 로우는 전부 "정규 편성"이었으므로 false가 맞다.
    @Column(name = "via_makeup", nullable = false, columnDefinition = "boolean default false")
    private boolean viaMakeup;

    public RegularClassSessionStudent(RegularClassSession session, AcademyStudent academyStudent, boolean viaMakeup) {
        this.session = session;
        this.academyStudent = academyStudent;
        this.viaMakeup = viaMakeup;
    }
}
