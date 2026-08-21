package com.academy.reschedu.domain.regularclass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegularClassSessionRepository extends JpaRepository<RegularClassSession, Long> {

    Optional<RegularClassSession> findByRegularClass_IdAndDate(Long regularClassId, LocalDate date);

    List<RegularClassSession> findByRegularClass_Id(Long regularClassId);

    // 🎯 성능 최적화: 주간 조회/여석 조회 시 날짜별로 반복 조회하던 것을 한 번의 범위 조회로 대체하기 위함
    List<RegularClassSession> findByRegularClass_IdAndDateBetween(Long regularClassId, LocalDate start, LocalDate end);
}
