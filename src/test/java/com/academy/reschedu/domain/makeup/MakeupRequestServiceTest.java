package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.makeup.dto.MakeupRequestCreateRequest;
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
import com.academy.reschedu.domain.regularclass.RegularClassSessionStudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClassStudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClassTimeSlot;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakeupRequestServiceTest {

    @Mock
    private MakeupRequestRepository makeupRequestRepository;
    @Mock
    private MakeupTicketRepository makeupTicketRepository;
    @Mock
    private RegularClassRepository regularClassRepository;
    @Mock
    private RegularClassSessionStudentRepository regularClassSessionStudentRepository;
    @Mock
    private RegularClassStudentRepository regularClassStudentRepository;
    @Mock
    private RegularClassService regularClassService;
    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private MakeupRequestService makeupRequestService;

    private Academy academy;
    private Member parent;
    private Student child;
    private RegularClass targetClass;
    private RegularClassSession targetSession;
    private AcademyStudent academyStudent;
    private LocalDate targetDate;
    private LocalDate nextWednesday;
    private LocalDate pastTuesday;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 🎯 하드코딩된 절대 날짜 대신 "다음 화요일/수요일"과 "충분히 과거의 화요일"을 동적으로 계산해,
        // 테스트가 실행되는 시점과 무관하게 항상 유효하게 남도록 한다.
        LocalDate nextTuesday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.TUESDAY));
        nextWednesday = nextTuesday.plusDays(1);
        pastTuesday = nextTuesday.minusWeeks(60); // 60주(420일)는 7의 배수라 요일이 그대로 유지된다

        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        parent = new Member("parent@test.com", "encoded", "학부모", "010-0000-0001", MemberRole.PARENT, academy);
        ReflectionTestUtils.setField(parent, "id", 10L);

        child = new Student("아이", LocalDate.of(2015, 1, 1), "MALE", null, parent);

        Member teacher = new Member("teacher@test.com", "encoded", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
        targetClass = RegularClass.builder()
                .academy(academy).title("화요반").teacher(teacher).maxCapacity(2)
                .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0))))
                .build();
        ReflectionTestUtils.setField(targetClass, "id", 30L);
        ReflectionTestUtils.setField(targetClass, "uuid", UUID.randomUUID());

        academyStudent = new AcademyStudent(academy, child, "아이", null, true, null, null, null, null, null);
        ReflectionTestUtils.setField(academyStudent, "id", 40L);

        targetSession = RegularClassSession.builder()
                .regularClass(targetClass).date(nextTuesday)
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0))
                .maxCapacity(2).holidayCancelled(false)
                .build();
        ReflectionTestUtils.setField(targetSession, "id", 50L);

        targetDate = nextTuesday;

        // 🎯 일정 충돌 검증(validateNoScheduleConflict)의 기본값 — "충돌 없음"으로 lenient 처리해 두고,
        // 충돌 시나리오를 다루는 테스트에서만 개별적으로 다시 스텁한다.
        lenient().when(regularClassStudentRepository.findByAcademyStudent_Id(40L)).thenReturn(List.of());
        lenient().when(makeupRequestRepository.findByTicket_AcademyStudent_IdAndTargetDateAndStatusIn(
                eq(40L), any(), any())).thenReturn(List.of());

        // 🎯 createRequest/matchStudentToSlot이 공통으로 거는 Redisson 락 — 기본값은 "즉시 획득 성공"으로
        // 두고, 락 자체를 검증하는 테스트에서만 개별적으로 다시 스텁한다.
        RLock lock = mock(RLock.class);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        // 🎯 정원 계산에 더해지는 "대기 중인 신청 수"의 기본값 — 없음(0)으로 두고, 몰림 시나리오를
        // 다루는 테스트에서만 개별적으로 다시 스텁한다.
        lenient().when(makeupRequestRepository.countByTargetRegularClass_IdAndTargetDateAndStatus(
                anyLong(), any(), any())).thenReturn(0L);
    }

    private MakeupTicket unusedTicket() {
        MakeupTicket ticket = MakeupTicket.builder()
                .academyStudent(academyStudent).originClass(targetClass)
                .absentDate(targetDate.minusWeeks(1)).source(MakeupTicketSource.STUDENT_ABSENCE)
                .expiredAt(targetDate.plusMonths(1).atStartOfDay())
                .build();
        ReflectionTestUtils.setField(ticket, "id", 60L);
        return ticket;
    }

    @Nested
    class CreateRequest {

        @Test
        void 정상적으로_보강신청을_생성한다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);
            when(regularClassSessionStudentRepository.findBySession_Id(50L)).thenReturn(List.of());
            MakeupTicket ticket = unusedTicket();
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(ticket));
            when(makeupRequestRepository.existsByTicket_IdAndStatusIn(eq(60L), any())).thenReturn(false);
            when(makeupRequestRepository.save(any(MakeupRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);
            makeupRequestService.createRequest(request);
        }

        @Test
        void 사용가능한_보강권이_없으면_예외() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);
            when(regularClassSessionStudentRepository.findBySession_Id(50L)).thenReturn(List.of());
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("사용 가능한 보강권이 없습니다");
        }

        @Test
        void 학부모는_본인_자녀가_아니면_신청할_수_없다() {
            Member otherParent = new Member("other@test.com", "e", "다른학부모", "010-0000-0099", MemberRole.PARENT, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(otherParent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("본인의 자녀");
        }

        @Test
        void 정규_요일이_아닌_날짜는_신청할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));

            // targetClass는 화요일반인데 수요일로 신청
            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), nextWednesday);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("정규 요일이 아닙니다");
        }

        @Test
        void 과거_날짜는_신청할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), pastTuesday);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 지난 날짜");
        }

        @Test
        void 같은_시간에_다니는_다른_정규수업이_있으면_신청할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);

            // 이 학생이 이미 다니는 다른 반(같은 화요일 17~18시, targetSession과 겹침)
            Member otherTeacher = new Member("teacher2@test.com", "e", "강사2", "010-0000-0004", MemberRole.TEACHER, academy);
            RegularClass myOtherClass = RegularClass.builder()
                    .academy(academy).title("수영반").teacher(otherTeacher).maxCapacity(10)
                    .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0))))
                    .build();
            ReflectionTestUtils.setField(myOtherClass, "id", 31L);
            com.academy.reschedu.domain.regularclass.RegularClassStudent myEnrollment =
                    new com.academy.reschedu.domain.regularclass.RegularClassStudent(myOtherClass, academyStudent);
            when(regularClassStudentRepository.findByAcademyStudent_Id(40L)).thenReturn(List.of(myEnrollment));
            // 결석 처리되어 있지 않음(그 반에 실제로 참석 예정)
            when(makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(31L, 40L, targetDate))
                    .thenReturn(false);

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("같은 시간에 이미");
        }

        @Test
        void 결석_처리된_반과_겹치면_신청할_수_있다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);
            when(regularClassSessionStudentRepository.findBySession_Id(50L)).thenReturn(List.of());

            Member otherTeacher = new Member("teacher2@test.com", "e", "강사2", "010-0000-0004", MemberRole.TEACHER, academy);
            RegularClass myOtherClass = RegularClass.builder()
                    .academy(academy).title("수영반").teacher(otherTeacher).maxCapacity(10)
                    .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0))))
                    .build();
            ReflectionTestUtils.setField(myOtherClass, "id", 31L);
            com.academy.reschedu.domain.regularclass.RegularClassStudent myEnrollment =
                    new com.academy.reschedu.domain.regularclass.RegularClassStudent(myOtherClass, academyStudent);
            when(regularClassStudentRepository.findByAcademyStudent_Id(40L)).thenReturn(List.of(myEnrollment));
            // 그 반+날짜에 이미 결석 처리(보강권 발급)되어 있음 — 그 시간엔 원래 수업에 안 가므로 충돌이 아니다.
            when(makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(31L, 40L, targetDate))
                    .thenReturn(true);

            MakeupTicket ticket = unusedTicket();
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(ticket));
            when(makeupRequestRepository.existsByTicket_IdAndStatusIn(eq(60L), any())).thenReturn(false);
            when(makeupRequestRepository.save(any(MakeupRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            makeupRequestService.createRequest(request);
        }

        @Test
        void 정원이_가득_찬_회차는_신청할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);
            // maxCapacity=2 인 세션에 이미 2명이 차 있는 상태를 재현
            when(regularClassSessionStudentRepository.findBySession_Id(50L))
                    .thenReturn(List.of(
                            new com.academy.reschedu.domain.regularclass.RegularClassSessionStudent(targetSession, academyStudent, false),
                            new com.academy.reschedu.domain.regularclass.RegularClassSessionStudent(targetSession, academyStudent, false)));

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 정원이 가득");
        }

        @Test
        void 대기중인_신청만으로_정원이_차있어도_신청할_수_없다() {
            // 🎯 로스터엔 아직 아무도 편입되지 않았지만(정원 4석에 10명이 몰리는 시나리오), 이미 다른
            // 학부모들의 대기(PENDING) 신청이 정원만큼 쌓여 있으면 그 뒤의 신청은 즉시 거절되어야 한다.
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(java.util.Optional.of(child));
            when(regularClassRepository.findByUuid(targetClass.getUuid())).thenReturn(java.util.Optional.of(targetClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(java.util.Optional.of(academyStudent));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.existsBySession_IdAndAcademyStudent_Id(50L, 40L))
                    .thenReturn(false);
            when(regularClassSessionStudentRepository.findBySession_Id(50L)).thenReturn(List.of());
            // maxCapacity=2인 세션에 이미 PENDING 신청 2건이 자리를 예약 중인 상태를 재현
            when(makeupRequestRepository.countByTargetRegularClass_IdAndTargetDateAndStatus(
                    30L, targetDate, MakeupRequestStatus.PENDING)).thenReturn(2L);

            MakeupRequestCreateRequest request = new MakeupRequestCreateRequest(
                    child.getUuid(), targetClass.getUuid(), targetDate);

            assertThatThrownBy(() -> makeupRequestService.createRequest(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 정원이 가득");
        }
    }

    @Nested
    class Approve {

        @Test
        void 정원이_가득_차면_수락할_수_없다() throws InterruptedException {
            Member admin = new Member("admin@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(admin);

            MakeupTicket ticket = unusedTicket();
            MakeupRequest makeupRequest = MakeupRequest.builder()
                    .ticket(ticket).targetRegularClass(targetClass).targetDate(targetDate).build();
            UUID requestUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(makeupRequest, "uuid", requestUuid);

            // 🎯 approve()가 정원 체크 전에 획득하는 Redisson 분산 락을 모킹 — 항상 즉시 획득 성공한다고 가정.
            RLock lock = mock(RLock.class);
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            when(makeupRequestRepository.findByUuid(requestUuid)).thenReturn(java.util.Optional.of(makeupRequest));
            when(regularClassService.ensureSessionForMakeupBooking(targetClass, targetDate)).thenReturn(targetSession);
            when(regularClassSessionStudentRepository.findBySession_Id(50L))
                    .thenReturn(List.of(
                            new com.academy.reschedu.domain.regularclass.RegularClassSessionStudent(targetSession, academyStudent, false),
                            new com.academy.reschedu.domain.regularclass.RegularClassSessionStudent(targetSession, academyStudent, false)));

            assertThatThrownBy(() -> makeupRequestService.approve(1L, requestUuid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정원이 가득 차");
        }

        @Test
        void 원장_강사가_아니면_수락할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);

            assertThatThrownBy(() -> makeupRequestService.approve(1L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("원장/강사만");
        }
    }
}
