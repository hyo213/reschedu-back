package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.member.dto.LoginRequest;
import com.academy.reschedu.domain.member.dto.MyProfileUpdateRequest;
import com.academy.reschedu.domain.member.dto.SignUpRequest;
import com.academy.reschedu.domain.member.dto.StudentManualRegisterRequest;
import com.academy.reschedu.domain.member.dto.StudentTeacherHandoverRequest;
import com.academy.reschedu.domain.member.dto.StudentUpdateRequest;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import com.academy.reschedu.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private AcademyRepository academyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private EmailAuthService emailAuthService;
    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private RegularClassService regularClassService;
    @Mock
    private EnrollmentPeriodHistoryRepository enrollmentPeriodHistoryRepository;
    @Mock
    private com.academy.reschedu.global.security.LoginAttemptService loginAttemptService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberService memberService;

    private Academy academy;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").address("서울").build();
        ReflectionTestUtils.setField(academy, "id", 1L);
    }

    private Member member(MemberRole role, Academy academy, String email, String phone, String encodedPassword) {
        Member member = new Member(email, encodedPassword, "홍길동", phone, role, academy);
        ReflectionTestUtils.setField(member, "id", 100L);
        return member;
    }

    // ─────────────────────────────── signUp ───────────────────────────────

    @Nested
    class SignUp {

        @Test
        void 원장으로_가입하면_자동으로_승인된다() {
            SignUpRequest request = new SignUpRequest(
                    "admin@test.com", "password123", "원장님", MemberRole.ADMIN, 1L, "010-1111-2222", null);

            when(memberRepository.existsByEmail("admin@test.com")).thenReturn(false);
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(passwordEncoder.encode("password123")).thenReturn("encoded");
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            memberService.signUp(request);

            verify(memberRepository).save(argThatApproved());
        }

        private Member argThatApproved() {
            return org.mockito.ArgumentMatchers.argThat(m -> m != null && m.isApproved());
        }

        @Test
        void 이미_존재하는_이메일이면_예외() {
            SignUpRequest request = new SignUpRequest(
                    "dup@test.com", "password123", "강사", MemberRole.TEACHER, 1L, "010-1111-3333", null);
            when(memberRepository.existsByEmail("dup@test.com")).thenReturn(true);

            assertThatThrownBy(() -> memberService.signUp(request))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 존재하지_않는_학원이면_예외() {
            SignUpRequest request = new SignUpRequest(
                    "teacher@test.com", "password123", "강사", MemberRole.TEACHER, 999L, "010-1111-4444", null);
            when(memberRepository.existsByEmail("teacher@test.com")).thenReturn(false);
            when(academyRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.signUp(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────── login ───────────────────────────────

    @Nested
    class Login {

        @Test
        void 정상_로그인시_토큰을_발급한다() {
            Member admin = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            admin.approve();
            when(memberRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
            when(jwtTokenProvider.createAccessToken(anyString(), any())).thenReturn("token");

            var response = memberService.login(new LoginRequest("admin@test.com", "password123"));

            assertThat(response.accessToken()).isEqualTo("token");
        }

        @Test
        void 비밀번호가_틀리면_예외() {
            Member admin = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            when(memberRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> memberService.login(new LoginRequest("admin@test.com", "wrong")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 승인되지_않은_강사는_로그인할_수_없다() {
            Member teacher = member(MemberRole.TEACHER, academy, "teacher@test.com", "010-0000-0002", "encoded");
            // isApproved 기본값 false — approve() 호출하지 않음
            when(memberRepository.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacher));
            when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);

            assertThatThrownBy(() -> memberService.login(new LoginRequest("teacher@test.com", "password123")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 승인되지_않아도_학부모는_로그인할_수_있다() {
            Member parent = member(MemberRole.PARENT, academy, "parent@test.com", "010-0000-0003", "encoded");
            when(memberRepository.findByEmail("parent@test.com")).thenReturn(Optional.of(parent));
            when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
            when(jwtTokenProvider.createAccessToken(anyString(), any())).thenReturn("token");

            var response = memberService.login(new LoginRequest("parent@test.com", "password123"));

            assertThat(response.accessToken()).isEqualTo("token");
        }
    }

    // ─────────────────────────── updateMyProfile ───────────────────────────

    @Nested
    class UpdateMyProfile {

        @Test
        void 이름과_연락처만_변경한다() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);

            memberService.updateMyProfile(new MyProfileUpdateRequest("새이름", "010-2000-0000", null, null));

            assertThat(me.getName()).isEqualTo("새이름");
            assertThat(me.getPhone()).isEqualTo("010-2000-0000");
        }

        @Test
        void 연락처를_바꾸지_않으면_중복검사를_하지_않는다() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);

            memberService.updateMyProfile(new MyProfileUpdateRequest("새이름", "010-1000-0000", null, null));

            verify(memberRepository, never()).existsByPhone(anyString());
        }

        @Test
        void 이미_사용중인_연락처면_예외() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);
            when(memberRepository.existsByPhone("010-9999-9999")).thenReturn(true);

            assertThatThrownBy(() -> memberService.updateMyProfile(
                    new MyProfileUpdateRequest("새이름", "010-9999-9999", null, null)))
                    .isInstanceOf(IllegalStateException.class);

            // 🚨 중복 검증은 updateBasicInfo 반영보다 먼저 이뤄져야 한다 — 실패 시 기존 정보가 그대로 유지되어야 한다.
            assertThat(me.getPhone()).isEqualTo("010-1000-0000");
            assertThat(me.getName()).isEqualTo("홍길동");
        }

        @Test
        void 현재_비밀번호_없이_새_비밀번호만_보내면_예외() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);

            assertThatThrownBy(() -> memberService.updateMyProfile(
                    new MyProfileUpdateRequest("새이름", "010-1000-0000", null, "newpassword123")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 현재_비밀번호가_틀리면_예외() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);
            when(passwordEncoder.matches("wrongCurrent", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> memberService.updateMyProfile(
                    new MyProfileUpdateRequest("새이름", "010-1000-0000", "wrongCurrent", "newpassword123")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 새_비밀번호가_8자_미만이면_예외() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);
            when(passwordEncoder.matches("correctCurrent", "encoded")).thenReturn(true);

            assertThatThrownBy(() -> memberService.updateMyProfile(
                    new MyProfileUpdateRequest("새이름", "010-1000-0000", "correctCurrent", "short")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 정상적으로_비밀번호를_변경한다() {
            Member me = member(MemberRole.ADMIN, academy, "me@test.com", "010-1000-0000", "encoded");
            when(currentMemberProvider.getCurrentMember()).thenReturn(me);
            when(passwordEncoder.matches("correctCurrent", "encoded")).thenReturn(true);
            when(passwordEncoder.encode("newpassword123")).thenReturn("newEncoded");

            memberService.updateMyProfile(new MyProfileUpdateRequest(
                    "새이름", "010-1000-0000", "correctCurrent", "newpassword123"));

            assertThat(me.getPassword()).isEqualTo("newEncoded");
        }
    }

    // ─────────────────── 담당 강사 지정 검증 (교차 학원/권한 회귀 테스트) ───────────────────

    @Nested
    class TeacherAssignmentGuard {

        @Test
        void 학생_정보_수정시_다른_학원_소속_강사는_지정할_수_없다() {
            Academy otherAcademy = Academy.builder().name("다른 학원").address("부산").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member requester = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            Member foreignTeacher = member(MemberRole.TEACHER, otherAcademy, "other@test.com", "010-0000-0099", "encoded");
            UUID foreignTeacherUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(foreignTeacher, "uuid", foreignTeacherUuid);

            AcademyStudent registration = new AcademyStudent(academy, null, "관리명", null, true, null, null, null, null, null);
            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(any(), eq(1L)))
                    .thenReturn(Optional.of(registration));
            when(memberRepository.findByUuid(foreignTeacherUuid)).thenReturn(Optional.of(foreignTeacher));

            StudentTeacherHandoverRequest request =
                    new StudentTeacherHandoverRequest(foreignTeacherUuid, LocalDate.now());

            assertThatThrownBy(() -> memberService.scheduleStudentTeacherHandover(1L, UUID.randomUUID(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("해당 학원 소속 강사만");
        }

        @Test
        void 학생_정보_수정시_강사가_아닌_회원은_담당_강사로_지정할_수_없다() {
            Member requester = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            Member notATeacher = member(MemberRole.PARENT, academy, "parent@test.com", "010-0000-0098", "encoded");
            UUID notATeacherUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(notATeacher, "uuid", notATeacherUuid);

            AcademyStudent registration = new AcademyStudent(academy, null, "관리명", null, true, null, null, null, null, null);
            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(academyStudentRepository.findByStudentUuidAndAcademyId(any(), eq(1L)))
                    .thenReturn(Optional.of(registration));
            when(memberRepository.findByUuid(notATeacherUuid)).thenReturn(Optional.of(notATeacher));

            StudentTeacherHandoverRequest request =
                    new StudentTeacherHandoverRequest(notATeacherUuid, LocalDate.now());

            assertThatThrownBy(() -> memberService.scheduleStudentTeacherHandover(1L, UUID.randomUUID(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("해당 학원 소속 강사만");
        }

        @Test
        void 수동_등록시_다른_학원_소속_강사는_지정할_수_없다() {
            Academy otherAcademy = Academy.builder().name("다른 학원").address("부산").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member requester = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            Member foreignTeacher = member(MemberRole.TEACHER, otherAcademy, "other@test.com", "010-0000-0099", "encoded");
            UUID foreignTeacherUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(foreignTeacher, "uuid", foreignTeacherUuid);

            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(memberRepository.findByPhone("010-3000-0001")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(studentRepository.findByParentIdAndNameAndBirthDate(any(), any(), any())).thenReturn(Optional.empty());
            when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(academyStudentRepository.existsByAcademyIdAndStudent_Id(eq(1L), any())).thenReturn(false);
            when(memberRepository.findByUuid(foreignTeacherUuid)).thenReturn(Optional.of(foreignTeacher));

            StudentManualRegisterRequest request = new StudentManualRegisterRequest(
                    "학부모", "010-3000-0001", "temp12345",
                    "학생", LocalDate.of(2015, 1, 1), "MALE", null,
                    "학생", foreignTeacherUuid, null, null, null, null, null,
                    LocalDate.now(), LocalDate.now().plusYears(1));

            assertThatThrownBy(() -> memberService.registerStudentManual(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("해당 학원 소속 강사만");
        }
    }

    // ─────────────────────────── approveMember ───────────────────────────

    @Nested
    class ApproveMember {

        @Test
        void 원장이_아니면_강사를_승인할_수_없다() {
            Member requester = member(MemberRole.TEACHER, academy, "teacher@test.com", "010-0000-0001", "encoded");
            Member target = member(MemberRole.TEACHER, academy, "newteacher@test.com", "010-0000-0002", "encoded");
            UUID targetUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(target, "uuid", targetUuid);

            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(memberRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> memberService.approveMember(targetUuid))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 다른_학원_소속_강사는_승인할_수_없다() {
            Academy otherAcademy = Academy.builder().name("다른 학원").address("부산").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member requester = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            Member target = member(MemberRole.TEACHER, otherAcademy, "newteacher@test.com", "010-0000-0002", "encoded");
            UUID targetUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(target, "uuid", targetUuid);

            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(memberRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> memberService.approveMember(targetUuid))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 같은_학원_강사는_원장이_승인할_수_있다() {
            Member requester = member(MemberRole.ADMIN, academy, "admin@test.com", "010-0000-0001", "encoded");
            Member target = member(MemberRole.TEACHER, academy, "newteacher@test.com", "010-0000-0002", "encoded");
            UUID targetUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(target, "uuid", targetUuid);

            when(currentMemberProvider.getCurrentMember()).thenReturn(requester);
            when(memberRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));

            memberService.approveMember(targetUuid);

            assertThat(target.isApproved()).isTrue();
        }
    }
}
