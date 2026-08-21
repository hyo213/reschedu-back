package com.academy.reschedu.domain.notice;

import com.academy.reschedu.domain.notice.dto.NoticeCreateRequest;
import com.academy.reschedu.domain.notice.dto.NoticeResponse;
import com.academy.reschedu.domain.notice.dto.NoticeUpdateRequest;
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
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 원장/강사 전용: 공지 작성
     * POST /api/notices?academyId=1
     */
    @PostMapping
    public ResponseEntity<NoticeResponse> create(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.createNotice(academyId, request));
    }

    /**
     * 원장/강사 전용: 관리용 전체 공지 목록 (노출 꺼짐/기간 만료 포함)
     * GET /api/notices?academyId=1
     */
    @GetMapping
    public ResponseEntity<List<NoticeResponse>> list(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(noticeService.getNotices(academyId));
    }

    /**
     * 소속 회원 전원: 현재 노출 중인 공지 목록 (대시보드 '주요 알림' 및 공지사항 게시판 읽기용)
     * GET /api/notices/active?academyId=1
     */
    @GetMapping("/active")
    public ResponseEntity<List<NoticeResponse>> activeList(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(noticeService.getActiveNotices(academyId));
    }

    /**
     * 🎯 [다학원 자녀 지원] 학부모 전용: 대시보드용 — 자녀들이 다니는 모든 학원의 노출 중인 공지를
     * 최신순으로 모아 반환한다(각 항목에 학원명 포함).
     * GET /api/notices/my-children-active
     */
    @GetMapping("/my-children-active")
    public ResponseEntity<List<NoticeResponse>> myChildrenActiveList() {
        return ResponseEntity.ok(noticeService.getActiveNoticesForMyChildren());
    }

    /**
     * 소속 회원 전원: 공지 상세 조회
     * GET /api/notices/{uuid}?academyId=1
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> detail(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(noticeService.getNotice(academyId, uuid));
    }

    /**
     * 원장/강사 전용: 공지 수정
     * PATCH /api/notices/{uuid}?academyId=1
     */
    @PatchMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> update(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody NoticeUpdateRequest request) {
        return ResponseEntity.ok(noticeService.updateNotice(academyId, uuid, request));
    }

    /**
     * 원장/강사 전용: 공지 삭제
     * DELETE /api/notices/{uuid}?academyId=1
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(
            @PathVariable("uuid") UUID uuid,
            @RequestParam("academyId") Long academyId) {
        noticeService.deleteNotice(academyId, uuid);
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
