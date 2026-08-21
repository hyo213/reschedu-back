package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.MakeupRequestCreateRequest;
import com.academy.reschedu.domain.makeup.dto.MakeupRequestResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupSlotResponse;
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
@RequestMapping("/api/makeup-requests")
@RequiredArgsConstructor
public class MakeupRequestController {

    private final MakeupRequestService makeupRequestService;

    /**
     * 학부모(+원장/강사)용: 정원이 아직 차지 않은 여석 목록 조회
     * GET /api/makeup-requests/open-slots?academyId=1&weekStart=2026-08-03
     */
    @GetMapping("/open-slots")
    public ResponseEntity<List<MakeupSlotResponse>> getOpenSlots(
            @RequestParam("academyId") Long academyId,
            @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(makeupRequestService.getOpenSlots(academyId, weekStart));
    }

    /**
     * 학부모 전용: 보강 신청 접수
     * POST /api/makeup-requests
     */
    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody MakeupRequestCreateRequest request) {
        UUID requestUuid = makeupRequestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("requestUuid", requestUuid));
    }

    /**
     * 원장/강사 전용: 보강 매칭 화면에서 여석에 학생을 직접 매칭(등록)한다. 생성과 동시에 확정(APPROVED)된다.
     * POST /api/makeup-requests/match?academyId=1
     */
    @PostMapping("/match")
    public ResponseEntity<Map<String, UUID>> match(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody MakeupRequestCreateRequest request) {
        UUID requestUuid = makeupRequestService.matchStudentToSlot(academyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("requestUuid", requestUuid));
    }

    /**
     * 학부모 전용: 본인 자녀들의 보강 신청 내역 조회
     * GET /api/makeup-requests/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<MakeupRequestResponse>> getMyRequests() {
        return ResponseEntity.ok(makeupRequestService.getMyRequests());
    }

    /**
     * 원장/강사용: 보강 매칭 센터 - 대기 중인 보강 신청 목록 조회
     * GET /api/makeup-requests/pending?academyId=1
     */
    @GetMapping("/pending")
    public ResponseEntity<List<MakeupRequestResponse>> getPending(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(makeupRequestService.getPendingRequests(academyId));
    }

    /**
     * 원장/강사용: 보강 신청 수락
     * PATCH /api/makeup-requests/{uuid}/approve?academyId=1
     */
    @PatchMapping("/{uuid}/approve")
    public ResponseEntity<Void> approve(@PathVariable("uuid") UUID uuid, @RequestParam("academyId") Long academyId) {
        makeupRequestService.approve(academyId, uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * 원장/강사용: 보강 신청 거절
     * PATCH /api/makeup-requests/{uuid}/reject?academyId=1
     */
    @PatchMapping("/{uuid}/reject")
    public ResponseEntity<Void> reject(@PathVariable("uuid") UUID uuid, @RequestParam("academyId") Long academyId) {
        makeupRequestService.reject(academyId, uuid);
        return ResponseEntity.noContent().build();
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
