package com.academy.reschedu.domain.academy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademyHolidayRepository extends JpaRepository<AcademyHoliday, Long> {

    List<AcademyHoliday> findByAcademyIdOrderByDateAsc(Long academyId);

    List<AcademyHoliday> findByAcademyIdInOrderByDateAsc(List<Long> academyIds);

    List<AcademyHoliday> findByAcademyIdAndDateBetweenOrderByDateAsc(Long academyId, LocalDate start, LocalDate end);

    boolean existsByAcademyIdAndDate(Long academyId, LocalDate date);

    Optional<AcademyHoliday> findByUuidAndAcademyId(UUID uuid, Long academyId);
}
