package com.academy.reschedu.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 🔐 로그인 및 인증용 조회 (원장/강사는 이메일 기준)
    Optional<Member> findByEmail(String email);

    // 📱 [신규 핵심] 학부모 최초 로그인 및 수기 등록 중복 감지용 (휴대폰 번호 기준)
    Optional<Member> findByPhone(String phone);

    // 👤 이메일 및 휴대폰 번호 가입 중복 검증용
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // 🔑 내부 식별 및 승인 처리용 API 타겟용 조회
    Optional<Member> findByUuid(UUID uuid);

    /**
     * 특정 학원에 소속된 권한별 계정 조회 (원장님용 학원 내 전체 강사 목록 땡겨오기 등)
     * * 이제 STUDENT(수강생)는 별도 테이블로 독립했으므로, 주로 TEACHER(강사) 목록을 뽑을 때 사용됩니다.
     */
    List<Member> findByAcademyIdAndRole(Long academyId, MemberRole role);
}