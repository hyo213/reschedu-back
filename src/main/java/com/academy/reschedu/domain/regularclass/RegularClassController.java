package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.regularclass.dto.ChangeClassTeacherRequest;
import com.academy.reschedu.domain.regularclass.dto.NextClassResponse;
import com.academy.reschedu.domain.regularclass.dto.RegularClassCreateRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassDiscontinueRequest;
import com.academy.reschedu.domain.regularclass.dto.RegularClassResponse;
import com.academy.reschedu.domain.regularclass.dto.RegularClassUpdateRequest;
import com.academy.reschedu.domain.regularclass.dto.ScheduleAssignmentRequest;
import com.academy.reschedu.domain.regularclass.dto.ScheduleChangeRequest;
import com.academy.reschedu.domain.regularclass.dto.ScheduleHistoryResponse;
import com.academy.reschedu.domain.regularclass.dto.SmartScheduleAssignmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/regular-classes")
@RequiredArgsConstructor
public class RegularClassController {

    private final RegularClassService regularClassService;

    /**
     * 원장/강사용: 정규 수업 시간표 추가 API.
     * 요일을 여러 개 선택해 한 번에 등록해도, 요일마다 완전히 독립된 정규 수업이 각각 만들어진다
     * (강사는 동시에 두 곳에서 수업할 수 없으므로 — RegularClassService.createRegularClass 참고).
     * POST /api/regular-classes?academyId=1
     */
    @PostMapping
    public ResponseEntity<Map<String, List<UUID>>> create(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody RegularClassCreateRequest request) {
        List<UUID> uuids = regularClassService.createRegularClass(academyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("uuids", uuids));
    }

    /**
     * 원장/강사용: 정규 수업 시간표 목록 조회 API
     * GET /api/regular-classes?academyId=1&teacherUuid=...&weekStart=2026-07-27
     *
     * weekStart(해당 주에 속한 아무 날짜, 보통 월요일)를 지정하면 그 주의 휴무/결석 예외 정보가
     * weeklyOccurrences에 함께 채워진다. 지정하지 않으면 시간표 기본 정보만 반환한다.
     */
    @GetMapping
    public ResponseEntity<List<RegularClassResponse>> list(
            @RequestParam("academyId") Long academyId,
            @RequestParam(value = "teacherUuid", required = false) UUID teacherUuid,
            @RequestParam(value = "weekStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(regularClassService.getRegularClasses(academyId, teacherUuid, weekStart));
    }

    /**
     * 원장/강사용: 정규 수업 시간표 수정 API (기본 정보 + 수강생 명단 전체 교체)
     * PUT /api/regular-classes/{uuid}?academyId=1
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<Void> update(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody RegularClassUpdateRequest request) {
        regularClassService.updateRegularClass(academyId, uuid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 원장/강사용: 담당 강사를 특정 날짜부터 바꾼다 — 그 반의 현재 로스터 전원을 새 강사의 같은
     * 요일·시간 반으로 이관한다(효력일 이전 기록은 기존 강사로 그대로 남는다).
     * POST /api/regular-classes/{uuid}/change-teacher?academyId=1
     */
    @PostMapping("/{uuid}/change-teacher")
    public ResponseEntity<Map<String, List<UUID>>> changeTeacher(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody ChangeClassTeacherRequest request) {
        List<UUID> movedStudentUuids = regularClassService.changeClassTeacher(academyId, uuid, request);
        return ResponseEntity.ok(Map.of("movedStudentUuids", movedStudentUuids));
    }

    /**
     * 🎯 원장/강사용: 이 반(요일·시간 슬롯)을 특정 날짜부터 종료한다 — 배정되어 있던 학생들도 그 전날까지만
     * 이 반으로 남고, 과거 기록은 [수강 히스토리]에 그대로 보존된다.
     * POST /api/regular-classes/{uuid}/discontinue?academyId=1
     */
    @PostMapping("/{uuid}/discontinue")
    public ResponseEntity<Void> discontinue(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody RegularClassDiscontinueRequest request) {
        regularClassService.discontinueRegularClass(academyId, uuid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 원장/강사용: 실수로 만든 반을 이력 없이 완전히 삭제한다 — 학생 배정/보강권/보강 신청 이력이
     * 하나라도 있으면 거부되며, 그 경우 위 discontinue([반 종료])를 대신 써야 한다.
     * DELETE /api/regular-classes/{uuid}?academyId=1
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        regularClassService.deleteRegularClass(academyId, uuid);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 [수강생 관리] 원장/강사용: 특정 학생을 정규 수업에 기간을 지정해 배정한다.
     * POST /api/regular-classes/schedule-assignments?academyId=1
     */
    @PostMapping("/schedule-assignments")
    public ResponseEntity<Map<String, UUID>> assignSchedule(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody ScheduleAssignmentRequest request) {
        UUID regularClassUuid = regularClassService.assignStudentSchedule(academyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("regularClassUuid", regularClassUuid));
    }

    /**
     * 🎯 [수강생 관리] 원장/강사용: 요일 변경 — 기존 반 배정을 종료하고 새 반으로 배정을 시작한다(원자적 처리).
     * POST /api/regular-classes/schedule-change?academyId=1
     */
    @PostMapping("/schedule-change")
    public ResponseEntity<Void> changeSchedule(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody ScheduleChangeRequest request) {
        regularClassService.changeStudentSchedule(academyId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 🎯 [수강생 관리] 원장/강사용: 스마트 반 배정 — 요청한 요일 중 같은 강사의 기존 반과 겹치는
     * 요일은 그 반에 합류시키고, 겹치지 않는 요일만 모아 새 반을 만든다.
     * POST /api/regular-classes/schedule-smart-assign?academyId=1
     */
    @PostMapping("/schedule-smart-assign")
    public ResponseEntity<Map<String, List<UUID>>> smartAssignSchedule(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody SmartScheduleAssignmentRequest request) {
        List<UUID> regularClassUuids = regularClassService.smartAssignStudentSchedule(academyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("regularClassUuids", regularClassUuids));
    }

    /**
     * 🎯 [수강 히스토리] 원장/강사용: 특정 학생의 반 배정 이력(과거~현재~예정)을 조회한다.
     * GET /api/regular-classes/schedule-history?academyId=1&studentUuid=...
     */
    @GetMapping("/schedule-history")
    public ResponseEntity<List<ScheduleHistoryResponse>> getScheduleHistory(
            @RequestParam("academyId") Long academyId,
            @RequestParam("studentUuid") UUID studentUuid) {
        return ResponseEntity.ok(regularClassService.getStudentScheduleHistory(academyId, studentUuid));
    }

    /**
     * 학부모 전용: 본인 자녀가 편성된 시간표만 조회하는 API.
     * 조회 대상은 서버가 JWT 인증 정보로 직접 판별하므로, 요청 파라미터로 다른 학생을 지정할 수 없다.
     * GET /api/regular-classes/my-children?weekStart=2026-07-27
     */
    @GetMapping("/my-children")
    public ResponseEntity<List<RegularClassResponse>> myChildrenClasses(
            @RequestParam(value = "weekStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(regularClassService.getMyChildrenRegularClasses(weekStart));
    }

    /**
     * 원장/강사용: 메인 대시보드 "다음 수업" 카드 — 현재 시각 기준으로 가장 가까운 다가오는 시간표 회차.
     * GET /api/regular-classes/next?academyId=1&teacherUuid=...
     */
    @GetMapping("/next")
    public ResponseEntity<NextClassResponse> next(
            @RequestParam("academyId") Long academyId,
            @RequestParam(value = "teacherUuid", required = false) UUID teacherUuid) {
        return ResponseEntity.ok(regularClassService.getNextClass(academyId, teacherUuid).orElse(null));
    }

    /**
     * 학부모 전용: 메인 대시보드 "다음 수업" 카드 — 본인 자녀 시간표 중 가장 가까운 회차.
     * GET /api/regular-classes/my-children/next
     */
    @GetMapping("/my-children/next")
    public ResponseEntity<NextClassResponse> myChildrenNext() {
        return ResponseEntity.ok(regularClassService.getNextClassForMyChildren().orElse(null));
    }

    // ─── 예외 처리 ──────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
