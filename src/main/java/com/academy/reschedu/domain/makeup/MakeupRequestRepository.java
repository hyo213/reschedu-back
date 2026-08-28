package com.academy.reschedu.domain.makeup;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MakeupRequestRepository extends JpaRepository<MakeupRequest, Long> {

    @EntityGraph(attributePaths = {
            "ticket", "ticket.academyStudent", "ticket.academyStudent.student", "ticket.originClass", "ticket.originClass.teacher",
            "targetRegularClass", "targetRegularClass.teacher"
    })
    Optional<MakeupRequest> findByUuid(UUID uuid);

    // 🎯 한 티켓이 동시에 여러 대기/수락 신청에 중복 연결되지 않도록 신청 생성 시 검증하기 위함
    boolean existsByTicket_IdAndStatusIn(Long ticketId, List<MakeupRequestStatus> statuses);

    // 🎯 휴무일 취소 시, 그 휴무 티켓으로 이미 수락된 보강 매칭이 있으면 찾아 취소하기 위함
    @EntityGraph(attributePaths = {"targetRegularClass"})
    Optional<MakeupRequest> findByTicket_IdAndStatus(Long ticketId, MakeupRequestStatus status);

    // 🎯 같은 날짜에 이미 잡힌 다른 보강 신청과 시간이 겹치는지 검증하기 위함
    @EntityGraph(attributePaths = {"targetRegularClass"})
    List<MakeupRequest> findByTicket_AcademyStudent_IdAndTargetDateAndStatusIn(
            Long academyStudentId, LocalDate targetDate, List<MakeupRequestStatus> statuses);

    // 🎯 [정규 수업 완전 삭제] 이 반을 대상으로 신청된 보강 신청 이력이 있으면 삭제를 막기 위함
    boolean existsByTargetRegularClass_Id(Long regularClassId);

    // 🎯 같은 여석(수업+날짜)에 이미 걸려 있는 대기(PENDING) 신청 수 — 아직 로스터에 반영되지 않았지만
    // 사실상 자리를 예약 중인 건이므로, 신청 시점 정원 계산에 로스터 인원과 함께 더해야 한다
    long countByTargetRegularClass_IdAndTargetDateAndStatus(Long regularClassId, LocalDate targetDate, MakeupRequestStatus status);

    // 🎯 학부모 전용: 본인 자녀들의 보강 신청 내역 전체(상태 무관) 조회
    @EntityGraph(attributePaths = {
            "ticket", "ticket.academyStudent", "ticket.academyStudent.student", "ticket.originClass", "ticket.originClass.teacher",
            "targetRegularClass", "targetRegularClass.teacher"
    })
    List<MakeupRequest> findByTicket_AcademyStudent_Student_Parent_IdOrderByCreatedAtDesc(Long parentMemberId);

    // 🎯 원장/강사용: 보강 매칭 센터의 대기 목록 조회
    @EntityGraph(attributePaths = {
            "ticket", "ticket.academyStudent", "ticket.academyStudent.student", "ticket.originClass", "ticket.originClass.teacher",
            "targetRegularClass", "targetRegularClass.teacher"
    })
    List<MakeupRequest> findByTargetRegularClass_Academy_IdAndStatusOrderByCreatedAtAsc(Long academyId, MakeupRequestStatus status);
}
