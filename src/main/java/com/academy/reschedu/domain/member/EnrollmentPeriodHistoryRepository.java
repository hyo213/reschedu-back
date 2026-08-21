package com.academy.reschedu.domain.member;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentPeriodHistoryRepository extends JpaRepository<EnrollmentPeriodHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<EnrollmentPeriodHistory> findByAcademyStudent_IdOrderByCreatedAtDesc(Long academyStudentId);
}
