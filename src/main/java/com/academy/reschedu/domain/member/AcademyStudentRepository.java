package com.academy.reschedu.domain.member;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademyStudentRepository extends JpaRepository<AcademyStudent, Long>, AcademyStudentRepositoryCustom {

    @EntityGraph(attributePaths = {"student", "student.parent"})
    List<AcademyStudent> findByAcademyId(Long academyId);

    @EntityGraph(attributePaths = {"student", "student.parent"})
    Optional<AcademyStudent> findByStudentUuidAndAcademyId(UUID studentUuid, Long academyId);

    // 🎯 가입 승인 대상 조회용: 학생 UUID로 소속된 모든 학원 등록 장부를 찾는다.
    List<AcademyStudent> findByStudent_Uuid(UUID studentUuid);

    boolean existsByAcademyIdAndStudent_Id(Long academyId, Long studentId);

    // 🎯 [다학원 자녀 지원] 이 학부모의 자녀 중 한 명이라도 이 학원에 등록되어 있는지 확인 — 학부모 본인의
    // Member.academy와 무관하게, 실제로 자녀가 다니는 학원이라면 조회 권한을 부여하기 위함.
    boolean existsByAcademyIdAndStudent_Parent_Id(Long academyId, Long parentId);

    // 🎯 학부모 전용: 로그인한 부모(memberId)의 자녀들이 등록된 모든 학원 장부를 찾는다.
    // Member.academy가 없는 학부모 계정(수동 등록 등)도 이 경로로 소속 학원을 역추적할 수 있다.
    @EntityGraph(attributePaths = {"academy"})
    List<AcademyStudent> findByStudent_Parent_Id(Long parentId);
}