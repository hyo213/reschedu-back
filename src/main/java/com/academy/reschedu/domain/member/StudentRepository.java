package com.academy.reschedu.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByParentIdAndNameAndBirthDate(Long parentId, String name, java.time.LocalDate birthDate);

    Optional<Student> findByUuid(UUID uuid);

    List<Student> findByParentId(Long parentId);
}