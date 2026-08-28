package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyHoliday;
import com.academy.reschedu.domain.academy.AcademyHolidayRepository;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.makeup.MakeupRequestRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketService;
import com.academy.reschedu.domain.makeup.MakeupTicketSource;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRepository;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.StudentRepository;
import com.academy.reschedu.domain.regularclass.dto.ChangeClassTeacherRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassCreateRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassUpdateRequest;
import com.academy.reschedu.domain.regularclass.dto.TimeSlotRequest;
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
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegularClassServiceTest {

    @Mock
    private RegularClassRepository regularClassRepository;
    @Mock
    private RegularClassStudentRepository regularClassStudentRepository;
    @Mock
    private RegularClassSessionRepository regularClassSessionRepository;
    @Mock
    private RegularClassSessionStudentRepository regularClassSessionStudentRepository;
    @Mock
    private AcademyRepository academyRepository;
    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private AcademyHolidayRepository academyHolidayRepository;
    @Mock
    private MakeupTicketRepository makeupTicketRepository;
    @Mock
    private MakeupRequestRepository makeupRequestRepository;
    @Mock
    private MakeupTicketService makeupTicketService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @InjectMocks
    private RegularClassService regularClassService;

    private Academy academy;
    private Member admin;
    private Member teacher;
    private UUID teacherUuid;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").address("서울").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        admin = new Member("admin@test.com", "encoded", "원장", "010-0000-0001", MemberRole.ADMIN, academy);
        ReflectionTestUtils.setField(admin, "id", 10L);
        // 🎯 인증 컨텍스트를 쓰지 않는 메서드(applyHolidayToSessions 등) 테스트도 있으므로 lenient 처리한다.
        lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);

        teacher = new Member("teacher@test.com", "encoded", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
        ReflectionTestUtils.setField(teacher, "id", 11L);
        teacherUuid = UUID.randomUUID();
        ReflectionTestUtils.setField(teacher, "uuid", teacherUuid);

        // 🎯 요청자 소속/시간 검증 등 academyRepository 조회 이전에 먼저 실패하는 테스트도 있으므로 lenient 처리한다.
        lenient().when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
    }

    private static final List<TimeSlotRequest> MON_WED_FRI_3PM = List.of(
            new TimeSlotRequest(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 0)),
            new TimeSlotRequest(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0), LocalTime.of(16, 0)),
            new TimeSlotRequest(DayOfWeek.FRIDAY, LocalTime.of(15, 0), LocalTime.of(16, 0)));

    private RegularClassCreateRequest createRequest(int maxCapacity, List<UUID> studentUuids) {
        return new RegularClassCreateRequest(
                "월수금반", teacherUuid, "301호", maxCapacity, MON_WED_FRI_3PM, studentUuids);
    }

    private RegularClass buildClass(int maxCapacity) {
        RegularClass regularClass = RegularClass.builder()
                .academy(academy).title("월수금반").teacher(teacher).roomNumber("301호")
                .maxCapacity(maxCapacity)
                .timeSlots(java.util.Set.of(
                        new RegularClassTimeSlot(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 0)),
                        new RegularClassTimeSlot(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0), LocalTime.of(16, 0)),
                        new RegularClassTimeSlot(DayOfWeek.FRIDAY, LocalTime.of(15, 0), LocalTime.of(16, 0))))
                .build();
        ReflectionTestUtils.setField(regularClass, "id", 500L);
        return regularClass;
    }

    private long nextAcademyStudentId = 700L;

    /**
     * 🚨 updateRegularClass의 로스터 제거 로직은 academyStudent.getStudent().getUuid()를 그대로
     * 역참조하므로, student가 null이면 NPE가 난다 — 반드시 실제 Student를 채운 AcademyStudent를 만든다.
     * (레포지토리 조회 스텁은 걸지 않는다 — 이미 로스터에 있어 재조회가 필요 없는 시나리오에서
     * strict-stubbing이 "사용되지 않은 스텁"으로 실패시키기 때문에, 필요한 테스트에서만 별도로 stubFind한다.)
     */
    private AcademyStudent activeStudentFixture(UUID studentUuid) {
        com.academy.reschedu.domain.member.Student student =
                new com.academy.reschedu.domain.member.Student("학생", null, "MALE", null, admin);
        ReflectionTestUtils.setField(student, "uuid", studentUuid);
        AcademyStudent academyStudent = new AcademyStudent(
                academy, student, "학생", null, true, null, null, null, null, null);
        ReflectionTestUtils.setField(academyStudent, "id", nextAcademyStudentId++);
        return academyStudent;
    }

    /** 신규로 로스터에 추가되는(=enrollStudent가 실제로 조회하는) 시나리오 전용: 조회 스텁까지 함께 건다. */
    private AcademyStudent activeStudent(UUID studentUuid) {
        AcademyStudent academyStudent = activeStudentFixture(studentUuid);
        when(academyStudentRepository.findByStudentUuidAndAcademyId(eq(studentUuid), eq(1L)))
                .thenReturn(Optional.of(academyStudent));
        return academyStudent;
    }

    // ─────────────────────────── createRegularClass ───────────────────────────

    @Nested
    class CreateRegularClass {

        @Test
        void 종료시간이_시작시간보다_빠르면_예외() {
            RegularClassCreateRequest request = new RegularClassCreateRequest(
                    "제목", teacherUuid, null, 10,
                    List.of(new TimeSlotRequest(DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(15, 0))), null);

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 요청자가_소속되지_않은_학원이면_예외() {
            Academy otherAcademy = Academy.builder().name("다른학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member otherAdmin = new Member("other@test.com", "e", "원장2", "010-0000-0009", MemberRole.ADMIN, otherAcademy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(otherAdmin);

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, createRequest(10, null)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 존재하지_않는_강사면_예외() {
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, createRequest(10, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 담당강사가_강사_권한이_아니면_예외() {
            Member notTeacher = new Member("parent@test.com", "e", "학부모", "010-0000-0008", MemberRole.PARENT, academy);
            ReflectionTestUtils.setField(notTeacher, "uuid", teacherUuid);
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.of(notTeacher));

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, createRequest(10, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("해당 학원 소속 강사만");
        }

        @Test
        void 담당강사가_다른_학원_소속이면_예외() {
            Academy otherAcademy = Academy.builder().name("다른학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member foreignTeacher = new Member("t2@test.com", "e", "강사2", "010-0000-0007", MemberRole.TEACHER, otherAcademy);
            ReflectionTestUtils.setField(foreignTeacher, "uuid", teacherUuid);
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.of(foreignTeacher));

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, createRequest(10, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("해당 학원 소속 강사만");
        }

        @Test
        void 정원이_가득_찬_상태에서_학생을_추가하면_예외() {
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.of(teacher));
            // 🚨 save()는 Mockito 목이라 실제 @GeneratedValue id를 채워주지 않으므로, 이후 로스터 조회 스텁이
            // regularClass.getId() 기준으로 정확히 매칭되도록 side effect로 직접 id를 부여해야 한다.
            when(regularClassRepository.save(any(RegularClass.class))).thenAnswer(invocation -> {
                RegularClass saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 500L);
                return saved;
            });
            when(regularClassSessionRepository.findByRegularClass_Id(anyLong())).thenReturn(List.of());

            UUID firstStudentUuid = UUID.randomUUID();
            UUID secondStudentUuid = UUID.randomUUID();
            AcademyStudent first = activeStudent(firstStudentUuid);
            AcademyStudent second = activeStudent(secondStudentUuid);
            // 🚨 enrollStudent의 "이미 등록됨" 판정이 이제 existsBy...가 아니라 이력 조회 기반이므로,
            // 두 학생 모두 과거 이력이 없는 것으로 스텁한다.
            when(regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(first.getId())).thenReturn(List.of());
            when(regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(second.getId())).thenReturn(List.of());

            // 🎯 정원 1명인 반에 학생을 순서대로 등록: 첫 학생 등록 시점엔 로스터가 비어 있어 통과하고,
            // 두 번째 학생 등록 시점엔 첫 학생이 이미 반영된 것으로 스텁을 바꿔치기해 정원 초과를 재현한다.
            RegularClassStudent firstEnrollment = new RegularClassStudent(null, first);
            when(regularClassStudentRepository.findByRegularClass_Id(anyLong()))
                    .thenReturn(List.of())
                    .thenReturn(List.of(firstEnrollment));

            RegularClassCreateRequest request = createRequest(1, List.of(firstStudentUuid, secondStudentUuid));

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정원을 초과");
        }

        @Test
        void 승인되지_않은_수강생은_등록할_수_없다() {
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.of(teacher));
            UUID studentUuid = UUID.randomUUID();
            // 🚨 예외 메시지가 academyStudent.getStudent().getName()을 그대로 참조하므로 student는 반드시 채워야 한다.
            com.academy.reschedu.domain.member.Student student =
                    new com.academy.reschedu.domain.member.Student("미승인학생", null, "MALE", null, admin);
            ReflectionTestUtils.setField(student, "uuid", studentUuid);
            AcademyStudent unapproved = new AcademyStudent(
                    academy, student, "미승인학생", null, false, null, null, null, null, null);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(eq(studentUuid), eq(1L)))
                    .thenReturn(Optional.of(unapproved));

            assertThatThrownBy(() -> regularClassService.createRegularClass(1L, createRequest(10, List.of(studentUuid))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("가입 승인이 완료되지 않은");
        }
    }

    // ─────────────────────────── updateRegularClass ───────────────────────────

    @Nested
    class UpdateRegularClass {

        @Test
        void 현재_유효_인원보다_작게_정원을_줄이면_예외() {
            RegularClass existing = buildClass(10);
            when(regularClassRepository.findByUuidAndAcademyId(any(), eq(1L))).thenReturn(Optional.of(existing));
            when(memberRepository.findByUuid(teacherUuid)).thenReturn(Optional.of(teacher));

            // 🎯 기존 활성 학생 2명을 그대로 유지(desired 목록에 둘 다 포함)한 채 정원만 1명으로 줄이는 시나리오.
            // 아무도 제거되지 않으므로 "제거 후 재계산" 로직이 실제 삭제를 흉내 낼 필요가 없어 목 설정이 단순해진다.
            UUID studentUuid1 = UUID.randomUUID();
            UUID studentUuid2 = UUID.randomUUID();
            AcademyStudent student1 = activeStudentFixture(studentUuid1);
            AcademyStudent student2 = activeStudentFixture(studentUuid2);
            RegularClassStudent enrollment1 = new RegularClassStudent(existing, student1);
            RegularClassStudent enrollment2 = new RegularClassStudent(existing, student2);
            when(regularClassStudentRepository.findByRegularClass_Id(500L))
                    .thenReturn(List.of(enrollment1, enrollment2));

            RegularClassUpdateRequest request = new RegularClassUpdateRequest(
                    "제목", teacherUuid, null, 1,
                    List.of(new TimeSlotRequest(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 0))),
                    List.of(studentUuid1, studentUuid2));

            assertThatThrownBy(() -> regularClassService.updateRegularClass(1L, existing.getUuid(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정원을 설정할 수 없습니다");
        }
    }

    // ─────────────────────────── changeClassTeacher ───────────────────────────

    @Nested
    class ChangeClassTeacher {

        /**
         * 🎯 재현: 강사1→강사2 인계(효력일 X) 직후, 같은 효력일로 강사2→강사3 인계를 한 번 더 걸었을 때
         * 두 번째 인계에서도 새 강사(강사3)가 AcademyStudent.teacher에 실제로 반영되어야 한다.
         *
         * 버그였던 지점: 첫 인계로 새로 만들어진 배정(RegularClassStudent)은 startDate가 effectiveFrom(미래)
         * 이므로, 두 번째 인계의 로스터 조회가 예전처럼 isActiveOn(effectiveFrom-1일)로 필터링하면 그 아직
         * 시작 안 한 배정이 통째로 걸러져 로스터가 비어 버리고, 반복문 자체가 돌지 않아 scheduleTeacherHandover가
         * 호출되지 않는다 — 즉 강사3의 [수강생 관리] 목록에 학생이 나타나지 않는다.
         */
        @Test
        void 연속된_담당강사_인계_두번째_인계에서도_새_강사가_반영된다() {
            Member teacher2 = new Member("t2@test.com", "e", "강사2", "010-0000-0003", MemberRole.TEACHER, academy);
            ReflectionTestUtils.setField(teacher2, "id", 12L);
            UUID teacher2Uuid = UUID.randomUUID();
            ReflectionTestUtils.setField(teacher2, "uuid", teacher2Uuid);

            Member teacher3 = new Member("t3@test.com", "e", "강사3", "010-0000-0004", MemberRole.TEACHER, academy);
            ReflectionTestUtils.setField(teacher3, "id", 13L);
            UUID teacher3Uuid = UUID.randomUUID();
            ReflectionTestUtils.setField(teacher3, "uuid", teacher3Uuid);

            RegularClass fromClass = buildClass(10); // teacher(=강사1)의 반, id=500L
            ReflectionTestUtils.setField(fromClass, "uuid", UUID.randomUUID()); // @UuidGenerator는 목 save()로는 안 채워짐
            UUID studentUuid = UUID.randomUUID();
            AcademyStudent academyStudent = activeStudent(studentUuid);
            ReflectionTestUtils.setField(academyStudent, "teacher", teacher); // 처음엔 강사1이 담당

            // 🚨 실제 DB처럼 동작하는 간이 in-memory 저장소: 반(uuid→엔티티), 학생의 반 편성 이력(list).
            Map<UUID, RegularClass> classesByUuid = new HashMap<>();
            classesByUuid.put(fromClass.getUuid(), fromClass);
            List<RegularClassStudent> enrollmentHistory = new ArrayList<>();
            enrollmentHistory.add(new RegularClassStudent(fromClass, academyStudent));

            AtomicLong nextClassId = new AtomicLong(501L);
            when(regularClassRepository.save(any(RegularClass.class))).thenAnswer(invocation -> {
                // 🚨 uuid는 @UuidGenerator(Hibernate 전용)로 채워지는데, 목(mock) save()로는 실제 영속화가
                // 일어나지 않아 절대 채워지지 않는다 — 테스트에서 직접 uuid를 부여해줘야 한다.
                RegularClass saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", nextClassId.getAndIncrement());
                ReflectionTestUtils.setField(saved, "uuid", UUID.randomUUID());
                classesByUuid.put(saved.getUuid(), saved);
                return saved;
            });
            when(regularClassRepository.findByUuidAndAcademyId(any(UUID.class), eq(1L)))
                    .thenAnswer(invocation -> Optional.ofNullable(classesByUuid.get((UUID) invocation.getArgument(0))));
            when(regularClassRepository.findByUuid(any(UUID.class)))
                    .thenAnswer(invocation -> Optional.ofNullable(classesByUuid.get((UUID) invocation.getArgument(0))));
            when(regularClassRepository.findByAcademyIdAndTeacher_Uuid(eq(1L), eq(teacher2Uuid))).thenReturn(new ArrayList<>());
            when(regularClassRepository.findByAcademyIdAndTeacher_Uuid(eq(1L), eq(teacher3Uuid))).thenReturn(new ArrayList<>());
            when(memberRepository.findByUuid(teacher2Uuid)).thenReturn(Optional.of(teacher2));
            when(memberRepository.findByUuid(teacher3Uuid)).thenReturn(Optional.of(teacher3));

            when(regularClassStudentRepository.findByRegularClass_Id(anyLong())).thenAnswer(invocation -> {
                Long classId = invocation.getArgument(0);
                return enrollmentHistory.stream()
                        .filter(rcs -> rcs.getRegularClass().getId().equals(classId))
                        .toList();
            });
            when(regularClassStudentRepository.findByAcademyStudent_IdOrderByStartDateDesc(academyStudent.getId()))
                    .thenReturn(enrollmentHistory);
            when(regularClassStudentRepository.save(any(RegularClassStudent.class))).thenAnswer(invocation -> {
                RegularClassStudent saved = invocation.getArgument(0);
                enrollmentHistory.add(saved);
                return saved;
            });
            doAnswer(invocation -> {
                enrollmentHistory.remove((RegularClassStudent) invocation.getArgument(0));
                return null;
            }).when(regularClassStudentRepository).delete(any(RegularClassStudent.class));
            when(regularClassSessionRepository.findByRegularClass_Id(anyLong())).thenReturn(List.of());

            // 오늘로부터 충분히 먼 미래 날짜 — assignStudentSchedule의 "오늘 기준 정원" 체크를 피해간다.
            LocalDate effectiveFrom = LocalDate.now().plusMonths(6);

            regularClassService.changeClassTeacher(1L, fromClass.getUuid(),
                    new ChangeClassTeacherRequest(teacher2Uuid, effectiveFrom));
            assertThat(academyStudent.getTeacher().getId()).isEqualTo(teacher2.getId());

            RegularClass toClass1 = classesByUuid.values().stream()
                    .filter(rc -> rc.getTeacher().getId().equals(teacher2.getId()))
                    .findFirst().orElseThrow();

            regularClassService.changeClassTeacher(1L, toClass1.getUuid(),
                    new ChangeClassTeacherRequest(teacher3Uuid, effectiveFrom));

            assertThat(academyStudent.getTeacher().getId()).isEqualTo(teacher3.getId());
            assertThat(academyStudent.getPreviousTeacher().getId()).isEqualTo(teacher2.getId());
        }
    }

    @Nested
    class ApplyHolidayToSessions {

        @Test
        void issueMakeupTickets가_true면_보강권을_발급한다() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            RegularClass regularClass = buildClass(10);
            AcademyStudent student = activeStudentFixture(UUID.randomUUID());
            AcademyHoliday holiday = AcademyHoliday.builder()
                    .academy(academy).date(monday).issueMakeupTickets(true).build();

            when(regularClassRepository.findByAcademyId(1L)).thenReturn(List.of(regularClass));
            when(regularClassSessionRepository.findByRegularClass_IdAndDate(500L, monday)).thenReturn(Optional.empty());
            when(academyHolidayRepository.findByAcademyIdAndDate(1L, monday)).thenReturn(Optional.of(holiday));
            when(regularClassStudentRepository.findByRegularClass_Id(500L))
                    .thenReturn(List.of(new RegularClassStudent(regularClass, student)));
            when(makeupTicketService.issueTicketIfNeeded(student, regularClass, monday, MakeupTicketSource.ACADEMY_HOLIDAY))
                    .thenReturn(true);

            int issuedCount = regularClassService.applyHolidayToSessions(academy, monday);

            assertThat(issuedCount).isEqualTo(1);
        }

        @Test
        void issueMakeupTickets가_false면_보강권을_발급하지_않는다() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            RegularClass regularClass = buildClass(10);
            AcademyStudent student = activeStudentFixture(UUID.randomUUID());
            AcademyHoliday holiday = AcademyHoliday.builder()
                    .academy(academy).date(monday).issueMakeupTickets(false).build();

            when(regularClassRepository.findByAcademyId(1L)).thenReturn(List.of(regularClass));
            when(regularClassSessionRepository.findByRegularClass_IdAndDate(500L, monday)).thenReturn(Optional.empty());
            when(academyHolidayRepository.findByAcademyIdAndDate(1L, monday)).thenReturn(Optional.of(holiday));
            when(regularClassStudentRepository.findByRegularClass_Id(500L))
                    .thenReturn(List.of(new RegularClassStudent(regularClass, student)));

            int issuedCount = regularClassService.applyHolidayToSessions(academy, monday);

            assertThat(issuedCount).isEqualTo(0);
        }
    }
}
