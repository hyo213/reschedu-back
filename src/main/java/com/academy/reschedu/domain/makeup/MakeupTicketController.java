package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.AbsenceRequest;
import com.academy.reschedu.domain.makeup.dto.ManualTicketGrantRequest;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketDetailResponse;
import com.academy.reschedu.domain.makeup.dto.StudentTicketCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/makeup-tickets")
@RequiredArgsConstructor
public class MakeupTicketController {

    private final MakeupTicketService makeupTicketService;

    /**
     * 학부모 전용: 결석 신청 (성공 시 보강권 1개 자동 발급)
     * POST /api/makeup-tickets/absence
     */
    @PostMapping("/absence")
    public ResponseEntity<Map<String, UUID>> requestAbsence(@Valid @RequestBody AbsenceRequest request) {
        UUID ticketUuid = makeupTicketService.requestAbsence(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ticketUuid", ticketUuid));
    }

    /**
     * 학부모/원장/강사: 결석 신청 취소 (미사용 상태의 보강권만 회수됨)
     * DELETE /api/makeup-tickets/absence
     */
    @DeleteMapping("/absence")
    public ResponseEntity<Void> cancelAbsence(@Valid @RequestBody AbsenceRequest request) {
        makeupTicketService.cancelAbsence(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 원장/강사용: 보강 매칭 센터 - 학생별 잔여 보강권 개수 조회
     * GET /api/makeup-tickets/counts?academyId=1
     */
    @GetMapping("/counts")
    public ResponseEntity<List<StudentTicketCountResponse>> getRemainingCounts(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(makeupTicketService.getRemainingTicketCounts(academyId));
    }

    /**
     * 학부모 전용: [보강 신청] 화면 - 본인 자녀들의 잔여 보강권 개수 조회
     * GET /api/makeup-tickets/my-children-counts?academyId=1
     */
    @GetMapping("/my-children-counts")
    public ResponseEntity<List<StudentTicketCountResponse>> getMyChildrenCounts(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(makeupTicketService.getMyChildrenTicketCounts(academyId));
    }

    /**
     * 원장/강사용: 보강 매칭 센터 - 특정 학생의 잔여 보강권 상세 내역(원래 수업일 목록) 조회
     * GET /api/makeup-tickets/details?academyId=1&studentUuid=...
     */
    @GetMapping("/details")
    public ResponseEntity<List<MakeupTicketDetailResponse>> getTicketDetails(
            @RequestParam("academyId") Long academyId,
            @RequestParam("studentUuid") UUID studentUuid) {
        return ResponseEntity.ok(makeupTicketService.getTicketDetails(academyId, studentUuid));
    }

    /**
     * 학부모 전용: 본인 자녀 한 명의 보강권 전체 이력(미사용/사용/만료 포함) 조회
     * GET /api/makeup-tickets/my-children/{studentUuid}/details?academyId=1
     */
    @GetMapping("/my-children/{studentUuid}/details")
    public ResponseEntity<List<MakeupTicketDetailResponse>> getMyChildTicketHistory(
            @PathVariable("studentUuid") UUID studentUuid,
            @RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(makeupTicketService.getMyChildTicketHistory(academyId, studentUuid));
    }

    /**
     * 원장/강사용: 보강 매칭 센터 - 특정 학생에게 보강권을 수동으로 지급(초기 등록/추가 지급)
     * POST /api/makeup-tickets/grant?academyId=1
     */
    @PostMapping("/grant")
    public ResponseEntity<Void> grantManually(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody ManualTicketGrantRequest request) {
        makeupTicketService.grantTicketsManually(academyId, request);
        return ResponseEntity.noContent().build();
    }

    // ─── 예외 처리 ──────────────────────────────────────────────────────────

    /**
     * 🎯 [보강권 전체 정책] 원장/강사가 결석 대리 처리 또는 수동 지급 시 발급 제한을 초과했을 때만 던져진다.
     * 일반 오류(400)와 구분되는 409로 응답해, 프론트가 이 경우엔 alert 대신 confirm(예/아니오)을 띄우고
     * "예"를 누르면 overrideLimit=true로 재요청하도록 한다.
     */
    @ExceptionHandler(MakeupTicketLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleLimitExceededException(MakeupTicketLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage(), "limitExceeded", true));
    }

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
