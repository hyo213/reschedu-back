package com.academy.reschedu.domain.regularclass;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RegularClassSessionStudentRepository extends JpaRepository<RegularClassSessionStudent, Long> {

    @EntityGraph(attributePaths = {"academyStudent", "academyStudent.student"})
    List<RegularClassSessionStudent> findBySession_Id(Long sessionId);

    // 🎯 성능 최적화: 주간 조회/여석 조회 시 세션별로 반복 조회하던 로스터를 한 번에 벌크 조회하기 위함
    @EntityGraph(attributePaths = {"session", "academyStudent", "academyStudent.student"})
    List<RegularClassSessionStudent> findBySession_IdIn(Collection<Long> sessionIds);

    boolean existsBySession_IdAndAcademyStudent_Id(Long sessionId, Long academyStudentId);

    void deleteBySession_IdAndAcademyStudent_Id(Long sessionId, Long academyStudentId);
}
