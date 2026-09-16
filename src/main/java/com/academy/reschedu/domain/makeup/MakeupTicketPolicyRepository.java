package com.academy.reschedu.domain.makeup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MakeupTicketPolicyRepository extends JpaRepository<MakeupTicketPolicy, Long> {

    Optional<MakeupTicketPolicy> findByAcademy_Id(Long academyId);

    // 🎯 [AI 리포트] 학부모 자녀가 여러 학원에 걸쳐 있어도 정책 조회를 한 번에 끝내기 위함(N+1 회피)
    List<MakeupTicketPolicy> findByAcademy_IdIn(List<Long> academyIds);
}
