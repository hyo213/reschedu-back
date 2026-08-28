package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.makeup.dto.AbsenceRequest;
import com.academy.reschedu.domain.makeup.dto.ManualTicketGrantRequest;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.domain.member.StudentRepository;
import com.academy.reschedu.domain.regularclass.RegularClass;
import com.academy.reschedu.domain.regularclass.RegularClassRepository;
import com.academy.reschedu.domain.regularclass.RegularClassSession;
import com.academy.reschedu.domain.regularclass.RegularClassSessionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakeupTicketServiceTest {

    @Mock
    private MakeupTicketRepository makeupTicketRepository;
    @Mock
    private MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    @Mock
    private MakeupRequestRepository makeupRequestRepository;
    @Mock
    private RegularClassRepository regularClassRepository;
    @Mock
    private RegularClassStudentRepository regularClassStudentRepository;
    @Mock
    private RegularClassSessionRepository regularClassSessionRepository;
    @Mock
    private RegularClassSessionStudentRepository regularClassSessionStudentRepository;
    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MakeupTicketService makeupTicketService;

    private Academy academy;
    private Member parent;
    private Student child;
    private RegularClass regularClass;
    private AcademyStudent academyStudent;
    private LocalDate nextMonday;
    private LocalDate nextTuesday;

    @BeforeEach
    void setUp() {
        // 🎯 하드코딩된 절대 날짜 대신 "다음 월요일/화요일"을 동적으로 계산해, 테스트가 실행되는 시점과
        // 무관하게 항상 미래의 유효한 요일로 남도록 한다.
        nextMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        nextTuesday = nextMonday.plusDays(1);

        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        parent = new Member("parent@test.com", "encoded", "학부모", "010-0000-0001", MemberRole.PARENT, academy);
        ReflectionTestUtils.setField(parent, "id", 10L);

        child = new Student("아이", LocalDate.of(2015, 1, 1), "MALE", null, parent);
        ReflectionTestUtils.setField(child, "id", 20L);

        Member teacher = new Member("teacher@test.com", "encoded", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
        regularClass = RegularClass.builder()
                .academy(academy).title("월요반").teacher(teacher).maxCapacity(10)
                .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 0))))
                .build();
        ReflectionTestUtils.setField(regularClass, "id", 30L);
        ReflectionTestUtils.setField(regularClass, "uuid", UUID.randomUUID());

        academyStudent = new AcademyStudent(academy, child, "아이", null, true, null, null, null, null, null);
        ReflectionTestUtils.setField(academyStudent, "id", 40L);
    }

    @Nested
    class RequestAbsence {

        @Test
        void 정상적으로_결석_처리하고_보강권을_발급한다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(regularClassStudentRepository.existsByRegularClass_IdAndAcademyStudent_Id(30L, 40L))
                    .thenReturn(true);
            when(makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                    30L, 40L, nextMonday)).thenReturn(false);
            when(makeupTicketRepository.save(any(MakeupTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);
            makeupTicketService.requestAbsence(request);

            verify(makeupTicketRepository).save(any(MakeupTicket.class));
        }

        @Test
        void 학부모가_본인_자녀가_아니면_예외() {
            Member otherParent = new Member("other@test.com", "e", "다른학부모", "010-0000-0099", MemberRole.PARENT, academy);
            ReflectionTestUtils.setField(otherParent, "id", 99L);
            when(currentMemberProvider.getCurrentMember()).thenReturn(otherParent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("본인의 자녀");
        }

        @Test
        void 정규_요일이_아니면_예외() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));

            // nextTuesday는 화요일 — regularClass는 월요일반이므로 요일 불일치
            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextTuesday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("정규 요일이 아닙니다");
        }

        @Test
        void 시간표에_편성되지_않은_수강생이면_예외() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(regularClassStudentRepository.existsByRegularClass_IdAndAcademyStudent_Id(30L, 40L))
                    .thenReturn(false);

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("편성되지 않은");
        }

        @Test
        void 수강_기간_밖이면_예외() {
            AcademyStudent expiredStudent = new AcademyStudent(
                    academy, child, "아이", null, true, null, null, null, null, null);
            ReflectionTestUtils.setField(expiredStudent, "id", 40L);
            expiredStudent.updateEnrollmentPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(expiredStudent));
            when(regularClassStudentRepository.existsByRegularClass_IdAndAcademyStudent_Id(30L, 40L))
                    .thenReturn(true);

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("수강 기간에 포함되지 않습니다");
        }

        @Test
        void 월_발급_제한을_초과하는_결석_신청은_거부된다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(regularClassStudentRepository.existsByRegularClass_IdAndAcademyStudent_Id(30L, 40L))
                    .thenReturn(true);
            when(makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                    30L, 40L, nextMonday)).thenReturn(false);

            MakeupTicketPolicy policy = MakeupTicketPolicy.builder().academy(academy).monthlyIssueLimit(1).build();
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.of(policy));
            when(makeupTicketRepository.countByAcademyStudent_IdAndCreatedAtBetween(eq(40L), any(), any()))
                    .thenReturn(1L);

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이번 달");
            verify(makeupTicketRepository, never()).save(any());
        }

        @Test
        void 같은_날짜에_이미_결석처리_되어있으면_예외() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(regularClassStudentRepository.existsByRegularClass_IdAndAcademyStudent_Id(30L, 40L))
                    .thenReturn(true);
            when(makeupTicketRepository.existsByOriginClass_IdAndAcademyStudent_IdAndAbsentDate(
                    30L, 40L, nextMonday)).thenReturn(true);

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.requestAbsence(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 해당 날짜에 대한");
        }
    }

    @Nested
    class CancelAbsence {

        @Test
        void 미사용_티켓은_정상적으로_취소된다() {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent).originClass(regularClass)
                    .absentDate(nextMonday).source(MakeupTicketSource.STUDENT_ABSENCE)
                    .expiredAt(nextMonday.plusDays(60).atStartOfDay())
                    .build();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.STUDENT_ABSENCE))
                    .thenReturn(Optional.of(ticket));

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);
            makeupTicketService.cancelAbsence(request);

            verify(makeupTicketRepository).delete(ticket);
        }

        @Test
        void 이미_사용된_티켓은_취소할_수_없다() {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent).originClass(regularClass)
                    .absentDate(nextMonday).source(MakeupTicketSource.STUDENT_ABSENCE)
                    .expiredAt(nextMonday.plusDays(60).atStartOfDay())
                    .build();
            ticket.use();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(studentRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
            when(regularClassRepository.findByUuid(regularClass.getUuid())).thenReturn(Optional.of(regularClass));
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.STUDENT_ABSENCE))
                    .thenReturn(Optional.of(ticket));

            AbsenceRequest request = new AbsenceRequest(child.getUuid(), regularClass.getUuid(), nextMonday, false);

            assertThatThrownBy(() -> makeupTicketService.cancelAbsence(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 보강 신청에 사용");
            verify(makeupTicketRepository, never()).delete(any());
        }
    }

    @Nested
    class GrantTicketsManually {

        @Test
        void 요청한_수량만큼_보강권을_지급한다() {
            Member admin = new Member("admin@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));
            when(makeupTicketRepository.save(any(MakeupTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            ManualTicketGrantRequest request = new ManualTicketGrantRequest(child.getUuid(), 3, "초기 이관분", null, false);
            makeupTicketService.grantTicketsManually(1L, request);

            verify(makeupTicketRepository, times(3)).save(any(MakeupTicket.class));
        }

        @Test
        void 학부모는_수동_지급할_수_없다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);

            ManualTicketGrantRequest request = new ManualTicketGrantRequest(child.getUuid(), 1, null, null, false);

            assertThatThrownBy(() -> makeupTicketService.grantTicketsManually(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("원장/강사만");
            verify(makeupTicketRepository, never()).save(any());
        }

        @Test
        void 최대_보유_개수를_초과하는_수동_지급은_확인이_필요하다() {
            Member admin = new Member("admin@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));

            MakeupTicketPolicy policy = MakeupTicketPolicy.builder().academy(academy).maxOutstandingTickets(2).build();
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.of(policy));

            MakeupTicket existing1 = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
            MakeupTicket existing2 = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(existing1, existing2));

            ManualTicketGrantRequest request = new ManualTicketGrantRequest(child.getUuid(), 1, null, null, false);

            // 🎯 원장/강사는 학부모와 달리 무조건 차단이 아니라, "확인 후 강행할 수 있는" 전용 예외를 받는다.
            assertThatThrownBy(() -> makeupTicketService.grantTicketsManually(1L, request))
                    .isInstanceOf(MakeupTicketLimitExceededException.class)
                    .hasMessageContaining("최대 개수");
            verify(makeupTicketRepository, never()).save(any());
        }

        @Test
        void 최대_보유_개수를_초과해도_overrideLimit이면_지급된다() {
            Member admin = new Member("admin@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(child.getUuid(), 1L))
                    .thenReturn(Optional.of(academyStudent));

            MakeupTicketPolicy policy = MakeupTicketPolicy.builder().academy(academy).maxOutstandingTickets(2).build();
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.of(policy));

            MakeupTicket existing1 = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
            MakeupTicket existing2 = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(existing1, existing2));
            when(makeupTicketRepository.save(any(MakeupTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            ManualTicketGrantRequest request = new ManualTicketGrantRequest(child.getUuid(), 1, null, null, true);
            makeupTicketService.grantTicketsManually(1L, request);

            verify(makeupTicketRepository, times(1)).save(any(MakeupTicket.class));
        }

        @Test
        void 다른_학원의_수강생에게는_지급할_수_없다() {
            Academy otherAcademy = Academy.builder().name("다른학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member admin = new Member("admin@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, otherAcademy);

            when(currentMemberProvider.getCurrentMember()).thenReturn(admin);

            ManualTicketGrantRequest request = new ManualTicketGrantRequest(child.getUuid(), 1, null, null, false);

            assertThatThrownBy(() -> makeupTicketService.grantTicketsManually(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소속 학원의 수강생에게만");
        }
    }

    @Nested
    class RetractHolidayTicket {

        @Test
        void 미사용_티켓이면_삭제하고_true를_반환한다() {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent).originClass(regularClass).absentDate(nextMonday)
                    .source(MakeupTicketSource.ACADEMY_HOLIDAY).build();
            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.ACADEMY_HOLIDAY)).thenReturn(Optional.of(ticket));

            boolean result = makeupTicketService.retractHolidayTicket(academyStudent, regularClass, nextMonday, parent);

            assertThat(result).isTrue();
            verify(makeupTicketRepository).delete(ticket);
        }

        @Test
        void 발급된_티켓이_없으면_false를_반환한다() {
            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.ACADEMY_HOLIDAY)).thenReturn(Optional.empty());

            boolean result = makeupTicketService.retractHolidayTicket(academyStudent, regularClass, nextMonday, parent);

            assertThat(result).isFalse();
        }

        @Test
        void 사용된_티켓이고_매칭_대상날짜가_미래면_매칭을_취소하고_티켓을_복원한다() {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent).originClass(regularClass).absentDate(nextMonday)
                    .source(MakeupTicketSource.ACADEMY_HOLIDAY).build();
            ticket.use();

            RegularClass targetClass = RegularClass.builder()
                    .academy(academy).title("화요반").teacher(regularClass.getTeacher()).maxCapacity(10)
                    .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0))))
                    .build();
            ReflectionTestUtils.setField(targetClass, "id", 31L);

            MakeupRequest makeupRequest = MakeupRequest.builder()
                    .ticket(ticket).targetRegularClass(targetClass).targetDate(nextTuesday).build();
            makeupRequest.approve(parent);

            RegularClassSession targetSession = RegularClassSession.builder()
                    .regularClass(targetClass).date(nextTuesday).startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0))
                    .maxCapacity(10).holidayCancelled(false).build();
            ReflectionTestUtils.setField(targetSession, "id", 99L);

            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.ACADEMY_HOLIDAY)).thenReturn(Optional.of(ticket));
            when(makeupRequestRepository.findByTicket_IdAndStatus(ticket.getId(), MakeupRequestStatus.APPROVED))
                    .thenReturn(Optional.of(makeupRequest));
            when(regularClassSessionRepository.findByRegularClass_IdAndDate(31L, nextTuesday))
                    .thenReturn(Optional.of(targetSession));

            boolean result = makeupTicketService.retractHolidayTicket(academyStudent, regularClass, nextMonday, parent);

            assertThat(result).isTrue();
            assertThat(ticket.getStatus()).isEqualTo(MakeupTicketStatus.UNUSED);
            assertThat(makeupRequest.getStatus()).isEqualTo(MakeupRequestStatus.CANCELLED);
            verify(regularClassSessionStudentRepository).deleteBySession_IdAndAcademyStudent_Id(99L, 40L);
            verify(makeupTicketRepository, never()).delete(any());
            verify(eventPublisher).publishEvent(any(com.academy.reschedu.domain.notification.NotificationEvent.class));
        }

        @Test
        void 사용된_티켓이지만_매칭_대상날짜가_과거면_손대지_않는다() {
            MakeupTicket ticket = MakeupTicket.builder()
                    .academyStudent(academyStudent).originClass(regularClass).absentDate(nextMonday)
                    .source(MakeupTicketSource.ACADEMY_HOLIDAY).build();
            ticket.use();

            RegularClass targetClass = RegularClass.builder()
                    .academy(academy).title("화요반").teacher(regularClass.getTeacher()).maxCapacity(10)
                    .timeSlots(Set.of(new RegularClassTimeSlot(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0))))
                    .build();
            ReflectionTestUtils.setField(targetClass, "id", 31L);

            LocalDate pastTuesday = nextTuesday.minusWeeks(1);
            MakeupRequest makeupRequest = MakeupRequest.builder()
                    .ticket(ticket).targetRegularClass(targetClass).targetDate(pastTuesday).build();
            makeupRequest.approve(parent);

            when(makeupTicketRepository.findByOriginClass_IdAndAcademyStudent_IdAndAbsentDateAndSource(
                    30L, 40L, nextMonday, MakeupTicketSource.ACADEMY_HOLIDAY)).thenReturn(Optional.of(ticket));
            when(makeupRequestRepository.findByTicket_IdAndStatus(ticket.getId(), MakeupRequestStatus.APPROVED))
                    .thenReturn(Optional.of(makeupRequest));

            boolean result = makeupTicketService.retractHolidayTicket(academyStudent, regularClass, nextMonday, parent);

            assertThat(result).isFalse();
            assertThat(ticket.getStatus()).isEqualTo(MakeupTicketStatus.USED);
            assertThat(makeupRequest.getStatus()).isEqualTo(MakeupRequestStatus.APPROVED);
            verify(regularClassSessionStudentRepository, never()).deleteBySession_IdAndAcademyStudent_Id(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
