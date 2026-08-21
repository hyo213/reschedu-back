package com.academy.reschedu.domain.regularclass;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RegularClassStudentRepository extends JpaRepository<RegularClassStudent, Long> {

    @EntityGraph(attributePaths = {"academyStudent", "academyStudent.student"})
    List<RegularClassStudent> findByRegularClass_Id(Long regularClassId);

    boolean existsByRegularClass_IdAndAcademyStudent_Id(Long regularClassId, Long academyStudentId);

    // 🎯 [수강생 관리] 화면에서 특정 학생의 수강 기간이 바뀔 때, 이 학생이 편성된 모든 시간표를 찾기 위함
    List<RegularClassStudent> findByAcademyStudent_Id(Long academyStudentId);

    // 🎯 학부모 전용: 로그인한 부모(memberId)의 자녀가 편성된 모든 시간표 등록 명단 조회
    @EntityGraph(attributePaths = {"regularClass", "regularClass.teacher", "regularClass.academy",
            "academyStudent", "academyStudent.student"})
    List<RegularClassStudent> findByAcademyStudent_Student_Parent_Id(Long parentMemberId);

    // 🎯 [수강 히스토리] 특정 수강생의 반 배정 이력(현재+과거+미래 예정) 전체를 최신순으로 조회하기 위함
    @EntityGraph(attributePaths = {"regularClass", "regularClass.teacher"})
    List<RegularClassStudent> findByAcademyStudent_IdOrderByStartDateDesc(Long academyStudentId);

    // 🎯 [수강생 목록] 화면에 학생별 시간표 요약("월3수4금5")을 함께 보여주기 위해, 여러 학생의 배정을
    // 한 번의 벌크 조회로 가져온다(학생 수만큼 반복 조회하는 N+1을 피하기 위함).
    @EntityGraph(attributePaths = {"regularClass"})
    List<RegularClassStudent> findByAcademyStudent_IdIn(Collection<Long> academyStudentIds);
}
