package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.academy.dto.AcademySummaryResponse;
import com.academy.reschedu.domain.member.dto.*;
import com.academy.reschedu.global.security.jwt.JwtCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtCookieProvider jwtCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, UUID>> signUp(@Valid @RequestBody SignUpRequest request) {
        UUID uuid = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("uuid", uuid));
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, String>> checkEmail(@RequestParam("email") String email) {
        memberService.validateDuplicateEmail(email);
        return ResponseEntity.ok(Map.of("message", "사용 가능한 이메일입니다."));
    }

    /**
     * 회원가입 / 정보수정 전용 이메일 인증코드 발송 API
     * POST /api/members/email/send?email=user@test.com
     */
    @PostMapping("/email/send")
    public ResponseEntity<Map<String, String>> sendVerificationEmail(@RequestParam("email") String email) {
        // 중복 이메일 체크 후 인증 메일 발송 로직 호출
        memberService.sendVerificationEmail(email);
        return ResponseEntity.ok(Map.of("message", "인증 메일이 발송되었습니다."));
    }

    /**
     * 사용자가 입력한 인증코드 검증 API
     * POST /api/members/email/verify?email=user@test.com&code=123456
     */
    @PostMapping("/email/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @RequestParam("email") String email,
            @RequestParam("code") String code) {
        memberService.verifyEmailCode(email, code);
        return ResponseEntity.ok(Map.of("message", "이메일 인증이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = memberService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieProvider.createAccessTokenCookie(result.accessToken()).toString());
        return ResponseEntity.ok(result.profile());
    }

    /**
     * 마이페이지: 로그인한 본인의 계정 정보 조회 (수정 화면 Pre-fill용)
     * GET /api/members/me
     */
    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        return ResponseEntity.ok(memberService.getMyProfile());
    }

    /**
     * 마이페이지: 로그인한 본인의 계정 정보(이름/연락처/비밀번호) 수정
     * PATCH /api/members/me
     */
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMyProfile(@Valid @RequestBody MyProfileUpdateRequest request) {
        memberService.updateMyProfile(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 학부모 전용: 본인 자녀 목록 조회 API (결석 신청 화면의 자녀 선택 등에 사용)
     */
    @GetMapping("/my-children")
    public ResponseEntity<List<MyChildResponse>> getMyChildren() {
        return ResponseEntity.ok(memberService.getMyChildren());
    }

    /**
     * 학부모 전용: [내 계정 정보] 화면에서 자녀 정보를 추가/수정하기 위한 상세 목록 조회
     * GET /api/members/my-children/detail
     */
    @GetMapping("/my-children/detail")
    public ResponseEntity<List<MyChildDetailResponse>> getMyChildrenDetail() {
        return ResponseEntity.ok(memberService.getMyChildrenDetail());
    }

    /**
     * 🎯 [다학원 자녀 지원] 학부모 전용: 자녀들이 다니는 학원 목록(중복 제거) — 보강 신청/공지사항
     * 화면의 "학원 선택" 셀렉트박스에 쓴다.
     * GET /api/members/my-children-academies
     */
    @GetMapping("/my-children-academies")
    public ResponseEntity<List<AcademySummaryResponse>> getMyChildrenAcademies() {
        return ResponseEntity.ok(memberService.getMyChildrenAcademies());
    }

    /**
     * 학부모 전용: 자녀 추가
     * POST /api/members/my-children
     */
    @PostMapping("/my-children")
    public ResponseEntity<Map<String, UUID>> addMyChild(@Valid @RequestBody ChildAddRequest request) {
        UUID uuid = memberService.addMyChild(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("uuid", uuid));
    }

    /**
     * 학부모 전용: 자녀 정보 수정
     * PATCH /api/members/my-children/{studentUuid}
     */
    @PatchMapping("/my-children/{studentUuid}")
    public ResponseEntity<Void> updateMyChild(
            @PathVariable("studentUuid") UUID studentUuid,
            @Valid @RequestBody ChildUpdateRequest request) {
        memberService.updateMyChild(studentUuid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 [다학원 자녀 지원] 학부모 전용: 이미 등록된 자녀를 다른 학원에도 다니게 한다(승인 대기 상태로 추가).
     * POST /api/members/my-children/{studentUuid}/academies
     */
    @PostMapping("/my-children/{studentUuid}/academies")
    public ResponseEntity<Void> addAcademyToMyChild(
            @PathVariable("studentUuid") UUID studentUuid,
            @Valid @RequestBody ChildAcademyAddRequest request) {
        memberService.addAcademyToMyChild(studentUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 특정 학원의 모든 강사 목록 조회 API
     */
    @GetMapping("/teachers")
    public ResponseEntity<List<TeacherListResponse>> getAllTeachers(@RequestParam("academyId") Long academyId) {
        List<Member> teachers = memberService.getAllTeachersByAcademyId(academyId);

        List<TeacherListResponse> response = teachers.stream()
                .map(TeacherListResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * 개편판: 수강생 목록 조회 API (AcademyStudent 매핑 테이블 기준 변환 노출)
     */
    @GetMapping("/students")
    public ResponseEntity<List<StudentListResponse>> getAllStudents(
            @RequestParam("academyId") Long academyId,
            @RequestParam(value = "teacherUuid", required = false) UUID teacherUuid,
            @RequestParam(value = "unpaidOnly", required = false, defaultValue = "false") boolean unpaidOnly,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(memberService.getAllStudents(academyId, teacherUuid, unpaidOnly, keyword));
    }

    /**
     * 개편판: 수강생 단건 상세 조회 API
     * GET /api/members/students/{uuid}?academyId=1
     */
    @GetMapping("/students/{uuid}")
    public ResponseEntity<StudentDetailResponse> getStudentDetail(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(memberService.getStudentDetail(uuid, academyId));
    }

    /**
     * 🎯 [수강 히스토리] 특정 학생의 반 배정(요일/스케줄) 이력 + 수강 기간 변경 이력 조회
     * GET /api/members/students/{uuid}/history?academyId=1
     */
    @GetMapping("/students/{uuid}/history")
    public ResponseEntity<StudentHistoryResponse> getStudentHistory(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(memberService.getStudentHistory(uuid, academyId));
    }

    /**
     * 개편판: 수강생 정보 수정 API
     * PUT /api/members/students/{uuid}?academyId=1
     */
    @PutMapping("/students/{uuid}")
    public ResponseEntity<Void> updateStudent(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @RequestBody StudentUpdateRequest request) {
        memberService.updateStudent(uuid, academyId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 [수강생 관리] 담당 강사 인계(효력일 지정) API — 원장/강사 모두 호출 가능.
     * 새 강사는 즉시, 기존 강사는 effectiveFrom 전날까지 [수강생 관리] 목록에서 계속 보인다.
     * POST /api/members/students/{uuid}/teacher-handover?academyId=1
     */
    @PostMapping("/students/{uuid}/teacher-handover")
    public ResponseEntity<Void> scheduleStudentTeacherHandover(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody StudentTeacherHandoverRequest request) {
        memberService.scheduleStudentTeacherHandover(academyId, uuid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 [수강생 관리] 화면 전용: 수강 기간(수강료 납부 기간) 등록/연장 API.
     * 정규 수업 관리 화면에서는 더 이상 이 정보를 다루지 않는다.
     * PATCH /api/members/students/{uuid}/enrollment-period?academyId=1
     */
    @PatchMapping("/students/{uuid}/enrollment-period")
    public ResponseEntity<Void> updateEnrollmentPeriod(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @RequestBody StudentEnrollmentPeriodUpdateRequest request) {
        memberService.updateEnrollmentPeriod(uuid, academyId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 원장/강사용: 수강생 회원가입 없이 오프라인 직접 수동 등록 API
     * POST /api/members/students/manual?academyId=1
     */
    @PostMapping("/students/manual")
    public ResponseEntity<Map<String, UUID>> registerStudentManual(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody StudentManualRegisterRequest request) {
        UUID studentUuid = memberService.registerStudentManual(academyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("uuid", studentUuid));
    }

    /**
     * 강사/수강생(학부모) 회원가입 승인 API
     * PATCH /api/members/{uuid}/approve
     */
    @PatchMapping("/{uuid}/approve")
    public ResponseEntity<Void> approve(@PathVariable("uuid") UUID uuid) {
        memberService.approveMember(uuid);
        return ResponseEntity.ok().build();
    }

    // ─── 예외 처리 ──────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        if (e.getMessage().contains("누락되었습니다")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}