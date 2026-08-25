package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.academy.dto.AcademySummaryResponse;
import com.academy.reschedu.domain.member.dto.*;
import com.academy.reschedu.domain.notification.NotificationEvent;
import com.academy.reschedu.domain.notification.NotificationType;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.domain.regularclass.dto.ScheduleHistoryResponse;
import com.academy.reschedu.domain.regularclass.dto.ScheduleSummary;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import com.academy.reschedu.global.security.LoginAttemptService;
import com.academy.reschedu.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final AcademyStudentRepository academyStudentRepository;
    private final AcademyRepository academyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailAuthService emailAuthService;
    private final CurrentMemberProvider currentMemberProvider;
    private final RegularClassService regularClassService;
    private final EnrollmentPeriodHistoryRepository enrollmentPeriodHistoryRepository;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UUID signUp(SignUpRequest request) {
        validateDuplicateEmail(request.email());

        Academy academy = academyRepository.findById(request.academyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원(센터)입니다."));

        String encodedPassword = passwordEncoder.encode(request.password());

        Member member = request.toMemberEntity(encodedPassword, academy);
        if (request.role() == MemberRole.ADMIN) {
            member.approve();
        }
        Member savedMember = memberRepository.save(member);

        if (request.role() == MemberRole.TEACHER) {
            eventPublisher.publishEvent(NotificationEvent.toAcademyRoles(
                    NotificationType.TEACHER_SIGNUP_PENDING,
                    savedMember.getName() + " 강사님의 가입 승인 요청이 있습니다.",
                    "/dashboard/teachers",
                    academy.getId(),
                    List.of(MemberRole.ADMIN)
            ));
        }

        if (request.role() == MemberRole.PARENT) {
            request.validateParentFields();

            for (SignUpRequest.ChildInfo childDto : request.children()) {
                Student student = childDto.toStudentEntity(savedMember);
                studentRepository.save(student);

                AcademyStudent academyStudent = childDto.toAcademyStudentEntity(academy, student);
                academyStudentRepository.save(academyStudent);

                eventPublisher.publishEvent(NotificationEvent.toAcademyRoles(
                        NotificationType.STUDENT_ENROLLMENT_PENDING,
                        student.getName() + " 학생의 수강 등록 승인 요청이 있습니다.",
                        "/dashboard/students",
                        academy.getId(),
                        List.of(MemberRole.ADMIN, MemberRole.TEACHER)
                ));
            }
        }

        return savedMember.getUuid();
    }

    public void validateDuplicateEmail(String email) {
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 존재하는 이메일입니다.");
        }
    }

    @Transactional
    public void sendVerificationEmail(String email) {
        validateDuplicateEmail(email);
        emailAuthService.sendAuthCode(email);
    }

    @Transactional
    public void verifyEmailCode(String email, String code) {
        boolean isVerified = emailAuthService.verifyAuthCode(email, code);
        if (!isVerified) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }
    }

    public LoginResult login(LoginRequest request) {
        String requestedLoginId = request.loginId();
        loginAttemptService.checkNotLocked(requestedLoginId);

        Member member = memberRepository.findByEmail(requestedLoginId)
                .or(() -> memberRepository.findByPhone(requestedLoginId))
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(requestedLoginId);
                    return new IllegalStateException("아이디 또는 비밀번호가 일치하지 않습니다.");
                });

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            loginAttemptService.recordFailure(requestedLoginId);
            throw new IllegalStateException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        loginAttemptService.recordSuccess(requestedLoginId);

        if (member.getRole() != MemberRole.PARENT && !member.isApproved()) {
            throw new IllegalStateException("학원 관리자의 가입 승인이 완료되지 않았습니다.");
        }

        String tokenSubject = member.getEmail() != null ? member.getEmail() : member.getPhone();
        String accessToken = jwtTokenProvider.createAccessToken(tokenSubject, member.getRole());

        return new LoginResult(LoginResponse.of(member), accessToken);
    }

    @Transactional
    public UUID registerStudentManual(Long academyId, StudentManualRegisterRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        validateManagerRole(requester);

        Academy academy = getAcademyOrThrow(academyId);

        Member parent = memberRepository.findByPhone(request.parentPhone())
                .orElseGet(() -> {
                    String encodedTempPassword = passwordEncoder.encode(request.temporaryPassword());
                    Member newParent = Member.createPreRegisteredParent(
                            request.parentName(),
                            request.parentPhone(),
                            encodedTempPassword
                    );
                    return memberRepository.save(newParent);
                });

        Student student = studentRepository.findByParentIdAndNameAndBirthDate(parent.getId(), request.name(), request.birthDate())
                .orElseGet(() -> {
                    Student newStudent = new Student(
                            request.name(),
                            request.birthDate(),
                            request.gender(),
                            request.childPhone(),
                            parent
                    );
                    return studentRepository.save(newStudent);
                });

        if (academyStudentRepository.existsByAcademyIdAndStudent_Id(academyId, student.getId())) {
            throw new IllegalStateException("이미 해당 학원에 등록된 수강생입니다.");
        }

        Member teacher = null;
        if (request.teacherUuid() != null) {
            teacher = resolveTeacher(academyId, request.teacherUuid());
        }

        AcademyStudent academyStudent = new AcademyStudent(
                academy,
                student,
                request.managementName(),
                teacher,
                true,
                request.schoolName(),
                request.shuttlePickupLocation(),
                request.shuttleDropoffLocation(),
                request.discountType(),
                request.memo()
        );

        // 수강 기간은 컨트롤러의 @Valid로 항상 채워져 있다.
        validateEnrollmentPeriod(request.enrollmentStartDate(), request.enrollmentEndDate());
        academyStudent.updateEnrollmentPeriod(request.enrollmentStartDate(), request.enrollmentEndDate());

        academyStudentRepository.save(academyStudent);
        return student.getUuid();
    }

    /**
     * 학부모 전용: 본인 자녀 목록 조회 (결석 신청 시 자녀 선택 등에 사용).
     */
    public List<MyChildResponse> getMyChildren() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있습니다.");
        }
        return studentRepository.findByParentId(parent.getId()).stream()
                .map(MyChildResponse::from)
                .toList();
    }

    /**
     * 학부모 전용: [내 계정 정보] 화면에서 자녀 정보를 추가/수정하기 위한 상세 목록 조회.
     * 원내 관리 필드(담당강사/셔틀/할인/메모/수강기간)는 노출하지 않는다.
     */
    public List<MyChildDetailResponse> getMyChildrenDetail() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있습니다.");
        }
        List<AcademyStudent> registrations = academyStudentRepository.findByStudent_Parent_Id(parent.getId());
        Map<Long, AcademyStudent> firstRegistrationByStudentId = new LinkedHashMap<>();
        Map<Long, List<AcademyStudent>> registrationsByStudentId = new LinkedHashMap<>();
        for (AcademyStudent registration : registrations) {
            Long studentId = registration.getStudent().getId();
            firstRegistrationByStudentId.putIfAbsent(studentId, registration);
            registrationsByStudentId.computeIfAbsent(studentId, k -> new ArrayList<>()).add(registration);
        }
        return studentRepository.findByParentId(parent.getId()).stream()
                .map(student -> {
                    AcademyStudent registration = firstRegistrationByStudentId.get(student.getId());
                    List<ChildAcademyRegistration> academies = registrationsByStudentId
                            .getOrDefault(student.getId(), List.of()).stream()
                            .map(ChildAcademyRegistration::from)
                            .toList();
                    return MyChildDetailResponse.of(
                            student,
                            registration != null ? registration.getSchoolName() : null,
                            registration == null || registration.isApproved(),
                            academies
                    );
                })
                .toList();
    }

    /** 학부모 전용: 이미 등록된 자녀를 다른 학원에도 다니게 한다(새 학원 등록만 승인 대기로 추가). */
    @Transactional
    public UUID addAcademyToMyChild(UUID studentUuid, ChildAcademyAddRequest request) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 이용할 수 있습니다.");
        }

        Student student = studentRepository.findByUuid(studentUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자녀 정보입니다."));
        if (!student.getParent().getId().equals(parent.getId())) {
            throw new IllegalStateException("본인의 자녀 정보만 수정할 수 있습니다.");
        }

        Academy academy = getAcademyOrThrow(request.academyId());

        if (academyStudentRepository.existsByAcademyIdAndStudent_Id(academy.getId(), student.getId())) {
            throw new IllegalStateException("이미 등록된 학원입니다.");
        }

        AcademyStudent academyStudent = new AcademyStudent(
                academy, student, student.getName(), null, false, request.schoolName(),
                null, null, null, null
        );
        academyStudentRepository.save(academyStudent);

        return student.getUuid();
    }

    /** 학부모 전용: 자녀들이 다니는 학원 목록(중복 제거)을 조회한다. */
    public List<AcademySummaryResponse> getMyChildrenAcademies() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있습니다.");
        }
        Map<Long, Academy> academiesById = new LinkedHashMap<>();
        for (AcademyStudent registration : academyStudentRepository.findByStudent_Parent_Id(parent.getId())) {
            academiesById.putIfAbsent(registration.getAcademy().getId(), registration.getAcademy());
        }
        return academiesById.values().stream().map(AcademySummaryResponse::from).toList();
    }

    /** 학부모 전용: 자녀를 새로 추가한다. 요청의 academyId로 승인 대기 상태로 등록된다. */
    @Transactional
    public UUID addMyChild(ChildAddRequest request) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 자녀를 추가할 수 있습니다.");
        }
        Academy academy = getAcademyOrThrow(request.academyId());

        Student student = new Student(request.name(), request.birthDate(), request.gender(), request.childPhone(), parent);
        studentRepository.save(student);

        AcademyStudent academyStudent = new AcademyStudent(
                academy, student, student.getName(), null, false, request.schoolName(),
                null, null, null, null
        );
        academyStudentRepository.save(academyStudent);

        return student.getUuid();
    }

    /** 학부모 전용: 본인 자녀 정보(이름/생년월일/성별/연락처/학교명)를 수정한다. */
    @Transactional
    public void updateMyChild(UUID studentUuid, ChildUpdateRequest request) {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 자녀 정보를 수정할 수 있습니다.");
        }

        Student student = studentRepository.findByUuid(studentUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자녀 정보입니다."));
        if (!student.getParent().getId().equals(parent.getId())) {
            throw new IllegalStateException("본인의 자녀 정보만 수정할 수 있습니다.");
        }

        student.updateName(request.name());
        student.updateCoreInfo(request.birthDate(), request.gender(), request.childPhone());

        for (AcademyStudent registration : academyStudentRepository.findByStudent_Uuid(studentUuid)) {
            registration.updateSchoolName(request.schoolName());
        }
    }

    /**
     * 마이페이지: 로그인한 본인의 계정 정보 조회 (수정 화면 진입 시 Pre-fill용).
     */
    public MyProfileResponse getMyProfile() {
        Member member = currentMemberProvider.getCurrentMember();
        return MyProfileResponse.from(member);
    }

    /**
     * 마이페이지: 로그인한 본인의 계정 정보(이름/연락처/비밀번호) 수정.
     * 비밀번호는 currentPassword/newPassword가 함께 채워져 있을 때만 변경을 시도한다.
     */
    @Transactional
    public void updateMyProfile(MyProfileUpdateRequest request) {
        Member member = currentMemberProvider.getCurrentMember();

        String newPhone = request.phone().trim();
        if (!newPhone.equals(member.getPhone()) && memberRepository.existsByPhone(newPhone)) {
            throw new IllegalStateException("이미 사용 중인 연락처입니다.");
        }
        member.updateBasicInfo(request.name().trim(), newPhone);

        boolean wantsPasswordChange = request.newPassword() != null && !request.newPassword().isBlank();
        if (wantsPasswordChange) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new IllegalStateException("비밀번호를 변경하려면 현재 비밀번호를 입력해야 합니다.");
            }
            if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
                throw new IllegalStateException("현재 비밀번호가 일치하지 않습니다.");
            }
            if (request.newPassword().length() < 8) {
                throw new IllegalStateException("새 비밀번호는 8자 이상이어야 합니다.");
            }
            member.changePassword(passwordEncoder.encode(request.newPassword()));
        }
    }

    public List<Member> getAllTeachersByAcademyId(Long academyId) {
        validateRequesterBelongsToAcademy(academyId);
        return memberRepository.findByAcademyIdAndRole(academyId, MemberRole.TEACHER);
    }

    @Transactional(readOnly = true)
    public List<StudentListResponse> getAllStudents(Long academyId, UUID teacherUuid, boolean unpaidOnly, String keyword) {
        validateRequesterBelongsToAcademy(academyId);
        List<AcademyStudent> academyStudents = academyStudentRepository.search(academyId, teacherUuid, unpaidOnly, keyword);

        LocalDate today = LocalDate.now();
        // 학생별 시간표 요약("월3수4금5")을 벌크 조회한다.
        Map<Long, List<ScheduleSummary>> schedulesByStudentId = regularClassService.getActiveScheduleSummaries(
                academyStudents.stream().map(AcademyStudent::getId).toList());

        return academyStudents.stream()
                .map(academyStudent -> StudentListResponse.from(academyStudent, today,
                        schedulesByStudentId.getOrDefault(academyStudent.getId(), List.of())))
                .toList();
    }

    public StudentDetailResponse getStudentDetail(UUID studentUuid, Long academyId) {
        validateRequesterBelongsToAcademy(academyId);
        AcademyStudent registration = getRegistrationOrThrow(studentUuid, academyId);
        return StudentDetailResponse.from(registration, LocalDate.now());
    }

    /** 특정 학생의 반 배정(요일/스케줄) 이력과 수강 기간 변경 이력을 함께 조회한다. */
    public StudentHistoryResponse getStudentHistory(UUID studentUuid, Long academyId) {
        validateRequesterBelongsToAcademy(academyId);
        AcademyStudent registration = getRegistrationOrThrow(studentUuid, academyId);

        List<ScheduleHistoryResponse> scheduleHistory = regularClassService.getStudentScheduleHistory(academyId, studentUuid);
        List<EnrollmentPeriodHistoryResponse> enrollmentPeriodHistory = enrollmentPeriodHistoryRepository
                .findByAcademyStudent_IdOrderByCreatedAtDesc(registration.getId()).stream()
                .map(EnrollmentPeriodHistoryResponse::from)
                .toList();

        return new StudentHistoryResponse(scheduleHistory, enrollmentPeriodHistory);
    }

    /** [수강생 관리] 화면 전용: 수강 기간(수강료 납부 기간) 등록/연장. */
    @Transactional
    public void updateEnrollmentPeriod(UUID studentUuid, Long academyId, StudentEnrollmentPeriodUpdateRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        validateManagerRole(requester);

        validateEnrollmentPeriod(request.enrollmentStartDate(), request.enrollmentEndDate());

        AcademyStudent registration = getRegistrationOrThrow(studentUuid, academyId);

        // 덮어쓰기 전에 이전 값을 이력으로 스냅샷 남긴다.
        EnrollmentPeriodHistory history = EnrollmentPeriodHistory.builder()
                .academyStudent(registration)
                .changedBy(requester)
                .previousStartDate(registration.getEnrollmentStartDate())
                .previousEndDate(registration.getEnrollmentEndDate())
                .newStartDate(request.enrollmentStartDate())
                .newEndDate(request.enrollmentEndDate())
                .build();
        enrollmentPeriodHistoryRepository.save(history);

        registration.updateEnrollmentPeriod(request.enrollmentStartDate(), request.enrollmentEndDate());

        // 이 학생이 편성된 모든 정규 수업의 회차를 다시 동기화한다.
        regularClassService.syncSessionsForStudentPeriodChange(registration);
    }

    /** [수강생 관리] 나머지 필드(관리용 이름/셔틀/할인/메모 등)를 갱신한다. 담당 강사는 scheduleStudentTeacherHandover로만 변경한다. */
    @Transactional
    public void updateStudent(UUID studentUuid, Long academyId, StudentUpdateRequest request) {
        validateRequesterBelongsToAcademy(academyId);
        AcademyStudent registration = getLedgerOrThrow(studentUuid, academyId);

        LocalDate parsedBirthDate = null;
        if (request.birthDate() != null && !request.birthDate().trim().isEmpty()) {
            parsedBirthDate = LocalDate.parse(request.birthDate().trim());
        }

        registration.updateAcademyStudentInfo(
                request.managementName(),
                registration.getTeacher(),
                request.schoolName(),
                request.shuttlePickupLocation(),
                request.shuttleDropoffLocation(),
                request.discountType(),
                request.memo(),
                request.weeklyFrequency(),
                parsedBirthDate,
                request.gender(),
                request.childPhone(),
                request.phone()
        );
    }

    /**
     * [수강생 관리] 담당 강사 인계(효력일 지정). 새 강사는 즉시, 기존 강사는 effectiveFrom 전날까지
     * 목록에서 계속 보인다. 원장/강사 모두 호출 가능하다.
     */
    @Transactional
    public void scheduleStudentTeacherHandover(Long academyId, UUID studentUuid, StudentTeacherHandoverRequest request) {
        validateRequesterBelongsToAcademy(academyId);
        AcademyStudent registration = getLedgerOrThrow(studentUuid, academyId);

        Member newTeacher = resolveTeacher(academyId, request.newTeacherUuid());
        if (newTeacher.getId().equals(registration.getTeacher() != null ? registration.getTeacher().getId() : null)) {
            throw new IllegalArgumentException("이미 담당 중인 강사입니다.");
        }

        registration.scheduleTeacherHandover(registration.getTeacher(), newTeacher, request.effectiveFrom());
    }

    @Transactional
    public void approveMember(UUID uuid) {
        Member requester = currentMemberProvider.getCurrentMember();

        // 강사 승인이면 Member.uuid, 수강생 승인이면 Student.uuid가 전달되므로 순차 조회로 판별한다.
        Optional<Member> memberOpt = memberRepository.findByUuid(uuid);
        if (memberOpt.isPresent()) {
            approveTeacher(requester, memberOpt.get());
            return;
        }

        approveStudentEnrollments(requester, uuid);
    }

    /**
     * 강사 가입 승인: 원장만 가능하며, 같은 학원 소속 강사만 승인할 수 있다.
     */
    private void approveTeacher(Member requester, Member target) {
        if (requester.getRole() != MemberRole.ADMIN) {
            throw new IllegalStateException("강사 가입 승인은 원장만 할 수 있습니다.");
        }
        if (!isSameAcademy(requester.getAcademy(), target.getAcademy())) {
            throw new IllegalStateException("다른 학원 소속 강사는 승인할 수 없습니다.");
        }
        target.approve();
    }

    /**
     * 수강생 가입(등록) 승인: 원장/강사 모두 가능하지만, 요청자가 속한 학원의 등록 장부만 승인할 수 있다.
     * 한 학생이 여러 학원에 중복 등록되어 있더라도 요청자 소속 학원 건만 승인 대상으로 필터링한다.
     */
    private void approveStudentEnrollments(Member requester, UUID studentUuid) {
        studentRepository.findByUuid(studentUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. uuid=" + studentUuid));

        List<AcademyStudent> registrations = academyStudentRepository.findByStudent_Uuid(studentUuid);
        if (registrations.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 수강생 등록 정보입니다. uuid=" + studentUuid);
        }

        List<AcademyStudent> myAcademyRegistrations = registrations.stream()
                .filter(registration -> isSameAcademy(requester.getAcademy(), registration.getAcademy()))
                .toList();

        if (myAcademyRegistrations.isEmpty()) {
            throw new IllegalStateException("소속 학원에 등록되지 않은 수강생은 승인할 수 없습니다.");
        }

        myAcademyRegistrations.forEach(AcademyStudent::approve);
    }

    /** 요청자가 주어진 academyId 소속인지 검증한다. */
    private Member validateRequesterBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 데이터만 조회하거나 관리할 수 있습니다.");
        }
        return requester;
    }

    /** 원장·강사 전용 기능인지 검증한다(SecurityConfig의 1차 방어에 더한 서비스 계층 2차 방어). */
    private void validateManagerRole(Member requester) {
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("원장 또는 강사 권한이 필요합니다.");
        }
    }

    /** 잘못된 입력값 검증이므로 항상 400으로 매핑되는 IllegalStateException을 쓴다. */
    private void validateEnrollmentPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalStateException("수강 종료일은 시작일보다 이후여야 합니다.");
        }
    }

    private boolean isSameAcademy(Academy a, Academy b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }

    private Academy getAcademyOrThrow(Long academyId) {
        return academyRepository.findById(academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원입니다."));
    }

    private AcademyStudent getRegistrationOrThrow(UUID studentUuid, Long academyId) {
        return academyStudentRepository.findByStudentUuidAndAcademyId(studentUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 학원에 등록되지 않은 수강생이거나 존재하지 않습니다."));
    }

    private AcademyStudent getLedgerOrThrow(UUID studentUuid, Long academyId) {
        return academyStudentRepository.findByStudentUuidAndAcademyId(studentUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수강생 장부입니다."));
    }

    /** 담당 강사 지정은 해당 학원 소속의 TEACHER 권한 회원으로만 제한한다. */
    private Member resolveTeacher(Long academyId, UUID teacherUuid) {
        Member teacher = memberRepository.findByUuid(teacherUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 강사입니다."));
        if (teacher.getRole() != MemberRole.TEACHER
                || teacher.getAcademy() == null
                || !teacher.getAcademy().getId().equals(academyId)) {
            throw new IllegalArgumentException("해당 학원 소속 강사만 담당 강사로 지정할 수 있습니다.");
        }
        return teacher;
    }
}