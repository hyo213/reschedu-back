package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.MakeupRequestCreateRequest;
import com.academy.reschedu.domain.makeup.dto.MakeupRequestResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupSlotResponse;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.domain.member.StudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClass;
import com.academy.reschedu.domain.regularclass.RegularClassRepository;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.domain.regularclass.RegularClassSession;
import com.academy.reschedu.domain.regularclass.RegularClassSessionStudent;
import com.academy.reschedu.domain.regularclass.RegularClassSessionStudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClassStudent;
import com.academy.reschedu.domain.regularclass.RegularClassStudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClassTimeSlot;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MakeupRequestService {

    private final MakeupRequestRepository makeupRequestRepository;
    private final MakeupTicketRepository makeupTicketRepository;
    private final RegularClassRepository regularClassRepository;
    private final RegularClassSessionStudentRepository regularClassSessionStudentRepository;
    private final RegularClassStudentRepository regularClassStudentRepository;
    private final RegularClassService regularClassService;
    private final AcademyStudentRepository academyStudentRepository;
    private final StudentRepository studentRepository;
    private final CurrentMemberProvider currentMemberProvider;
    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    private static final long SLOT_LOCK_WAIT_SECONDS = 3L;
    private static final long SLOT_LOCK_LEASE_SECONDS = 10L;

    /** 보강 신청 화면: 지정한 주의 여석 목록을 조회한다(세션 확보/정원 계산은 RegularClassService에 위임). */
    // 회차를 새로 INSERT할 수 있어 readOnly 기본값을 여기서 쓰기 가능한 트랜잭션으로 덮어쓴다.
    @Transactional
    public List<MakeupSlotResponse> getOpenSlots(Long academyId, LocalDate weekReferenceDate) {
        return regularClassService.getOpenMakeupSlots(academyId, weekReferenceDate);
    }

    /**
     * 학부모가 본인 자녀의 보강권으로 다른 정규 수업의 특정 날짜 회차에 보강 참석을 신청한다.
     * 학부모는 본인 자녀에 대해서만, 원장/강사는 소속 학원의 수강생이라면 누구든 대신 신청할 수 있다.
     * 같은 여석에 대한 신청은 락으로 직렬화되고 대기 중인 신청도 정원에 포함해 계산하므로,
     * 정원보다 많은 인원이 동시에 몰려도(예: 4석에 10명) 정확히 정원만큼만 신청에 성공한다.
     * 다만 최종 확정은 원장/강사가 수락할 때 실제 로스터를 기준으로 한 번 더 검증한다
     * (그 사이 다른 대기 신청이 거절되거나 자리 상황이 바뀔 수 있으므로).
     */
    @Transactional
    public UUID createRequest(MakeupRequestCreateRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();

        // 같은 여석(수업+날짜)에 대한 신청을 이 락으로 직렬화한다 — 그렇지 않으면 여러 학부모가 동시에
        // 신청할 때 각자 다른 스레드에서 정원 미달로 읽고 통과해버려, 정원보다 많은 신청이 성공할 수 있다.
        acquireSlotLock(request.targetRegularClassUuid(), request.targetDate());

        PreparedBooking booking = prepareBooking(requester, request);

        MakeupRequest makeupRequest = MakeupRequest.builder()
                .ticket(booking.ticket())
                .targetRegularClass(booking.targetClass())
                .targetDate(request.targetDate())
                .build();
        makeupRequestRepository.save(makeupRequest);
        return makeupRequest.getUuid();
    }

    /** 원장/강사 전용: 여석에 학생을 직접 매칭한다(신청→수락 2단계 없이 즉시 APPROVED). */
    @Transactional
    public UUID matchStudentToSlot(Long academyId, MakeupRequestCreateRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("원장/강사만 보강 매칭을 할 수 있습니다.");
        }
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원에서만 보강 매칭을 할 수 있습니다.");
        }

        // 같은 회차의 정원 체크와 편성 INSERT를 하나의 락 구간으로 묶어 동시 매칭 경합을 막는다.
        acquireSlotLock(request.targetRegularClassUuid(), request.targetDate());

        PreparedBooking booking = prepareBooking(requester, request);
        if (!booking.targetClass().getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 수업에 대해서만 매칭할 수 있습니다.");
        }

        MakeupRequest makeupRequest = MakeupRequest.builder()
                .ticket(booking.ticket())
                .targetRegularClass(booking.targetClass())
                .targetDate(request.targetDate())
                .build();
        makeupRequestRepository.save(makeupRequest);

        regularClassSessionStudentRepository.save(new RegularClassSessionStudent(booking.targetSession(), booking.academyStudent(), true));
        booking.ticket().use();
        makeupRequest.approve(requester);

        return makeupRequest.getUuid();
    }

    /**
     * createRequest/matchStudentToSlot이 공유하는 준비 단계: 요청자 권한, 대상 회차의 유효성(정규 요일/과거
     * 날짜/휴무 여부/중복 편성/정원)을 검증하고, 사용할 미사용 보강권 한 장을 골라 함께 반환한다.
     */
    private PreparedBooking prepareBooking(Member requester, MakeupRequestCreateRequest request) {
        Student student = studentRepository.findByUuid(request.studentUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수강생입니다."));

        RegularClass targetClass = regularClassRepository.findByUuid(request.targetRegularClassUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정규 수업입니다."));

        validateRequester(requester, student, targetClass);

        AcademyStudent academyStudent = academyStudentRepository
                .findByStudentUuidAndAcademyId(request.studentUuid(), targetClass.getAcademy().getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 학원에 등록되지 않은 수강생입니다."));

        if (!targetClass.getDaysOfWeek().contains(request.targetDate().getDayOfWeek())) {
            throw new IllegalArgumentException("지정한 날짜는 해당 수업의 정규 요일이 아닙니다.");
        }
        if (request.targetDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("이미 지난 날짜로는 보강을 신청할 수 없습니다.");
        }

        RegularClassSession targetSession = regularClassService.ensureSessionForMakeupBooking(targetClass, request.targetDate());
        if (targetSession.isHolidayCancelled()) {
            throw new IllegalStateException("휴무일로 지정된 날짜에는 보강 신청을 할 수 없습니다.");
        }

        boolean alreadyAttending = regularClassSessionStudentRepository
                .existsBySession_IdAndAcademyStudent_Id(targetSession.getId(), academyStudent.getId());
        if (alreadyAttending) {
            throw new IllegalStateException("이미 해당 날짜에 정상 편성되어 있는 수업입니다.");
        }

        validateNoScheduleConflict(academyStudent, targetClass, targetSession, request.targetDate());

        // 결석 처리된 학생은 정원 계산에서 제외하고, 아직 수락 전인 대기(PENDING) 신청도 자리를 예약 중인
        // 것으로 보고 더한다 — 그래야 정원 4석에 10명이 동시에 몰려도 딱 4명만 신청에 성공한다.
        long enrolledCount = regularClassSessionStudentRepository.findBySession_Id(targetSession.getId()).stream()
                .filter(rcss -> !makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                        targetClass.getId(), rcss.getAcademyStudent().getId(), request.targetDate()))
                .count();
        long pendingCount = makeupRequestRepository.countByTargetRegularClass_IdAndTargetDateAndStatus(
                targetClass.getId(), request.targetDate(), MakeupRequestStatus.PENDING);
        if (enrolledCount + pendingCount >= targetSession.getMaxCapacity()) {
            throw new IllegalStateException("해당 회차는 이미 정원이 가득 찼습니다.");
        }

        MakeupTicket ticket = makeupTicketRepository
                .findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(academyStudent.getId(), MakeupTicketStatus.UNUSED)
                .stream()
                .filter(t -> !makeupRequestRepository.existsByTicket_IdAndStatusIn(
                        t.getId(), List.of(MakeupRequestStatus.PENDING, MakeupRequestStatus.APPROVED)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("사용 가능한 보강권이 없습니다."));

        return new PreparedBooking(ticket, targetClass, targetSession, academyStudent);
    }

    private record PreparedBooking(MakeupTicket ticket, RegularClass targetClass,
                                    RegularClassSession targetSession, AcademyStudent academyStudent) {
    }

    /**
     * 보강 신청 대상 시간과 겹치는 다른 일정이 있으면 막는다. 두 가지를 확인한다:
     * (1) 이 학생이 그 요일·시간에 실제로 다니는 다른 정규 수업(그 반+날짜로 결석 처리되어 있지 않은 경우),
     * (2) 같은 날짜에 이미 잡혀 있는 다른 보강 신청(대기/수락).
     */
    private void validateNoScheduleConflict(AcademyStudent academyStudent, RegularClass targetClass,
                                             RegularClassSession targetSession, LocalDate targetDate) {
        DayOfWeek dayOfWeek = targetDate.getDayOfWeek();

        for (RegularClassStudent enrollment : regularClassStudentRepository.findByAcademyStudent_Id(academyStudent.getId())) {
            RegularClass otherClass = enrollment.getRegularClass();
            if (otherClass.getId().equals(targetClass.getId()) || !enrollment.isActiveOn(targetDate)) {
                continue;
            }
            Optional<RegularClassTimeSlot> slot = otherClass.getTimeSlotFor(dayOfWeek);
            if (slot.isEmpty() || !timesOverlap(slot.get().getStartTime(), slot.get().getEndTime(),
                    targetSession.getStartTime(), targetSession.getEndTime())) {
                continue;
            }
            boolean absentFromOtherClass = makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                    otherClass.getId(), academyStudent.getId(), targetDate);
            if (absentFromOtherClass) {
                continue;
            }
            throw new IllegalStateException(String.format(
                    "같은 시간에 이미 다니고 있는 수업(%s, %s~%s)이 있어 보강 신청할 수 없습니다.",
                    otherClass.getTitle() != null ? otherClass.getTitle() : "수업",
                    slot.get().getStartTime(), slot.get().getEndTime()));
        }

        List<MakeupRequestStatus> activeStatuses = List.of(MakeupRequestStatus.PENDING, MakeupRequestStatus.APPROVED);
        for (MakeupRequest existing : makeupRequestRepository.findByTicket_AcademyStudent_IdAndTargetDateAndStatusIn(
                academyStudent.getId(), targetDate, activeStatuses)) {
            RegularClass existingClass = existing.getTargetRegularClass();
            Optional<RegularClassTimeSlot> slot = existingClass.getTimeSlotFor(dayOfWeek);
            if (slot.isPresent() && timesOverlap(slot.get().getStartTime(), slot.get().getEndTime(),
                    targetSession.getStartTime(), targetSession.getEndTime())) {
                throw new IllegalStateException("같은 시간에 이미 다른 보강 신청이 있어 중복 신청할 수 없습니다.");
            }
        }
    }

    private boolean timesOverlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * 학부모: 본인 자녀에 대해서만 허용. 원장/강사: 자신이 소속된 학원의 수업에 대해서만 허용.
     */
    private void validateRequester(Member requester, Student student, RegularClass targetClass) {
        if (requester.getRole() == MemberRole.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(requester.getId())) {
                throw new IllegalStateException("본인의 자녀에 대해서만 보강 신청을 할 수 있습니다.");
            }
            return;
        }
        if (requester.getRole() == MemberRole.ADMIN || requester.getRole() == MemberRole.TEACHER) {
            if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(targetClass.getAcademy().getId())) {
                throw new IllegalStateException("소속 학원의 수업에 대해서만 보강 신청을 할 수 있습니다.");
            }
            return;
        }
        throw new IllegalStateException("보강 신청을 할 수 있는 권한이 없습니다.");
    }

    /** 학부모 전용: 본인 자녀들의 보강 신청 내역(대기/수락/거절 전체)을 조회한다. */
    public List<MakeupRequestResponse> getMyRequests() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }
        return makeupRequestRepository.findByTicket_AcademyStudent_Student_Parent_IdOrderByCreatedAtDesc(parent.getId())
                .stream()
                .map(MakeupRequestResponse::from)
                .toList();
    }

    /** 원장/강사용: 보강 매칭 센터의 대기 목록 조회. */
    public List<MakeupRequestResponse> getPendingRequests(Long academyId) {
        validateStaffBelongsToAcademy(academyId);
        return makeupRequestRepository
                .findByTargetRegularClass_Academy_IdAndStatusOrderByCreatedAtAsc(academyId, MakeupRequestStatus.PENDING)
                .stream()
                .map(MakeupRequestResponse::from)
                .toList();
    }

    /**
     * 원장/강사가 보강 신청을 수락한다. 대상 회차의 로스터에 편입하고 티켓을 사용 처리한다.
     * 신청 시점 이후 정원이 이미 찼을 수 있으므로(동시 신청 등) 여기서 다시 한번 정원을 검증한다.
     */
    @Transactional
    public void approve(Long academyId, UUID requestUuid) {
        Member requester = validateStaffBelongsToAcademy(academyId);

        MakeupRequest makeupRequest = makeupRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보강 신청입니다."));
        validateBelongsToAcademy(makeupRequest, academyId);

        RegularClass targetClass = makeupRequest.getTargetRegularClass();

        // requestUuid가 아니라 (수업, 날짜) 단위로 잠근다 — matchStudentToSlot과 동일한 락 키 규칙.
        acquireSlotLock(targetClass.getUuid(), makeupRequest.getTargetDate());

        RegularClassSession targetSession = regularClassService.ensureSessionForMakeupBooking(targetClass, makeupRequest.getTargetDate());

        long currentCount = regularClassSessionStudentRepository.findBySession_Id(targetSession.getId()).stream()
                .filter(rcss -> !makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                        targetClass.getId(), rcss.getAcademyStudent().getId(), makeupRequest.getTargetDate()))
                .count();
        if (currentCount >= targetSession.getMaxCapacity()) {
            throw new IllegalStateException("정원이 가득 차 더 이상 수락할 수 없습니다. 거절 처리해주세요.");
        }

        AcademyStudent academyStudent = makeupRequest.getTicket().getAcademyStudent();
        boolean alreadyAttending = regularClassSessionStudentRepository
                .existsBySession_IdAndAcademyStudent_Id(targetSession.getId(), academyStudent.getId());
        if (!alreadyAttending) {
            regularClassSessionStudentRepository.save(new RegularClassSessionStudent(targetSession, academyStudent, true));
        }

        makeupRequest.getTicket().use();
        makeupRequest.approve(requester);
    }

    /**
     * 원장/강사가 보강 신청을 거절한다. 티켓은 소비되지 않으므로 학부모가 다른 여석에 다시 신청할 수 있다.
     */
    @Transactional
    public void reject(Long academyId, UUID requestUuid) {
        Member requester = validateStaffBelongsToAcademy(academyId);

        MakeupRequest makeupRequest = makeupRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보강 신청입니다."));
        validateBelongsToAcademy(makeupRequest, academyId);

        makeupRequest.reject(requester);
    }

    private Member validateStaffBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("원장/강사만 처리할 수 있습니다.");
        }
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 보강 신청만 조회하거나 관리할 수 있습니다.");
        }
        return requester;
    }

    private void validateBelongsToAcademy(MakeupRequest makeupRequest, Long academyId) {
        if (!makeupRequest.getTargetRegularClass().getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 보강 신청만 처리할 수 있습니다.");
        }
    }

    /**
     * 보강 여석(수업+날짜) 단위로 Redisson 분산 락을 건다. 락 자체는 이 메서드 안에서 바로 풀지 않고
     * {@link #releaseLockAfterCommit}에 등록해 트랜잭션이 커밋된 뒤에 풀리도록 한다 — 그렇지 않으면
     * 이 메서드를 감싼 트랜잭션이 아직 커밋되기 전에 락이 풀려, 그 사이 다른 스레드가 아직 반영되지
     * 않은(정원 미달로 보이는) 상태를 읽고 정원 체크를 통과해버리는 경합이 남는다.
     */
    private void acquireSlotLock(UUID regularClassUuid, LocalDate targetDate) {
        RLock lock = redissonClient.getLock("makeup-slot:" + regularClassUuid + ":" + targetDate);
        boolean acquired;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            acquired = lock.tryLock(SLOT_LOCK_WAIT_SECONDS, SLOT_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트가 발생했습니다.", e);
        } finally {
            // 🎯 여러 요청이 같은 자리를 두고 경합할 때 실제로 얼마나 대기했는지 관찰하기 위한 지표.
            sample.stop(meterRegistry.timer("makeup.slot.lock.wait"));
        }
        if (!acquired) {
            meterRegistry.counter("makeup.slot.lock.failed").increment();
            throw new IllegalStateException("다른 요청이 같은 자리를 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
        releaseLockAfterCommit(lock);
    }

    private void releaseLockAfterCommit(RLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lock.unlock();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
    }
}
