package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.AbsenceRequest;
import com.academy.reschedu.domain.makeup.dto.ManualTicketGrantRequest;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketDetailResponse;
import com.academy.reschedu.domain.makeup.dto.StudentTicketCountResponse;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.domain.member.StudentRepository;
import com.academy.reschedu.domain.notification.NotificationEvent;
import com.academy.reschedu.domain.notification.NotificationType;
import com.academy.reschedu.domain.regularclass.RegularClass;
import com.academy.reschedu.domain.regularclass.RegularClassRepository;
import com.academy.reschedu.domain.regularclass.RegularClassStudentRepository;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MakeupTicketService {

    /** 학원이 아직 보강권 정책을 한 번도 설정한 적 없을 때 쓰이는 기본 유효 기간(일). */
    private static final int DEFAULT_TICKET_VALID_DAYS = 60;

    private final MakeupTicketRepository makeupTicketRepository;
    private final MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    private final MakeupRequestRepository makeupRequestRepository;
    private final RegularClassRepository regularClassRepository;
    private final RegularClassStudentRepository regularClassStudentRepository;
    private final AcademyStudentRepository academyStudentRepository;
    private final StudentRepository studentRepository;
    private final CurrentMemberProvider currentMemberProvider;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결석 처리(신청). 학부모는 본인 자녀에 대해서만, 원장/강사는 소속 학원의 수강생이라면 누구든 처리할 수 있다.
     * 지정한 날짜가 실제로 그 수업의 정규 요일에 해당하고 그 명단에 편성되어 있어야 한다.
     * 같은 회차(수업+날짜)에 대해 이미 결석(휴무일 포함) 처리가 되어 있다면 중복 발급하지 않는다.
     */
    @Transactional
    public UUID requestAbsence(AbsenceRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();

        Student student = studentRepository.findByUuid(request.studentUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수강생입니다."));

        RegularClass regularClass = regularClassRepository.findByUuid(request.regularClassUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정규 수업입니다."));

        validateAbsenceRequester(requester, student, regularClass);

        if (!regularClass.getDaysOfWeek().contains(request.absentDate().getDayOfWeek())) {
            throw new IllegalArgumentException("지정한 날짜는 해당 수업의 정규 요일이 아닙니다.");
        }

        AcademyStudent academyStudent = getAcademyStudentOrThrow(request.studentUuid(), regularClass.getAcademy().getId());

        boolean isEnrolled = regularClassStudentRepository
                .existsByRegularClass_IdAndAcademyStudent_Id(regularClass.getId(), academyStudent.getId());
        if (!isEnrolled) {
            throw new IllegalArgumentException("해당 시간표에 편성되지 않은 수강생입니다.");
        }

        if (!academyStudent.isActiveOn(request.absentDate())) {
            throw new IllegalArgumentException("해당 날짜는 수강 기간에 포함되지 않습니다.");
        }

        if (makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                regularClass.getId(), academyStudent.getId(), request.absentDate())) {
            throw new IllegalStateException("이미 해당 날짜에 대한 결석 처리(보강권 발급)가 완료되었습니다.");
        }

        MakeupTicketPolicy policy = resolvePolicy(regularClass.getAcademy().getId());
        validateIssuanceLimits(academyStudent, policy, 1, requester, request.overrideLimit());

        MakeupTicket ticket = issueTicket(academyStudent, regularClass, request.absentDate(), MakeupTicketSource.STUDENT_ABSENCE);
        return ticket.getUuid();
    }

    /**
     * 결석 신청 취소. 학부모는 본인 자녀에 대해서만, 원장/강사는 소속 학원의 수강생이라면 누구든 처리할 수 있다
     * (권한 규칙은 결석 신청과 동일하게 검증한다). 학원 휴무(ACADEMY_HOLIDAY)로 발급된 티켓은 이 API로 취소할 수
     * 없다 — 그건 휴무일 지정 취소(AcademyHolidayService)를 통해서만 회수되어야 한다.
     */
    @Transactional
    public void cancelAbsence(AbsenceRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();

        Student student = studentRepository.findByUuid(request.studentUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수강생입니다."));

        RegularClass regularClass = regularClassRepository.findByUuid(request.regularClassUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정규 수업입니다."));

        validateAbsenceRequester(requester, student, regularClass);

        AcademyStudent academyStudent = getAcademyStudentOrThrow(request.studentUuid(), regularClass.getAcademy().getId());

        MakeupTicket ticket = makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                        regularClass.getId(), academyStudent.getId(), request.absentDate(), MakeupTicketSource.STUDENT_ABSENCE)
                .orElseThrow(() -> new IllegalArgumentException("취소할 수 있는 결석 신청 내역을 찾을 수 없습니다."));

        if (ticket.getStatus() != MakeupTicketStatus.UNUSED) {
            throw new IllegalStateException("이미 보강 신청에 사용되었거나 만료된 결석 신청은 취소할 수 없습니다.");
        }

        makeupTicketRepository.delete(ticket);
    }

    /**
     * 같은 회차(수업+학생+날짜)에 이미 발급된 티켓이 없을 때만 새로 발급한다. RegularClassService의
     * 휴무일 발급/세션 동기화 경로에서 쓰인다.
     *
     * @return 새로 발급했으면 true, 이미 발급되어 있어 건너뛰었으면 false
     */
    @Transactional
    public boolean issueTicketIfNeeded(AcademyStudent academyStudent, RegularClass regularClass, LocalDate date, MakeupTicketSource source) {
        boolean alreadyIssued = makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                regularClass.getId(), academyStudent.getId(), date);
        if (alreadyIssued) {
            return false;
        }
        issueTicket(academyStudent, regularClass, date, source);
        return true;
    }

    /**
     * 학부모: 본인 자녀에 대해서만 허용.
     * 원장/강사: 자신이 소속된 학원의 수업(=수강생)에 대해서만 허용.
     */
    private void validateAbsenceRequester(Member requester, Student student, RegularClass regularClass) {
        if (requester.getRole() == MemberRole.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(requester.getId())) {
                throw new IllegalStateException("본인의 자녀에 대해서만 결석 처리를 할 수 있습니다.");
            }
            return;
        }
        if (requester.getRole() == MemberRole.ADMIN || requester.getRole() == MemberRole.TEACHER) {
            if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(regularClass.getAcademy().getId())) {
                throw new IllegalStateException("소속 학원의 수업에 대해서만 결석 처리를 할 수 있습니다.");
            }
            return;
        }
        throw new IllegalStateException("결석 처리를 할 수 있는 권한이 없습니다.");
    }

    /** 발급 제한 검증은 호출부(requestAbsence)에서 미리 처리하고, 여기서는 순수하게 티켓만 만든다. */
    private MakeupTicket issueTicket(AcademyStudent academyStudent, RegularClass regularClass, LocalDate absentDate, MakeupTicketSource source) {
        MakeupTicketPolicy policy = resolvePolicy(academyStudent.getAcademy().getId());

        MakeupTicket ticket = MakeupTicket.builder()
                .academyStudent(academyStudent)
                .originClass(regularClass)
                .absentDate(absentDate)
                .source(source)
                .expiredAt(resolveExpiredAt(policy, absentDate))
                .build();
        MakeupTicket saved = makeupTicketRepository.save(ticket);
        notifyParentTicketIssued(academyStudent);
        return saved;
    }

    /** 보강권이 발급되면 그 학생의 학부모에게 실시간 알림(SSE 토스트)을 보낸다. */
    private void notifyParentTicketIssued(AcademyStudent academyStudent) {
        Member parent = academyStudent.getStudent().getParent();
        if (parent == null) {
            return;
        }
        eventPublisher.publishEvent(NotificationEvent.toMember(
                NotificationType.MAKEUP_TICKET_ISSUED,
                academyStudent.getStudent().getName() + " 학생에게 보강권이 발급되었습니다.",
                "/dashboard/makeup-apply",
                parent.getId()
        ));
    }

    /** 이 학원에 설정된 보강권 정책(없으면 null — 모든 항목이 "기본값" 취급된다). */
    private MakeupTicketPolicy resolvePolicy(Long academyId) {
        return makeupTicketPolicyRepository.findByAcademy_Id(academyId).orElse(null);
    }

    /**
     * 발급 시점의 만료 기한을 계산한다. 정책에 defaultValidityDays가 있으면 그 값을, 없으면
     * DEFAULT_TICKET_VALID_DAYS를 쓴다. 정책은 있는데 값이 null이면(명시적 "제한 없음") 만료 없음.
     */
    private LocalDateTime resolveExpiredAt(MakeupTicketPolicy policy, LocalDate anchorDate) {
        Integer validityDays = policy != null ? policy.getDefaultValidityDays() : null;
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        if (policy != null && validityDays == null) {
            return null;
        }
        int days = validityDays != null ? validityDays : DEFAULT_TICKET_VALID_DAYS;
        return anchor.plusDays(days).atStartOfDay();
    }

    /**
     * 발급 제한(최대 보유 개수/월 발급 제한) 검증. 학부모는 초과 시 무조건 차단하고, 원장/강사는
     * overrideLimit=false면 확인을 요구하는 예외를, true면(확인 후 재요청) 통과시킨다.
     */
    private void validateIssuanceLimits(AcademyStudent academyStudent, MakeupTicketPolicy policy, int quantity,
                                         Member requester, boolean overrideLimit) {
        if (policy == null) {
            return;
        }
        String violation = findLimitViolationMessage(academyStudent, policy, quantity);
        if (violation == null) {
            return;
        }
        if (requester.getRole() == MemberRole.PARENT) {
            throw new IllegalStateException(violation);
        }
        if (!overrideLimit) {
            throw new MakeupTicketLimitExceededException(violation);
        }
        // 원장/강사 + overrideLimit=true(확인 후 재요청) → 제한을 알면서도 강행. 통과.
    }

    /** 정책 위반 사유 메시지(없으면 null) — 최대 보유 개수 초과를 먼저, 그 다음 월 발급 제한 초과를 확인한다. */
    private String findLimitViolationMessage(AcademyStudent academyStudent, MakeupTicketPolicy policy, int quantity) {
        if (policy.getMaxOutstandingTickets() != null) {
            long outstanding = countValidUnusedTickets(academyStudent.getId());
            if (outstanding + quantity > policy.getMaxOutstandingTickets()) {
                return String.format(
                        "이 학생이 보유 가능한 보강권 최대 개수(%d개)를 초과하게 됩니다. (현재 보유 %d개, 요청 %d개)",
                        policy.getMaxOutstandingTickets(), outstanding, quantity);
            }
        }
        if (policy.getMonthlyIssueLimit() != null) {
            LocalDate today = LocalDate.now();
            LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
            LocalDateTime monthEnd = today.withDayOfMonth(today.lengthOfMonth()).plusDays(1).atStartOfDay();
            long issuedThisMonth = makeupTicketRepository.countByAcademyStudent_IdAndCreatedAtBetween(
                    academyStudent.getId(), monthStart, monthEnd);
            if (issuedThisMonth + quantity > policy.getMonthlyIssueLimit()) {
                return String.format(
                        "이 학생의 이번 달 발급 가능한 보강권 개수(월 %d개)를 초과하게 됩니다. (이번 달 이미 발급 %d개, 요청 %d개)",
                        policy.getMonthlyIssueLimit(), issuedThisMonth, quantity);
            }
        }
        return null;
    }

    /** 원장/강사 전용: 특정 학생에게 보강권을 수동으로 지급한다(특정 수업/날짜에 묶이지 않음). */
    @Transactional
    public void grantTicketsManually(Long academyId, ManualTicketGrantRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("원장/강사만 보강권을 수동으로 지급할 수 있습니다.");
        }
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 수강생에게만 보강권을 지급할 수 있습니다.");
        }

        AcademyStudent academyStudent = getAcademyStudentOrThrow(request.studentUuid(), academyId);

        MakeupTicketPolicy policy = resolvePolicy(academyId);
        validateIssuanceLimits(academyStudent, policy, request.quantity(), requester, request.overrideLimit());

        String memo = request.memo() != null && !request.memo().isBlank() ? request.memo().trim() : null;
        // validityDays가 오면 그 값을, null이면 기한 제한 없음을 그대로 적용한다.
        LocalDateTime expiredAt = request.validityDays() != null
                ? LocalDate.now().plusDays(request.validityDays()).atStartOfDay()
                : null;

        for (int i = 0; i < request.quantity(); i++) {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent)
                    .originClass(null)
                    .absentDate(null)
                    .source(MakeupTicketSource.MANUAL_GRANT)
                    .expiredAt(expiredAt)
                    .memo(memo)
                    .build();
            makeupTicketRepository.save(ticket);
        }
        notifyParentTicketIssued(academyStudent);
    }

    /**
     * 보강 매칭 센터용: 학원 소속 전체 수강생의 잔여(미사용) 보강권 개수를 집계한다.
     */
    public List<StudentTicketCountResponse> getRemainingTicketCounts(Long academyId) {
        validateRequesterBelongsToAcademy(academyId);

        return academyStudentRepository.findByAcademyId(academyId).stream()
                .filter(AcademyStudent::isApproved) // 승인 대기 중인 등록 건은 보강 매칭 대상에서 제외한다.
                .map(academyStudent -> new StudentTicketCountResponse(
                        academyStudent.getStudent().getUuid(),
                        academyStudent.getStudent().getName(),
                        academyStudent.getManagementName(),
                        countValidUnusedTickets(academyStudent.getId())
                ))
                .toList();
    }

    /**
     * 학부모 전용: 본인 자녀들의 지금 새로 보강 신청에 쓸 수 있는 보강권 개수를 조회한다.
     * 이미 다른 대기/수락된 보강 신청에 묶여 있는 티켓은 제외하고 센다.
     */
    public List<StudentTicketCountResponse> getMyChildrenTicketCounts(Long academyId) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }

        return studentRepository.findByParentId(parent.getId()).stream()
                .flatMap(child -> academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), academyId).stream())
                .map(academyStudent -> {
                    long availableCount = makeupTicketRepository
                            .findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(academyStudent.getId(), MakeupTicketStatus.UNUSED)
                            .stream()
                            .filter(MakeupTicket::isCurrentlyValid)
                            .filter(ticket -> !makeupRequestRepository.existsByTicket_IdAndStatusIn(
                                    ticket.getId(), List.of(MakeupRequestStatus.PENDING, MakeupRequestStatus.APPROVED)))
                            .count();
                    return new StudentTicketCountResponse(
                            academyStudent.getStudent().getUuid(),
                            academyStudent.getStudent().getName(),
                            academyStudent.getManagementName(),
                            availableCount
                    );
                })
                .toList();
    }

    /** 원장/강사 전용: 특정 학생의 보강권 전체 이력(미사용/사용/만료 포함)을 최신순으로 반환한다. */
    public List<MakeupTicketDetailResponse> getTicketDetails(Long academyId, UUID studentUuid) {
        validateRequesterBelongsToAcademy(academyId);
        return ticketHistory(getAcademyStudentOrThrow(studentUuid, academyId));
    }

    /** 학부모 전용: 본인 자녀 한 명의 보강권 전체 이력을 최신순으로 반환한다. */
    public List<MakeupTicketDetailResponse> getMyChildTicketHistory(Long academyId, UUID studentUuid) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }

        Student student = studentRepository.findByUuid(studentUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수강생입니다."));
        if (student.getParent() == null || !student.getParent().getId().equals(parent.getId())) {
            throw new IllegalStateException("본인 자녀의 보강권 이력만 조회할 수 있습니다.");
        }

        return ticketHistory(getAcademyStudentOrThrow(studentUuid, academyId));
    }

    private List<MakeupTicketDetailResponse> ticketHistory(AcademyStudent academyStudent) {
        return makeupTicketRepository.findByAcademyStudent_IdOrderByCreatedAtDesc(academyStudent.getId()).stream()
                .map(MakeupTicketDetailResponse::from)
                .toList();
    }

    /**
     * 휴무일 지정이 취소될 때, 그 휴무로 발급된 미사용 티켓만 찾아 회수(삭제)한다. 이미 사용된 티켓은 건드리지 않는다.
     *
     * @return 실제로 회수했으면 true, 없으면 false
     */
    @Transactional
    public boolean retractHolidayTicketIfUnused(AcademyStudent academyStudent, RegularClass regularClass, LocalDate date) {
        return makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSourceAndStatus(
                        regularClass.getId(), academyStudent.getId(), date, MakeupTicketSource.ACADEMY_HOLIDAY, MakeupTicketStatus.UNUSED)
                .map(ticket -> {
                    makeupTicketRepository.delete(ticket);
                    return true;
                })
                .orElse(false);
    }

    private AcademyStudent getAcademyStudentOrThrow(UUID studentUuid, Long academyId) {
        return academyStudentRepository.findByStudentUuidAndAcademyId(studentUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 학원에 등록되지 않은 수강생입니다."));
    }

    private Member validateRequesterBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 보강권 현황만 조회할 수 있습니다.");
        }
        return requester;
    }

    /** 유효기간이 지나지 않은 미사용 보강권 개수. */
    private long countValidUnusedTickets(Long academyStudentId) {
        return makeupTicketRepository
                .findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(academyStudentId, MakeupTicketStatus.UNUSED)
                .stream()
                .filter(MakeupTicket::isCurrentlyValid)
                .count();
    }
}
