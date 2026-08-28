package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyHoliday;
import com.academy.reschedu.domain.academy.AcademyHolidayRepository;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.makeup.MakeupRequestRepository;
import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.makeup.MakeupTicketRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketService;
import com.academy.reschedu.domain.makeup.MakeupTicketSource;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRepository;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.domain.member.StudentRepository;
import com.academy.reschedu.domain.makeup.dto.MakeupSlotResponse;
import com.academy.reschedu.domain.regularclass.dto.ChangeClassTeacherRequest;
import com.academy.reschedu.domain.regularclass.dto.NextClassResponse;
import com.academy.reschedu.domain.regularclass.dto.RegularClassCreateRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassDiscontinueRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassResponse;
import com.academy.reschedu.domain.regularclass.dto.RegularClassUpdateRequest;
import com.academy.reschedu.domain.regularclass.dto.RosterStudentResponse;
import com.academy.reschedu.domain.regularclass.dto.ScheduleAssignmentRequest;
import com.academy.reschedu.domain.regularclass.dto.ScheduleChangeRequest;
import com.academy.reschedu.domain.regularclass.dto.ScheduleHistoryResponse;
import com.academy.reschedu.domain.regularclass.dto.ScheduleSummary;
import com.academy.reschedu.domain.regularclass.dto.SmartScheduleAssignmentRequest;
import com.academy.reschedu.domain.regularclass.dto.TimeSlotRequest;
import com.academy.reschedu.domain.regularclass.dto.TimeSlotResponse;
import com.academy.reschedu.domain.regularclass.dto.WeeklyOccurrenceResponse;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegularClassService {

    private final RegularClassRepository regularClassRepository;
    private final RegularClassStudentRepository regularClassStudentRepository;
    private final RegularClassSessionRepository regularClassSessionRepository;
    private final RegularClassSessionStudentRepository regularClassSessionStudentRepository;
    private final AcademyRepository academyRepository;
    private final AcademyStudentRepository academyStudentRepository;
    private final AcademyHolidayRepository academyHolidayRepository;
    private final MakeupTicketRepository makeupTicketRepository;
    private final MakeupRequestRepository makeupRequestRepository;
    private final MakeupTicketService makeupTicketService;
    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final CurrentMemberProvider currentMemberProvider;

    /** 요일별로 정규 수업을 등록한다. 같은 강사의 동일 요일·시간 수업이 있으면 합류시키고, 없으면 새로 만든다. */
    @Transactional
    public List<UUID> createRegularClass(Long academyId, RegularClassCreateRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        Academy academy = getAcademyOrThrow(academyId);
        Member teacher = getVerifiedTeacher(academy, request.teacherUuid());

        List<RegularClass> teacherClasses = regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, request.teacherUuid());

        List<UUID> resultUuids = new ArrayList<>();
        for (TimeSlotRequest slot : request.timeSlots()) {
            RegularClass regularClass = findMatchingClass(teacherClasses, slot);
            if (regularClass == null) {
                regularClass = RegularClass.builder()
                        .academy(academy)
                        .title(blankToNull(request.title()))
                        .teacher(teacher)
                        .roomNumber(blankToNull(request.roomNumber()))
                        .maxCapacity(request.maxCapacity())
                        .timeSlots(buildTimeSlots(List.of(slot)))
                        .build();
                regularClassRepository.save(regularClass);
                teacherClasses.add(regularClass);
            }

            if (request.studentUuids() != null) {
                for (UUID studentUuid : request.studentUuids()) {
                    enrollStudent(regularClass, academy, studentUuid);
                }
            }
            resultUuids.add(regularClass.getUuid());
        }

        return resultUuids;
    }

    /** teacherClasses 중 요일·시작·종료 시각이 슬롯과 정확히 일치하는 수업을 찾는다(없으면 null). */
    private RegularClass findMatchingClass(List<RegularClass> teacherClasses, TimeSlotRequest slot) {
        return teacherClasses.stream()
                .filter(rc -> rc.getTimeSlotFor(slot.dayOfWeek())
                        .filter(ts -> ts.getStartTime().equals(slot.startTime()) && ts.getEndTime().equals(slot.endTime()))
                        .isPresent())
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void updateRegularClass(Long academyId, UUID classUuid, RegularClassUpdateRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        // 한 번에 하나의 요일·시간만 수정 가능. 다른 요일은 [시간표 추가]로 별도 등록한다.
        if (request.timeSlots().size() != 1) {
            throw new IllegalArgumentException("정규 수업 수정은 한 번에 하나의 요일만 가능합니다. 다른 요일은 별도로 새로 추가해주세요.");
        }
        Set<RegularClassTimeSlot> timeSlots = buildTimeSlots(request.timeSlots());

        Academy academy = getAcademyOrThrow(academyId);

        RegularClass regularClass = regularClassRepository.findByUuidAndAcademyId(classUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시간표입니다. uuid=" + classUuid));

        Member teacher = getVerifiedTeacher(academy, request.teacherUuid());

        List<UUID> desired = request.studentUuids() != null ? request.studentUuids() : List.of();
        Set<UUID> desiredSet = new HashSet<>(desired);
        // 현재 활성 로스터만 대상으로 한다(체크박스 전체 교체).
        List<RegularClassStudent> current = regularClassStudentRepository.findByRegularClass_Id(regularClass.getId()).stream()
                .filter(rcs -> rcs.isActiveOn(LocalDate.now()))
                .toList();
        Set<UUID> currentStudentUuids = new HashSet<>();
        for (RegularClassStudent enrollment : current) {
            currentStudentUuids.add(enrollment.getAcademyStudent().getStudent().getUuid());
        }

        // 순서: (1) 제거 → (2) 정원 등 기본 정보 갱신 → (3) 추가. 정원 검증을 정확히 하려면 이 순서가 필요하다.
        for (RegularClassStudent enrollment : current) {
            UUID studentUuid = enrollment.getAcademyStudent().getStudent().getUuid();
            if (!desiredSet.contains(studentUuid)) {
                AcademyStudent removedStudent = enrollment.getAcademyStudent();
                regularClassStudentRepository.delete(enrollment);
                removeStudentFromAllSessions(regularClass, removedStudent);
            }
        }

        // 정원 축소 검증은 오늘 기준 활성 인원으로 판단한다.
        long activeCountAfterRemoval = countActiveRoster(regularClass.getId(), LocalDate.now());
        if (request.maxCapacity() < activeCountAfterRemoval) {
            throw new IllegalStateException(String.format(
                    "현재 수강 기간이 유효한 인원(%d명)보다 작게 정원을 설정할 수 없습니다.", activeCountAfterRemoval));
        }

        regularClass.update(
                blankToNull(request.title()),
                teacher,
                blankToNull(request.roomNumber()),
                request.maxCapacity(),
                timeSlots
        );

        // 이미 생성된 회차는 정원을 스냅샷해 뒀으므로 템플릿 갱신과 별개로 모든 회차에 새 정원을 반영한다.
        regularClassSessionRepository.findByRegularClass_Id(regularClass.getId())
                .forEach(session -> session.updateMaxCapacity(request.maxCapacity()));

        for (UUID studentUuid : desired) {
            if (!currentStudentUuids.contains(studentUuid)) {
                enrollStudent(regularClass, academy, studentUuid);
            }
        }
    }

    /**
     * 담당 강사 변경(효력일 지정). 기존 반은 그대로 두고, effectiveFrom부터 현재 로스터 전원을 새 강사의
     * 같은 요일·시간 반으로 이관한다(없으면 새로 만든다). effectiveFrom 전날까지의 기록은 기존 반에 남는다.
     *
     * @return 이관된 학생들의 새 배정(정규 수업 uuid) 목록
     */
    @Transactional
    public List<UUID> changeClassTeacher(Long academyId, UUID classUuid, ChangeClassTeacherRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        Academy academy = getAcademyOrThrow(academyId);
        RegularClass fromClass = getRegularClassOrThrow(academyId, classUuid);
        Member newTeacher = getVerifiedTeacher(academy, request.newTeacherUuid());
        if (newTeacher.getId().equals(fromClass.getTeacher().getId())) {
            throw new IllegalArgumentException("이미 담당 중인 강사입니다.");
        }

        RegularClassTimeSlot slot = fromClass.getTimeSlots().iterator().next();
        TimeSlotRequest slotRequest = new TimeSlotRequest(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());

        List<RegularClass> newTeacherClasses = regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, request.newTeacherUuid());
        RegularClass toClass = findMatchingClass(newTeacherClasses, slotRequest);
        if (toClass == null) {
            List<UUID> created = createRegularClass(academyId, new RegularClassCreateRequest(
                    fromClass.getTitle(), request.newTeacherUuid(), fromClass.getRoomNumber(), fromClass.getMaxCapacity(),
                    List.of(slotRequest), List.of()));
            toClass = regularClassRepository.findByUuid(created.get(0))
                    .orElseThrow(() -> new IllegalStateException("새 강사 반 생성에 실패했습니다."));
        }

        LocalDate endDate = request.effectiveFrom().minusDays(1);
        // 현재 로스터 = 아직 종료 처리(endOn)되지 않은 배정(endDate == null). 미래 시작 예정 배정도 포함한다.
        List<RegularClassStudent> activeRoster = regularClassStudentRepository.findByRegularClass_Id(fromClass.getId()).stream()
                .filter(rcs -> rcs.getEndDate() == null)
                .toList();

        List<UUID> resultUuids = new ArrayList<>();
        for (RegularClassStudent enrollment : activeRoster) {
            AcademyStudent academyStudent = enrollment.getAcademyStudent();
            endActiveEnrollment(academyStudent, fromClass, endDate);
            UUID assigned = assignStudentSchedule(academyId, new ScheduleAssignmentRequest(
                    academyStudent.getStudent().getUuid(), toClass.getUuid(), request.effectiveFrom(), null));
            resultUuids.add(assigned);

            // [수강생 관리] 담당 강사는 즉시 바뀌고, 기존 강사는 effectiveFrom 전날까지 previousTeacher로 목록에 남는다.
            if (academyStudent.getTeacher() != null && academyStudent.getTeacher().getId().equals(fromClass.getTeacher().getId())) {
                academyStudent.scheduleTeacherHandover(academyStudent.getTeacher(), newTeacher, request.effectiveFrom());
            }
        }

        return resultUuids;
    }

    /**
     * 반 종료(효력일 지정). effectiveFrom부터 이 반을 더 이상 진행하지 않고, 배정된 학생들의 로스터도
     * effectiveFrom 전날로 종료 처리한다(다른 반으로 자동 이관하지 않는다).
     */
    @Transactional
    public void discontinueRegularClass(Long academyId, UUID classUuid, RegularClassDiscontinueRequest request) {
        validateRequesterBelongsToAcademy(academyId);
        RegularClass regularClass = getRegularClassOrThrow(academyId, classUuid);

        LocalDate endDate = request.effectiveFrom().minusDays(1);
        List<RegularClassStudent> openEnrollments = regularClassStudentRepository.findByRegularClass_Id(regularClass.getId()).stream()
                .filter(rcs -> rcs.getEndDate() == null)
                .toList();
        for (RegularClassStudent enrollment : openEnrollments) {
            // 아직 시작 전인 배정은 endOn 대신 삭제한다(종료일<시작일 이력 방지).
            if (enrollment.getStartDate() != null && enrollment.getStartDate().isAfter(endDate)) {
                enrollment.endOn(enrollment.getStartDate().minusDays(1));
                syncStudentAcrossSessions(enrollment);
                regularClassStudentRepository.delete(enrollment);
            } else {
                enrollment.endOn(endDate);
                syncStudentAcrossSessions(enrollment);
            }
        }

        regularClass.discontinue(request.effectiveFrom());
    }

    /** 반 완전 삭제. 학생 배정/보강권/보강 신청 이력이 하나라도 있으면 거부한다(그 경우 discontinueRegularClass 사용). */
    @Transactional
    public void deleteRegularClass(Long academyId, UUID classUuid) {
        validateRequesterBelongsToAcademy(academyId);
        RegularClass regularClass = getRegularClassOrThrow(academyId, classUuid);

        if (!regularClassStudentRepository.findByRegularClass_Id(regularClass.getId()).isEmpty()) {
            throw new IllegalStateException("학생이 배정된 적 있는 반은 완전히 삭제할 수 없습니다. [반 종료]를 이용해주세요.");
        }
        if (makeupTicketRepository.existsByOriginClass_Id(regularClass.getId())) {
            throw new IllegalStateException("보강권 발급 이력이 있는 반은 완전히 삭제할 수 없습니다. [반 종료]를 이용해주세요.");
        }
        if (makeupRequestRepository.existsByTargetRegularClass_Id(regularClass.getId())) {
            throw new IllegalStateException("보강 신청 이력이 있는 반은 완전히 삭제할 수 없습니다. [반 종료]를 이용해주세요.");
        }

        List<RegularClassSession> sessions = regularClassSessionRepository.findByRegularClass_Id(regularClass.getId());
        if (!sessions.isEmpty()) {
            List<Long> sessionIds = sessions.stream().map(RegularClassSession::getId).toList();
            List<RegularClassSessionStudent> lingeringSessionStudents = regularClassSessionStudentRepository.findBySession_IdIn(sessionIds);
            if (!lingeringSessionStudents.isEmpty()) {
                regularClassSessionStudentRepository.deleteAll(lingeringSessionStudents);
            }
            regularClassSessionRepository.deleteAll(sessions);
        }
        regularClassRepository.delete(regularClass);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Academy getAcademyOrThrow(Long academyId) {
        return academyRepository.findById(academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원입니다."));
    }

    private RegularClass getRegularClassOrThrow(Long academyId, UUID classUuid) {
        return regularClassRepository.findByUuidAndAcademyId(classUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시간표입니다."));
    }

    private AcademyStudent getAcademyStudentOrThrow(Long academyId, UUID studentUuid) {
        return academyStudentRepository.findByStudentUuidAndAcademyId(studentUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 학원에 등록되지 않은 수강생입니다."));
    }

    /** teacherUuid가 해당 학원 소속 강사인지 검증하고 반환한다. */
    private Member getVerifiedTeacher(Academy academy, UUID teacherUuid) {
        Member teacher = memberRepository.findByUuid(teacherUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 강사입니다."));
        if (teacher.getRole() != MemberRole.TEACHER || !isSameAcademy(teacher.getAcademy(), academy)) {
            throw new IllegalArgumentException("해당 학원 소속 강사만 담당 강사로 지정할 수 있습니다.");
        }
        return teacher;
    }

    /** 요일 중복 없이 각 슬롯의 시작<종료 시각을 검증하고 값 객체 집합으로 변환한다. */
    private Set<RegularClassTimeSlot> buildTimeSlots(List<TimeSlotRequest> requests) {
        Set<DayOfWeek> seenDays = new HashSet<>();
        Set<RegularClassTimeSlot> timeSlots = new LinkedHashSet<>();
        for (TimeSlotRequest slotRequest : requests) {
            if (!slotRequest.startTime().isBefore(slotRequest.endTime())) {
                throw new IllegalArgumentException(
                        String.format("%s요일의 종료 시간은 시작 시간보다 이후여야 합니다.", slotRequest.dayOfWeek()));
            }
            if (!seenDays.add(slotRequest.dayOfWeek())) {
                throw new IllegalArgumentException(
                        String.format("%s요일의 시간이 중복 입력되었습니다.", slotRequest.dayOfWeek()));
            }
            timeSlots.add(new RegularClassTimeSlot(slotRequest.dayOfWeek(), slotRequest.startTime(), slotRequest.endTime()));
        }
        return timeSlots;
    }

    private void enrollStudent(RegularClass regularClass, Academy academy, UUID studentUuid) {
        AcademyStudent academyStudent = academyStudentRepository.findByStudentUuidAndAcademyId(studentUuid, academy.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 학원에 등록되지 않은 수강생입니다. uuid=" + studentUuid));

        if (!academyStudent.isApproved()) {
            throw new IllegalStateException("가입 승인이 완료되지 않은 수강생은 시간표에 등록할 수 없습니다. (" + academyStudent.getStudent().getName() + ")");
        }

        // 완전히 종료된 과거 이력과는 겹쳐도 되지만, 아직 끝나지 않은 배정과는 겹치면 안 된다.
        LocalDate today = LocalDate.now();
        boolean hasUnterminatedConflict = regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(academyStudent.getId()).stream()
                .filter(rcs -> rcs.getRegularClass().getId().equals(regularClass.getId()))
                .anyMatch(rcs -> rcs.getEndDate() == null || !rcs.getEndDate().isBefore(today));
        if (hasUnterminatedConflict) {
            throw new IllegalStateException("이미 해당 시간표에 등록된 수강생입니다. (" + academyStudent.getStudent().getName() + ")");
        }

        // 정원은 오늘 기준 활성 인원만 대상으로 산정한다.
        if (academyStudent.isActiveOn(today)) {
            long activeCountToday = countActiveRoster(regularClass.getId(), today);
            if (activeCountToday >= regularClass.getMaxCapacity()) {
                throw new IllegalStateException(String.format(
                        "정규반 정원을 초과할 수 없습니다. (정원: %d명, 현재 수강 기간이 유효한 인원: %d명)",
                        regularClass.getMaxCapacity(), activeCountToday));
            }
        }

        RegularClassStudent enrollment = new RegularClassStudent(regularClass, academyStudent);
        regularClassStudentRepository.save(enrollment);
        syncStudentAcrossSessions(enrollment);
    }

    /** 정규 수업 로스터 중, 기준 날짜에 수강 기간이 유효한 인원 수. */
    private long countActiveRoster(Long regularClassId, LocalDate referenceDate) {
        return regularClassStudentRepository.findByRegularClass_Id(regularClassId).stream()
                .filter(rcs -> rcs.getAcademyStudent().isActiveOn(referenceDate) && rcs.isActiveOn(referenceDate))
                .count();
    }

    /** 이 반의 가장 이른 배정 시작일. 배정 이력이 없으면(신규 반) LocalDate.MIN — "항상 시작된 것"으로 취급. */
    private LocalDate computeEarliestStart(RegularClass regularClass) {
        return regularClassStudentRepository.findByRegularClass_Id(regularClass.getId()).stream()
                .map(rcs -> rcs.getStartDate() == null ? LocalDate.MIN : rcs.getStartDate())
                .min(Comparator.naturalOrder())
                .orElse(LocalDate.MIN);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 날짜별 개별 스케줄 인스턴스(RegularClassSession) 관리
    // ═══════════════════════════════════════════════════════════════════════

    /** 이미 생성된 회차면 그대로, 없으면 그 시점의 템플릿 로스터·정원·휴무 상태를 스냅샷해 생성한다. */
    private RegularClassSession ensureSession(RegularClass regularClass, LocalDate date) {
        return syncSessionForDate(regularClass, date).session();
    }

    /** MakeupRequestService 전용 진입점: 보강 신청 생성/수락 시 대상 회차를 (없으면 생성하며) 확보한다. */
    @Transactional
    public RegularClassSession ensureSessionForMakeupBooking(RegularClass regularClass, LocalDate date) {
        return ensureSession(regularClass, date);
    }

    /**
     * 학원 휴무일 등록 시 호출. 그 요일의 모든 정규 수업에 대해 회차를 확보(생성/휴무 갱신)하고
     * 각 회차 로스터 전원에게 보강권을 발급한다.
     *
     * @return 이번 호출로 새로 발급된 보강권 개수
     */
    @Transactional
    public int applyHolidayToSessions(Academy academy, LocalDate date) {
        List<RegularClass> affectedClasses = regularClassRepository.findByAcademyId(academy.getId()).stream()
                .filter(regularClass -> regularClass.getDaysOfWeek().contains(date.getDayOfWeek()))
                .toList();

        int issuedCount = 0;
        for (RegularClass regularClass : affectedClasses) {
            issuedCount += syncSessionForDate(regularClass, date).issuedTicketCount();
        }
        return issuedCount;
    }

    /**
     * 학원 휴무일 지정이 취소될 때 호출. 그 요일의 모든 회차를 정규 상태로 복원하고, 휴무로 발급된
     * 보강권을 회수한다 — 미사용 티켓은 삭제하고, 이미 다른 반에 매칭까지 걸어둔 티켓은 그 매칭의
     * 대상 날짜가 아직 미래인 경우에 한해 매칭을 취소하고 미사용으로 되돌린다.
     *
     * @return 실제로 회수(취소 포함)된 보강권 개수
     */
    @Transactional
    public int revertHolidayForSessions(Academy academy, LocalDate date, Member actor) {
        List<RegularClass> affectedClasses = regularClassRepository.findByAcademyId(academy.getId()).stream()
                .filter(regularClass -> regularClass.getDaysOfWeek().contains(date.getDayOfWeek()))
                .toList();

        int retractedCount = 0;
        for (RegularClass regularClass : affectedClasses) {
            Optional<RegularClassSession> sessionOpt =
                    regularClassSessionRepository.findByRegularClass_IdAndDate(regularClass.getId(), date);
            if (sessionOpt.isEmpty() || !sessionOpt.get().isHolidayCancelled()) {
                continue; // 회차 미생성이거나 이미 정상 상태면 되돌릴 게 없다.
            }

            RegularClassSession session = sessionOpt.get();
            session.revertHolidayCancelled();

            for (RegularClassSessionStudent rcss : regularClassSessionStudentRepository.findBySession_Id(session.getId())) {
                if (makeupTicketService.retractHolidayTicket(rcss.getAcademyStudent(), regularClass, date, actor)) {
                    retractedCount++;
                }
            }
        }
        return retractedCount;
    }

    /**
     * 세션을 (없으면 생성하며) 최신 휴무 상태로 맞추고, 이번 호출로 새로 확정된 휴무이면서 그 휴무가
     * 보강권 발급 대상(AcademyHoliday.issueMakeupTickets)이면 로스터 전원에게 보강권을 발급한다.
     * 조회 경로와 휴무일 등록 경로가 공유하는 핵심 로직.
     */
    private SessionSyncResult syncSessionForDate(RegularClass regularClass, LocalDate date) {
        Optional<AcademyHoliday> holiday = academyHolidayRepository.findByAcademyIdAndDate(regularClass.getAcademy().getId(), date);
        boolean holidayExists = holiday.isPresent();
        boolean issueTickets = holiday.map(AcademyHoliday::isIssueMakeupTickets).orElse(false);
        Optional<RegularClassSession> existing = regularClassSessionRepository.findByRegularClass_IdAndDate(regularClass.getId(), date);

        if (existing.isEmpty()) {
            RegularClassTimeSlot timeSlot = regularClass.getTimeSlotFor(date.getDayOfWeek())
                    .orElseThrow(() -> new IllegalStateException(
                            "이 반은 " + date.getDayOfWeek() + "요일에 시간이 지정되어 있지 않습니다."));
            RegularClassSession session = RegularClassSession.builder()
                    .regularClass(regularClass)
                    .date(date)
                    .title(regularClass.getTitle())
                    .roomNumber(regularClass.getRoomNumber())
                    .startTime(timeSlot.getStartTime())
                    .endTime(timeSlot.getEndTime())
                    .maxCapacity(regularClass.getMaxCapacity())
                    .holidayCancelled(holidayExists)
                    .build();
            regularClassSessionRepository.save(session);

            int issuedCount = 0;
            List<RegularClassStudent> templateRoster = regularClassStudentRepository.findByRegularClass_Id(regularClass.getId());
            for (RegularClassStudent enrollment : templateRoster) {
                AcademyStudent academyStudent = enrollment.getAcademyStudent();
                if (!academyStudent.isActiveOn(date) || !enrollment.isActiveOn(date)) {
                    continue;
                }
                regularClassSessionStudentRepository.save(new RegularClassSessionStudent(session, academyStudent, false));
                if (issueTickets && makeupTicketService.issueTicketIfNeeded(academyStudent, regularClass, date, MakeupTicketSource.ACADEMY_HOLIDAY)) {
                    issuedCount++;
                }
            }
            return new SessionSyncResult(session, issuedCount);
        }

        RegularClassSession session = existing.get();
        if (!holidayExists || session.isHolidayCancelled()) {
            return new SessionSyncResult(session, 0);
        }

        // 이미 생성된 회차가 뒤늦게 휴무일로 지정된 경우 — 지금 확정하고, 발급 대상이면 즉시 발급한다.
        session.markHolidayCancelled();
        int issuedCount = 0;
        for (RegularClassSessionStudent rcss : regularClassSessionStudentRepository.findBySession_Id(session.getId())) {
            if (issueTickets && makeupTicketService.issueTicketIfNeeded(rcss.getAcademyStudent(), regularClass, date, MakeupTicketSource.ACADEMY_HOLIDAY)) {
                issuedCount++;
            }
        }
        return new SessionSyncResult(session, issuedCount);
    }

    private record SessionSyncResult(RegularClassSession session, int issuedTicketCount) {
    }

    /**
     * 한 주 안의 후보 날짜들에 대해 회차를 확보할 때, 날짜별 휴무일 존재 여부와 세션 존재 여부를
     * 주 범위 전체에서 한 번씩만 벌크 조회해 재사용한다. 세션이 없거나 뒤늦게 휴무로 확정된
     * 날짜만 실제로 syncSessionForDate를 호출하고, 나머지는 조회만으로 끝난다.
     */
    private Map<LocalDate, RegularClassSession> ensureSessionsForWeek(
            RegularClass regularClass, LocalDate weekStart, LocalDate weekEnd, List<LocalDate> candidateDates) {
        Set<LocalDate> holidayDates = academyHolidayRepository
                .findByAcademyIdAndDateBetweenOrderByDateAsc(regularClass.getAcademy().getId(), weekStart, weekEnd).stream()
                .map(AcademyHoliday::getDate)
                .collect(Collectors.toSet());
        Map<LocalDate, RegularClassSession> existingSessions = regularClassSessionRepository
                .findByRegularClass_IdAndDateBetween(regularClass.getId(), weekStart, weekEnd).stream()
                .collect(Collectors.toMap(RegularClassSession::getDate, s -> s));

        Map<LocalDate, RegularClassSession> sessionsByDate = new HashMap<>();
        for (LocalDate date : candidateDates) {
            RegularClassSession existing = existingSessions.get(date);
            boolean needsSync = existing == null || (holidayDates.contains(date) && !existing.isHolidayCancelled());
            sessionsByDate.put(date, needsSync ? syncSessionForDate(regularClass, date).session() : existing);
        }
        return sessionsByDate;
    }

    /**
     * 학생이 새로 로스터에 편성됐을 때, 이미 생성된 모든 회차에 대해 "이 날짜에 유효한가"를 다시
     * 판단해 세션 로스터를 맞춘다. 이미 휴무로 확정된 회차라면 그 자리에서 보강권을 발급한다.
     */
    private void syncStudentAcrossSessions(RegularClassStudent enrollment) {
        RegularClass regularClass = enrollment.getRegularClass();
        AcademyStudent academyStudent = enrollment.getAcademyStudent();
        for (RegularClassSession session : regularClassSessionRepository.findByRegularClass_Id(regularClass.getId())) {
            boolean shouldBeIncluded = academyStudent.isActiveOn(session.getDate()) && enrollment.isActiveOn(session.getDate());
            boolean alreadyIncluded = regularClassSessionStudentRepository
                    .existsBySession_IdAndAcademyStudent_Id(session.getId(), academyStudent.getId());

            if (shouldBeIncluded && !alreadyIncluded) {
                regularClassSessionStudentRepository.save(new RegularClassSessionStudent(session, academyStudent, false));
                if (session.isHolidayCancelled()) {
                    makeupTicketService.issueTicketIfNeeded(academyStudent, regularClass, session.getDate(), MakeupTicketSource.ACADEMY_HOLIDAY);
                }
            } else if (!shouldBeIncluded && alreadyIncluded) {
                regularClassSessionStudentRepository.deleteBySession_IdAndAcademyStudent_Id(session.getId(), academyStudent.getId());
            }
        }
    }

    /** 수강 기간 변경 시, 이 학생이 편성된 모든 정규 수업의 기존 회차를 다시 동기화한다. */
    @Transactional
    public void syncSessionsForStudentPeriodChange(AcademyStudent academyStudent) {
        for (RegularClassStudent enrollment : regularClassStudentRepository.findByAcademyStudent_Id(academyStudent.getId())) {
            syncStudentAcrossSessions(enrollment);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // [수강생 관리] 기간별 요일 변경 / 수강 히스토리
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 학생을 정규 수업에 기간을 지정해 배정한다. [시간표 관리]의 체크박스 전체 교체(enrollStudent)와
     * 달리, 같은 반이라도 기간이 겹치지 않으면 종료된 이력 위에 새로 추가할 수 있다 — 이 이력이
     * 수강 히스토리의 데이터 소스가 된다.
     */
    @Transactional
    public UUID assignStudentSchedule(Long academyId, ScheduleAssignmentRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        RegularClass regularClass = getRegularClassOrThrow(academyId, request.regularClassUuid());
        AcademyStudent academyStudent = getAcademyStudentOrThrow(academyId, request.studentUuid());

        if (!academyStudent.isApproved()) {
            throw new IllegalStateException("가입 승인이 완료되지 않은 수강생은 시간표에 등록할 수 없습니다.");
        }
        if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate())) {
            throw new IllegalArgumentException("종료일은 시작일보다 이후여야 합니다.");
        }

        // 같은 반에 겹치는 기간으로 중복 배정되지 않도록 검증한다(DB 유니크 제약 대신 서비스 계층에서 처리).
        boolean overlaps = regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(academyStudent.getId()).stream()
                .filter(rcs -> rcs.getRegularClass().getId().equals(regularClass.getId()))
                .anyMatch(rcs -> periodsOverlap(rcs.getStartDate(), rcs.getEndDate(), request.startDate(), request.endDate()));
        if (overlaps) {
            throw new IllegalStateException("이미 해당 기간에 같은 반으로 배정된 이력이 있습니다.");
        }

        LocalDate today = LocalDate.now();
        boolean effectiveToday = (request.startDate() == null || !request.startDate().isAfter(today))
                && (request.endDate() == null || !request.endDate().isBefore(today));
        if (effectiveToday && academyStudent.isActiveOn(today)) {
            long activeCountToday = countActiveRoster(regularClass.getId(), today);
            if (activeCountToday >= regularClass.getMaxCapacity()) {
                throw new IllegalStateException(String.format(
                        "정규반 정원을 초과할 수 없습니다. (정원: %d명, 현재 수강 기간이 유효한 인원: %d명)",
                        regularClass.getMaxCapacity(), activeCountToday));
            }
        }

        RegularClassStudent enrollment = new RegularClassStudent(regularClass, academyStudent, request.startDate(), request.endDate());
        regularClassStudentRepository.save(enrollment);
        syncStudentAcrossSessions(enrollment);

        return regularClass.getUuid();
    }

    /** 요일 변경: 기존 반 배정을 특정 날짜로 종료하고 동시에 새 반으로 배정을 시작한다(원자적 처리). */
    @Transactional
    public void changeStudentSchedule(Long academyId, ScheduleChangeRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        AcademyStudent academyStudent = getAcademyStudentOrThrow(academyId, request.studentUuid());

        if (request.fromRegularClassUuid() != null) {
            RegularClass fromClass = regularClassRepository.findByUuidAndAcademyId(request.fromRegularClassUuid(), academyId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기존 시간표입니다."));
            endActiveEnrollment(academyStudent, fromClass, request.effectiveFrom().minusDays(1));
        }

        assignStudentSchedule(academyId, new ScheduleAssignmentRequest(
                request.studentUuid(), request.toRegularClassUuid(), request.effectiveFrom(), null));
    }

    /** 이 반에 대한 학생의 현재 배정(endDate == null, 미래 시작 포함)을 주어진 날짜로 종료 처리한다. */
    private void endActiveEnrollment(AcademyStudent academyStudent, RegularClass regularClass, LocalDate endDate) {
        RegularClassStudent activeEnrollment = regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(academyStudent.getId()).stream()
                .filter(rcs -> rcs.getRegularClass().getId().equals(regularClass.getId()) && rcs.getEndDate() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("종료할 기존 배정을 찾을 수 없습니다."));

        // 아직 시작 전인 배정이 다른 인계로 덮이는 경우 — 종료일<시작일 이력을 남기지 않도록 삭제한다.
        if (activeEnrollment.getStartDate() != null && activeEnrollment.getStartDate().isAfter(endDate)) {
            activeEnrollment.endOn(activeEnrollment.getStartDate().minusDays(1));
            syncStudentAcrossSessions(activeEnrollment);
            regularClassStudentRepository.delete(activeEnrollment);
            return;
        }

        activeEnrollment.endOn(endDate);
        syncStudentAcrossSessions(activeEnrollment);
    }

    /**
     * 스마트 반 배정: 요일·시간마다 같은 강사의 동일 요일·시간 반이 있으면 합류, 없으면 새로 만든다.
     * fromRegularClassUuid가 있으면(요일 변경) 기존 배정을 effectiveFrom 전날로 먼저 종료 처리한다.
     *
     * @return 이번에 배정된(합류 + 신규 생성 포함) 정규 수업 uuid 목록
     */
    @Transactional
    public List<UUID> smartAssignStudentSchedule(Long academyId, SmartScheduleAssignmentRequest request) {
        validateRequesterBelongsToAcademy(academyId);

        Academy academy = getAcademyOrThrow(academyId);
        Member teacher = getVerifiedTeacher(academy, request.teacherUuid());
        AcademyStudent academyStudent = getAcademyStudentOrThrow(academyId, request.studentUuid());

        if (request.fromRegularClassUuid() != null) {
            RegularClass fromClass = regularClassRepository.findByUuidAndAcademyId(request.fromRegularClassUuid(), academyId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기존 시간표입니다."));
            endActiveEnrollment(academyStudent, fromClass, request.effectiveFrom().minusDays(1));
        }

        List<RegularClass> teacherClasses = regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, request.teacherUuid());

        List<UUID> resultUuids = new ArrayList<>();
        for (TimeSlotRequest slot : request.timeSlots()) {
            RegularClass matched = findMatchingClass(teacherClasses, slot);

            UUID regularClassUuid;
            if (matched != null) {
                regularClassUuid = matched.getUuid();
            } else {
                List<UUID> created = createRegularClass(academyId, new RegularClassCreateRequest(
                        request.title(), request.teacherUuid(), request.roomNumber(), request.maxCapacity(), List.of(slot), List.of()));
                regularClassUuid = created.get(0);
                teacherClasses = regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, request.teacherUuid());
            }

            UUID assignedUuid = assignStudentSchedule(academyId, new ScheduleAssignmentRequest(
                    request.studentUuid(), regularClassUuid, request.effectiveFrom(), null));
            resultUuids.add(assignedUuid);
        }

        return resultUuids;
    }

    /** 학생이 과거~현재~예정으로 어떤 반에 어떤 기간 배정되어 있었는지 전체 이력을 최신순으로 반환한다. */
    public List<ScheduleHistoryResponse> getStudentScheduleHistory(Long academyId, UUID studentUuid) {
        validateRequesterBelongsToAcademy(academyId);
        AcademyStudent academyStudent = getAcademyStudentOrThrow(academyId, studentUuid);

        return regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(academyStudent.getId()).stream()
                .map(ScheduleHistoryResponse::from)
                .toList();
    }

    /** [수강생 목록] 전용: 여러 학생의 오늘 기준 활성 반 배정을 한 번의 벌크 조회로 가져와 학생별로 묶는다(N+1 방지). */
    public Map<Long, List<ScheduleSummary>> getActiveScheduleSummaries(Collection<Long> academyStudentIds) {
        if (academyStudentIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        return regularClassStudentRepository.findByAcademyStudent_IdIn(academyStudentIds).stream()
                .filter(rcs -> rcs.isActiveOn(today))
                .collect(Collectors.groupingBy(
                        rcs -> rcs.getAcademyStudent().getId(),
                        Collectors.flatMapping(rcs -> rcs.getRegularClass().getTimeSlots().stream()
                                        .map(slot -> new ScheduleSummary(slot.getDayOfWeek(), slot.getStartTime())),
                                Collectors.toList())
                ));
    }

    /** 두 기간이 겹치는지 여부. null은 그 방향으로 무제한을 의미한다. */
    private boolean periodsOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        boolean aStartsBeforeBEnds = bEnd == null || aStart == null || !aStart.isAfter(bEnd);
        boolean bStartsBeforeAEnds = aEnd == null || bStart == null || !bStart.isAfter(aEnd);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private void removeStudentFromAllSessions(RegularClass regularClass, AcademyStudent academyStudent) {
        for (RegularClassSession session : regularClassSessionRepository.findByRegularClass_Id(regularClass.getId())) {
            regularClassSessionStudentRepository.deleteBySession_IdAndAcademyStudent_Id(session.getId(), academyStudent.getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 조회
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * @param weekReferenceDate 이 날짜가 속한 주(월~일)의 회차 정보를 함께 반환한다. null이면 기본 시간표 정보만 반환.
     */
    // weekReferenceDate가 주어지면 회차(RegularClassSession)를 생성할 수 있어 클래스 기본값(readOnly)을 덮어쓴다.
    @Transactional
    public List<RegularClassResponse> getRegularClasses(Long academyId, UUID teacherUuid, LocalDate weekReferenceDate) {
        Member requester = validateRequesterBelongsToAcademy(academyId);

        List<RegularClass> regularClasses = teacherUuid != null
                ? regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, teacherUuid)
                : regularClassRepository.findByAcademyId(academyId);

        return regularClasses.stream()
                .map(regularClass -> toResponseWithRoster(regularClass, weekReferenceDate, requester))
                .toList();
    }

    /** 학부모 전용 조회: JWT로 인증된 본인 신원 기준으로 본인 자녀가 편성된 시간표만 반환한다. */
    @Transactional
    public List<RegularClassResponse> getMyChildrenRegularClasses(LocalDate weekReferenceDate) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }

        List<RegularClassStudent> myChildrenEnrollments =
                regularClassStudentRepository.findByAcademyStudent_Student_Parent_Id(parent.getId());

        // 한 시간표에 자녀가 여러 명 편성되어 있어도 시간표 자체는 한 번만 노출한다.
        Map<Long, RegularClass> distinctClasses = new LinkedHashMap<>();
        for (RegularClassStudent enrollment : myChildrenEnrollments) {
            distinctClasses.putIfAbsent(enrollment.getRegularClass().getId(), enrollment.getRegularClass());
        }

        return distinctClasses.values().stream()
                .map(regularClass -> toResponseWithRoster(regularClass, weekReferenceDate, parent))
                .toList();
    }

    private RegularClassResponse toResponseWithRoster(RegularClass regularClass, LocalDate weekReferenceDate, Member requester) {
        // 과거 종료됐거나 아직 시작 전인 배정 이력은 "현재 명단"에 노출하지 않는다.
        List<RegularClassStudent> rosterEntities = regularClassStudentRepository.findByRegularClass_Id(regularClass.getId()).stream()
                .filter(rcs -> rcs.isActiveOn(LocalDate.now()))
                .toList();
        List<RosterStudentResponse> roster = restrictToOwnChildrenIfParent(rosterEntities.stream()
                .map(rcs -> RosterStudentResponse.from(rcs.getAcademyStudent()))
                .toList(), requester);
        // 현재 인원은 오늘 기준 수강 기간이 유효한 인원만 센다. 신원을 특정하지 않는 집계값이라 학부모에게도 그대로 노출한다.
        int currentCount = (int) rosterEntities.stream()
                .filter(rcs -> rcs.getAcademyStudent().isActiveOn(LocalDate.now()))
                .count();
        List<WeeklyOccurrenceResponse> weeklyOccurrences = computeWeeklyOccurrences(regularClass, weekReferenceDate, requester);
        return RegularClassResponse.of(regularClass, roster, currentCount, weeklyOccurrences);
    }

    /** 요청자가 학부모면 명단을 본인 자녀로만 제한한다. 원장/강사는 전체 명단을 그대로 반환한다. */
    private List<RosterStudentResponse> restrictToOwnChildrenIfParent(List<RosterStudentResponse> students, Member requester) {
        if (requester.getRole() != MemberRole.PARENT) {
            return students;
        }
        Set<UUID> myChildrenUuids = studentRepository.findByParentId(requester.getId()).stream()
                .map(Student::getUuid)
                .collect(Collectors.toSet());
        return students.stream().filter(s -> myChildrenUuids.contains(s.uuid())).toList();
    }

    /**
     * weekReferenceDate가 속한 주(월~일) 중 이 수업의 정규 요일과 겹치는 날짜마다 회차를 확보하고,
     * 그 회차의 실제 로스터 스냅샷을 기준으로 응답을 만든다. 휴무가 아니면서 유효 수강생이 한 명도
     * 없는 회차는 응답에서 제외한다(빈 블록 방지).
     */
    private List<WeeklyOccurrenceResponse> computeWeeklyOccurrences(RegularClass regularClass, LocalDate weekReferenceDate, Member requester) {
        if (weekReferenceDate == null) {
            return List.of();
        }

        LocalDate monday = weekReferenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<LocalDate> candidateDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            // 반 종료 효력일 이후는 과거 로스터가 있었더라도 더 이상 보여줄 회차가 아니다.
            if (regularClass.getDaysOfWeek().contains(date.getDayOfWeek()) && !regularClass.isDiscontinuedOn(date)) {
                candidateDates.add(date);
            }
        }
        if (candidateDates.isEmpty()) {
            return List.of();
        }

        // 학부모 요청이면 결석자 명단을 본인 자녀로만 제한해야 하므로 미리 자녀 uuid 집합을 구해 둔다.
        boolean isParent = requester.getRole() == MemberRole.PARENT;
        Set<UUID> myChildrenUuids = isParent
                ? studentRepository.findByParentId(requester.getId()).stream().map(Student::getUuid).collect(Collectors.toSet())
                : Set.of();

        LocalDate earliestStart = computeEarliestStart(regularClass);

        // 세션 확보·로스터·결석 티켓을 이 주 범위 전체에 대해 벌크 조회한다.
        Map<LocalDate, RegularClassSession> sessionsByDate = ensureSessionsForWeek(regularClass, monday, sunday, candidateDates);

        List<Long> sessionIds = sessionsByDate.values().stream().map(RegularClassSession::getId).toList();
        Map<Long, List<RegularClassSessionStudent>> rosterBySessionId = regularClassSessionStudentRepository.findBySession_IdIn(sessionIds).stream()
                .collect(Collectors.groupingBy(rcss -> rcss.getSession().getId()));
        Map<LocalDate, Set<UUID>> absentUuidsByDate = makeupTicketRepository
                .findByOriginClass_IdAndAbsentDateBetween(regularClass.getId(), monday, sunday).stream()
                .collect(Collectors.groupingBy(MakeupTicket::getAbsentDate,
                        Collectors.mapping(t -> t.getAcademyStudent().getStudent().getUuid(), Collectors.toSet())));

        List<WeeklyOccurrenceResponse> occurrences = new ArrayList<>();
        for (LocalDate date : candidateDates) {
            RegularClassSession session = sessionsByDate.get(date);
            Set<UUID> absentStudentUuids = absentUuidsByDate.getOrDefault(date, Set.of());
            List<RegularClassSessionStudent> sessionRoster = rosterBySessionId.getOrDefault(session.getId(), List.of());

            // 로스터가 비어 있고 아직 시작 전(인계 대기 등)인 회차는 렌더링하지 않는다. 신규 반은 항상 표시된다.
            if (!session.isHolidayCancelled() && sessionRoster.isEmpty() && date.isBefore(earliestStart)) {
                continue;
            }

            List<RosterStudentResponse> allAttendingStudents = sessionRoster.stream()
                    .filter(rcss -> !absentStudentUuids.contains(rcss.getAcademyStudent().getStudent().getUuid()))
                    .map(RosterStudentResponse::from)
                    .toList();

            // 출석 명단도 학부모는 본인 자녀로만 제한한다.
            List<RosterStudentResponse> visibleAttendingStudents = isParent
                    ? allAttendingStudents.stream().filter(s -> myChildrenUuids.contains(s.uuid())).toList()
                    : allAttendingStudents;

            // 결석자 식별 정보는 원장/강사에게 전체, 학부모에게는 본인 자녀 결석분만 노출한다.
            List<RosterStudentResponse> absentStudents = List.of();
            List<RosterStudentResponse> myAbsentStudents = List.of();
            if (!absentStudentUuids.isEmpty()) {
                List<RosterStudentResponse> allAbsent = sessionRoster.stream()
                        .filter(rcss -> absentStudentUuids.contains(rcss.getAcademyStudent().getStudent().getUuid()))
                        .map(RosterStudentResponse::from)
                        .toList();
                if (isParent) {
                    myAbsentStudents = allAbsent.stream().filter(s -> myChildrenUuids.contains(s.uuid())).toList();
                } else {
                    absentStudents = allAbsent;
                }
            }

            occurrences.add(new WeeklyOccurrenceResponse(
                    date, date.getDayOfWeek(), session.getStartTime(), session.getEndTime(),
                    session.isHolidayCancelled(), session.getMaxCapacity(),
                    visibleAttendingStudents, absentStudentUuids.size(), absentStudents, myAbsentStudents
            ));
        }
        return occurrences;
    }

    /** 보강 신청 화면용: 지정한 주(월~일) 중 오늘 이후이면서 정원이 차지 않은 여석을 학원 전체에서 찾는다. 휴무 회차는 제외. */
    // 세션을 새로 생성할 수 있어(ensureSession) write 트랜잭션이 필요하다.
    @Transactional
    public List<MakeupSlotResponse> getOpenMakeupSlots(Long academyId, LocalDate weekReferenceDate) {
        Member requester = validateReaderCanAccessAcademy(academyId);
        // 학부모는 강의 정보(수업명/시간/여석)만 필요해 강사명·매칭된 학생 명단은 노출하지 않는다.
        boolean isParent = requester.getRole() == MemberRole.PARENT;

        LocalDate monday = weekReferenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<MakeupSlotResponse> slots = new ArrayList<>();
        for (RegularClass regularClass : regularClassRepository.findByAcademyId(academyId)) {
            List<LocalDate> candidateDates = new ArrayList<>();
            // 지난 수업도 표시한다 — 결석/보강 처리를 놓친 경우 사후에도 매칭할 수 있어야 한다. 반 종료 이후는 제외.
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                if (regularClass.getDaysOfWeek().contains(date.getDayOfWeek()) && !regularClass.isDiscontinuedOn(date)) {
                    candidateDates.add(date);
                }
            }
            if (candidateDates.isEmpty()) {
                continue;
            }

            LocalDate earliestStart = computeEarliestStart(regularClass);
            Map<LocalDate, RegularClassSession> sessionsByDate = ensureSessionsForWeek(regularClass, monday, sunday, candidateDates);

            List<Long> sessionIds = sessionsByDate.values().stream().map(RegularClassSession::getId).toList();
            List<RegularClassSessionStudent> sessionRoster = regularClassSessionStudentRepository.findBySession_IdIn(sessionIds);

            // 결석 처리된 학생은 그 자리만큼 여석이어야 하므로 정원 계산에서 제외한다.
            Map<LocalDate, Set<UUID>> absentUuidsByDate = makeupTicketRepository
                    .findByOriginClass_IdAndAbsentDateBetween(regularClass.getId(), monday, sunday).stream()
                    .collect(Collectors.groupingBy(MakeupTicket::getAbsentDate,
                            Collectors.mapping(t -> t.getAcademyStudent().getStudent().getUuid(), Collectors.toSet())));
            List<RegularClassSessionStudent> attendingRoster = sessionRoster.stream()
                    .filter(rcss -> !absentUuidsByDate.getOrDefault(rcss.getSession().getDate(), Set.of())
                            .contains(rcss.getAcademyStudent().getStudent().getUuid()))
                    .toList();

            Map<Long, Integer> countBySessionId = attendingRoster.stream()
                    .collect(Collectors.groupingBy(rcss -> rcss.getSession().getId(), Collectors.summingInt(rcss -> 1)));
            Map<Long, List<RosterStudentResponse>> attendingBySessionId = isParent
                    ? Map.of()
                    : attendingRoster.stream().collect(Collectors.groupingBy(
                            rcss -> rcss.getSession().getId(),
                            Collectors.mapping(RosterStudentResponse::from, Collectors.toList())));

            for (LocalDate date : candidateDates) {
                RegularClassSession session = sessionsByDate.get(date);
                if (session.isHolidayCancelled()) {
                    continue;
                }

                if (date.isBefore(earliestStart)) {
                    continue;
                }

                int currentCount = countBySessionId.getOrDefault(session.getId(), 0);
                int remaining = session.getMaxCapacity() - currentCount;
                if (remaining <= 0) {
                    continue;
                }

                slots.add(new MakeupSlotResponse(
                        regularClass.getUuid(), regularClass.getTitle(),
                        isParent ? null : regularClass.getTeacher().getName(),
                        regularClass.getRoomNumber(), date, date.getDayOfWeek(),
                        session.getStartTime(), session.getEndTime(),
                        session.getMaxCapacity(), currentCount, remaining,
                        attendingBySessionId.getOrDefault(session.getId(), List.of())
                ));
            }
        }

        slots.sort(Comparator.comparing(MakeupSlotResponse::date).thenComparing(MakeupSlotResponse::startTime));
        return slots;
    }

    /** "다음 수업" 탐색 시 며칠 앞까지 내다볼지(모든 요일 조합을 커버하기에 충분한 여유값). */
    private static final int NEXT_CLASS_LOOKAHEAD_DAYS = 30;

    /** 원장/강사 대시보드용: 현재 시각 기준 가장 가까운 다가오는 회차. teacherUuid 없으면 학원 전체, 있으면 담당분만. */
    // findNextOccurrence가 후보 날짜의 회차를 생성할 수 있어 write 트랜잭션이 필요하다.
    @Transactional
    public Optional<NextClassResponse> getNextClass(Long academyId, UUID teacherUuid) {
        validateRequesterBelongsToAcademy(academyId);

        List<RegularClass> candidates = teacherUuid != null
                ? regularClassRepository.findByAcademyIdAndTeacher_Uuid(academyId, teacherUuid)
                : regularClassRepository.findByAcademyId(academyId);

        return findNextOccurrence(candidates);
    }

    /** 학부모 대시보드용: 본인 자녀가 편성된 시간표 중 가장 가까운 다가오는 회차. */
    @Transactional
    public Optional<NextClassResponse> getNextClassForMyChildren() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }

        List<RegularClassStudent> myChildrenEnrollments =
                regularClassStudentRepository.findByAcademyStudent_Student_Parent_Id(parent.getId());

        Map<Long, RegularClass> distinctClasses = new LinkedHashMap<>();
        for (RegularClassStudent enrollment : myChildrenEnrollments) {
            distinctClasses.putIfAbsent(enrollment.getRegularClass().getId(), enrollment.getRegularClass());
        }

        return findNextOccurrence(new ArrayList<>(distinctClasses.values()));
    }

    /** 후보 시간표 중 지금 이후로 가장 이르게 시작하는, 유효 수강생이 있는 회차를 찾는다. 휴무일/공백 회차는 건너뛴다. */
    private Optional<NextClassResponse> findNextOccurrence(List<RegularClass> candidates) {
        LocalDateTime now = LocalDateTime.now();

        RegularClass bestClass = null;
        RegularClassSession bestSession = null;
        LocalDateTime bestStart = null;

        for (RegularClass regularClass : candidates) {
            for (int offset = 0; offset <= NEXT_CLASS_LOOKAHEAD_DAYS; offset++) {
                LocalDate date = now.toLocalDate().plusDays(offset);
                Optional<RegularClassTimeSlot> timeSlot = regularClass.getTimeSlotFor(date.getDayOfWeek());
                if (timeSlot.isEmpty()) {
                    continue;
                }

                LocalDateTime occurrenceStart = LocalDateTime.of(date, timeSlot.get().getStartTime());
                if (occurrenceStart.isBefore(now)) {
                    continue;
                }

                if (academyHolidayRepository.existsByAcademyIdAndDate(regularClass.getAcademy().getId(), date)) {
                    continue;
                }

                RegularClassSession session = ensureSession(regularClass, date);
                boolean hasAttendees = !regularClassSessionStudentRepository.findBySession_Id(session.getId()).isEmpty();
                if (!hasAttendees) {
                    continue; // 유효 수강생이 없는 회차는 "다음 수업"으로 보여줄 의미가 없다.
                }

                if (bestStart == null || occurrenceStart.isBefore(bestStart)) {
                    bestStart = occurrenceStart;
                    bestClass = regularClass;
                    bestSession = session;
                }
                break; // 이 수업의 가장 이른 유효 후보를 찾았으니 다음 요일은 볼 필요 없다.
            }
        }

        if (bestClass == null) {
            return Optional.empty();
        }

        List<String> studentNames = regularClassSessionStudentRepository.findBySession_Id(bestSession.getId()).stream()
                .map(rcss -> {
                    AcademyStudent academyStudent = rcss.getAcademyStudent();
                    String managementName = academyStudent.getManagementName();
                    return managementName != null && !managementName.isBlank() ? managementName : academyStudent.getStudent().getName();
                })
                .toList();

        return Optional.of(new NextClassResponse(
                bestClass.getUuid(),
                bestClass.getTitle(),
                bestClass.getTeacher().getName(),
                bestClass.getRoomNumber(),
                bestSession.getDate(),
                bestSession.getStartTime(),
                bestSession.getEndTime(),
                studentNames.size(),
                bestSession.getMaxCapacity(),
                studentNames
        ));
    }

    private Member validateRequesterBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 시간표만 조회하거나 관리할 수 있습니다.");
        }
        return requester;
    }

    /** 조회 전용 완화 버전 — 학부모는 본인 소속 학원과 무관하게 자녀가 다니는 학원이면 조회를 허용한다. */
    private Member validateReaderCanAccessAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() != null && requester.getAcademy().getId().equals(academyId)) {
            return requester;
        }
        if (requester.getRole() == MemberRole.PARENT
                && academyStudentRepository.existsByAcademyIdAndStudent_Parent_Id(academyId, requester.getId())) {
            return requester;
        }
        throw new IllegalStateException("소속 학원의 시간표만 조회할 수 있습니다.");
    }

    private boolean isSameAcademy(Academy a, Academy b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }
}
