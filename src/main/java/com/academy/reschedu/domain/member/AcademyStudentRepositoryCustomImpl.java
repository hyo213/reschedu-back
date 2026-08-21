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
            // 🚨 담당 강사가 아직 배정되지 않은(teacher가 NULL인) 등록 건은 강사 필터와 무관하게 항상
            // 보여야 한다 — 그래야 어느 강사든 "주인 없는" 학생(승인 대기중이든, 승인은 됐지만 아직
            // 아무 강사에게도 배정되지 않았든)을 놓치지 않고 보고 담당을 맡을 수 있다. 이미 특정 강사가
            // 배정된 건은 기존처럼 그 강사(또는 원장)에게만 보인다.
            //
            // 🎯 담당 강사 인계 중(effectiveFrom 전날까지)인 학생은 이전 강사에게도 함께 보여야 한다 —
            // teacher는 인계 순간 이미 새 강사로 바뀌어 있으므로, previousTeacher + 아직 지나지 않은
            // teacherHandoverEffectiveFrom 조합으로만 매칭되는 이전 강사 몫을 별도로 더한다.
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

        // 기존 findByAcademyId 계열이 쓰던 @EntityGraph(student, student.parent)와 동일한 효과를
        // fetchJoin으로 재현한다 — 목록 화면에서 학생/학부모 정보를 N+1 없이 한 번에 가져온다.
        // 🚨 teacher는 반드시 leftJoin으로 명시해야 한다 — where절에서 academyStudent.teacher.uuid를
        // 참조할 때 명시적 조인이 없으면 Hibernate가 암묵적 INNER JOIN을 생성해, teacher가 NULL인(아직
        // 담당 강사가 없는) 로우가 where의 OR 조건과 무관하게 조인 단계에서 통째로 걸러져 버린다.
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
