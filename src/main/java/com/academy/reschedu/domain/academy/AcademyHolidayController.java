package com.academy.reschedu.domain.academy;

import com.academy.reschedu.domain.academy.dto.HolidayCreateRequest;
import com.academy.reschedu.domain.academy.dto.HolidayResponse;
import com.academy.reschedu.domain.academy.dto.HolidayUpdateRequest;
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
@RequestMapping("/api/academy-holidays")
@RequiredArgsConstructor
public class AcademyHolidayController {

    private final AcademyHolidayService academyHolidayService;

    /**
     * 원장 전용: 휴무일 등록 (등록 시 해당 요일 정규 수업 수강생 전원에게 보강권 자동 발급)
     * POST /api/academy-holidays?academyId=1
     */
    @PostMapping
    public ResponseEntity<HolidayResponse> create(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody HolidayCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academyHolidayService.createHoliday(academyId, request));
    }

    /**
     * 원장/강사용: 휴무일 목록 조회
     * GET /api/academy-holidays?academyId=1
     */
    @GetMapping
    public ResponseEntity<List<HolidayResponse>> list(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(academyHolidayService.getHolidays(academyId));
    }

    /**
     * 학부모 전용: 내 자녀가 속한 모든 학원의 휴무일 목록 조회 (academyId 파라미터 불필요)
     * GET /api/academy-holidays/my-children
     */
    @GetMapping("/my-children")
    public ResponseEntity<List<HolidayResponse>> myChildrenHolidays() {
        return ResponseEntity.ok(academyHolidayService.getHolidaysForMyChildren());
    }

    /**
     * 원장 전용: 휴무 사유 수정
     * PATCH /api/academy-holidays/{uuid}?academyId=1
     */
    @PatchMapping("/{uuid}")
    public ResponseEntity<HolidayResponse> update(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @RequestBody HolidayUpdateRequest request) {
        return ResponseEntity.ok(academyHolidayService.updateHoliday(academyId, uuid, request));
    }

    /**
     * 원장 전용: 휴무일 지정 취소 (해당 날짜 정규 수업 회차 복원 + 미사용 보강권 회수)
     * DELETE /api/academy-holidays/{uuid}?academyId=1
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Map<String, Integer>> delete(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        int retractedCount = academyHolidayService.deleteHoliday(academyId, uuid);
        return ResponseEntity.ok(Map.of("retractedTicketCount", retractedCount));
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
