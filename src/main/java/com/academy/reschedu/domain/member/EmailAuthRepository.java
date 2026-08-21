package com.academy.reschedu.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailAuthRepository extends JpaRepository<EmailAuth, Long> {
    // 이메일로 가장 최근에 생성된 인증 정보 하나를 조회
    Optional<EmailAuth> findFirstByEmailOrderByExpiredAtDesc(String email);
}