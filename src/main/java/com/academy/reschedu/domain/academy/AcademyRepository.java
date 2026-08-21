package com.academy.reschedu.domain.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AcademyRepository extends JpaRepository<Academy, Long> {
    // 키워드가 포함된 학원 이름 리스트 검색 (Like 조회)
    List<Academy> findByNameContaining(String keyword);

    // 학원명 중복 체크용
    Optional<Academy> findByName(String name);
}