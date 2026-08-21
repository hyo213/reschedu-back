package com.academy.reschedu.domain.regularclass;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegularClassRepository extends JpaRepository<RegularClass, Long> {

    @EntityGraph(attributePaths = {"teacher", "academy"})
    List<RegularClass> findByAcademyId(Long academyId);

    @EntityGraph(attributePaths = {"teacher", "academy"})
    List<RegularClass> findByAcademyIdAndTeacher_Uuid(Long academyId, UUID teacherUuid);

    @EntityGraph(attributePaths = {"teacher", "academy"})
    Optional<RegularClass> findByUuidAndAcademyId(UUID uuid, Long academyId);

    @EntityGraph(attributePaths = {"teacher", "academy"})
    Optional<RegularClass> findByUuid(UUID uuid);
}
