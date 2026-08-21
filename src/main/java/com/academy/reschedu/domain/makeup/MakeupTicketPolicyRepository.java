package com.academy.reschedu.domain.makeup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MakeupTicketPolicyRepository extends JpaRepository<MakeupTicketPolicy, Long> {

    Optional<MakeupTicketPolicy> findByAcademy_Id(Long academyId);
}
