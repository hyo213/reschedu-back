package com.academy.reschedu.domain.member;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.academy.reschedu.domain.member.QAcademyStudent.academyStudent;
import static com.academy.reschedu.domain.member.QStudent.student;

@RequiredArgsConstructor
public class AcademyStudentRepositoryCustomImpl implements AcademyStudentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AcademyStudent> search(Long academyId, UUID teacherUuid, boolean unpaidOnly, String keyword) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(academyStudent.academy.id.eq(academyId));

        if (teacherUuid != null) {
            // teacher가 아직 NULL인 등록 건은 모든 강사에게 보인다("주인 없는" 학생 배정 유도).
            // 인계 중(effectiveFrom 전날까지)인 학생은 previousTeacher 조건으로 이전 강사에게도 함께 보인다.
            LocalDate today = LocalDate.now();
            where.and(academyStudent.teacher.uuid.eq(teacherUuid)
                    .or(academyStudent.teacher.isNull())
                    .or(academyStudent.previousTeacher.uuid.eq(teacherUuid)
                            .and(academyStudent.teacherHandoverEffectiveFrom.gt(today))));
        }
        if (unpaidOnly) {
            where.and(unpaidPredicate());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.and(student.name.containsIgnoreCase(keyword)
                    .or(academyStudent.managementName.containsIgnoreCase(keyword)));
        }

        // student/student.parent는 fetchJoin으로 N+1을 피한다. teacher는 반드시 leftJoin으로 명시해야
        // 한다 — 암묵적 INNER JOIN이 되면 teacher가 NULL인 로우가 where절과 무관하게 걸러진다.
        return queryFactory
                .selectFrom(academyStudent)
                .join(academyStudent.student, student).fetchJoin()
                .leftJoin(student.parent).fetchJoin()
                .leftJoin(academyStudent.teacher)
                .leftJoin(academyStudent.previousTeacher)
                .where(where)
                .fetch();
    }

    /**
     * AcademyStudent.isPaidForUpcomingBillingMonth(LocalDate)의 "미납" 반대 조건을 QueryDSL로 옮긴 것.
     * 매달 20일을 기준으로 다가오는 청구월의 납부 여부를 판단하는 규칙이 바뀌면 두 곳을 함께 수정해야 한다.
     */
    private BooleanExpression unpaidPredicate() {
        LocalDate today = LocalDate.now();
        LocalDate billingMonthStart = today.getDayOfMonth() >= 20
                ? today.plusMonths(1).withDayOfMonth(1)
                : today.withDayOfMonth(1);
        return academyStudent.enrollmentEndDate.isNotNull()
                .and(academyStudent.enrollmentEndDate.lt(billingMonthStart));
    }
}
