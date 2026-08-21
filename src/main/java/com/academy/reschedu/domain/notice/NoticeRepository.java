package com.academy.reschedu.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByAcademyIdOrderByCreatedAtDesc(Long academyId);

    Optional<Notice> findByUuidAndAcademyId(UUID uuid, Long academyId);

    @Query("select n from Notice n where n.academy.id = :academyId and n.visible = true "
            + "and (n.visibleFrom is null or n.visibleFrom <= :today) "
            + "and (n.visibleUntil is null or n.visibleUntil >= :today) "
            + "order by n.createdAt desc")
    List<Notice> findActiveByAcademyId(@Param("academyId") Long academyId, @Param("today") LocalDate today);
}
