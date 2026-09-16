package com.academy.reschedu.domain.makeup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MakeupTicketRepository extends JpaRepository<MakeupTicket, Long> {

    // 🎯 [보강권 관리] 원장/강사가 특정 보강권을 삭제할 때 uuid로 조회하기 위함
    Optional<MakeupTicket> findByUuid(UUID uuid);

    boolean existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(Long regularClassId, Long academyStudentId, LocalDate absentDate);

    // 🎯 성능 최적화: 일정 충돌 검증 시 수업별로 반복 조회하던 결석 티켓을, 이 학생의 그 날짜 결석 티켓
    // 전체를 한 번에 가져와 메모리에서 걸러내는 방식으로 대체하기 위함 (N+1 회피)
    List<MakeupTicket> findByAcademyStudent_IdAndAbsentDate(Long academyStudentId, LocalDate absentDate);

    // 🎯 [정규 수업 완전 삭제] 이 반을 결석 사유로 발급된 보강권이 하나라도 있으면 삭제를 막기 위함
    boolean existsByOriginClass_Id(Long regularClassId);

    long countByAcademyStudent_IdAndStatus(Long academyStudentId, MakeupTicketStatus status);

    List<MakeupTicket> findByAcademyStudent_Academy_IdAndStatus(Long academyId, MakeupTicketStatus status);

    // 🎯 [AI 리포트] 학부모의 자녀가 여러 명이어도 학생별로 반복 조회하지 않도록, 대상 학생 전원의 보강권을
    // 한 번에 가져와 메모리에서 학생별로 묶어 쓰기 위함(N+1 회피). 상태 무관 전체를 가져와 결석 횟수/
    // 사용가능/사용완료를 전부 이 한 결과로 계산한다.
    List<MakeupTicket> findByAcademyStudent_IdIn(List<Long> academyStudentIds);

    // 특정 정규 수업의 특정 날짜에 결석 처리(어떤 사유든)된 수강생들의 학원등록장부 id 목록 조회용
    List<MakeupTicket> findByOriginClass_IdAndAbsentDate(Long regularClassId, LocalDate absentDate);

    // 🎯 성능 최적화: 주간 조회 시 날짜별로 반복 조회하던 결석 티켓을 한 번의 범위 조회로 대체하기 위함
    List<MakeupTicket> findByOriginClass_IdAndAbsentDateBetween(Long regularClassId, LocalDate start, LocalDate end);

    // 🎯 보강 매칭 센터: 학생별 잔여 보강권의 상세 내역(원래 수업일 목록) 조회용
    List<MakeupTicket> findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(Long academyStudentId, MakeupTicketStatus status);

    // 🎯 [보강권 관리 / 학부모 보강권 이력] 상태 무관 전체 이력(사용/만료 포함) 최신순 조회용
    List<MakeupTicket> findByAcademyStudent_IdOrderByCreatedAtDesc(Long academyStudentId);

    // 🎯 [보강권 정책] 이번 달(달력 기준) 이 학생에게 이미 발급된 보강권 개수(상태 무관, 발급 자체를 센다) 조회용
    long countByAcademyStudent_IdAndCreatedAtBetween(Long academyStudentId, LocalDateTime start, LocalDateTime end);

    // 🎯 휴무일 지정 취소 시, 그 날짜에 휴무로 인해 발급된 미사용 티켓만 정확히 찾아 회수하기 위함
    Optional<MakeupTicket> findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSourceAndStatus(
            Long regularClassId, Long academyStudentId, LocalDate absentDate, MakeupTicketSource source, MakeupTicketStatus status);

    // 🎯 결석 신청 취소: 상태와 무관하게 우선 존재 여부/상태를 확인한 뒤 명확한 에러 메시지를 주기 위함
    Optional<MakeupTicket> findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
            Long regularClassId, Long academyStudentId, LocalDate absentDate, MakeupTicketSource source);
}
