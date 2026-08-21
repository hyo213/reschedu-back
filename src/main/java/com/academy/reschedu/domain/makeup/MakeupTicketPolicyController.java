package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 🎯 [보강권 관리] 원장 전용: 학원 전체 보강권 정책(최대 보유 개수/월 발급 제한/기본 유효기간) 조회·설정.
 */
@RestController
@RequestMapping("/api/makeup-ticket-policy")
@RequiredArgsConstructor
public class MakeupTicketPolicyController {

    private final MakeupTicketPolicyService makeupTicketPolicyService;

    @GetMapping
    public ResponseEntity<MakeupTicketPolicyResponse> getPolicy(@RequestParam("academyId") Long academyId) {
        return ResponseEntity.ok(makeupTicketPolicyService.getPolicy(academyId));
    }

    @PutMapping
    public ResponseEntity<MakeupTicketPolicyResponse> updatePolicy(
            @RequestParam("academyId") Long academyId,
            @Valid @RequestBody MakeupTicketPolicyUpdateRequest request) {
        return ResponseEntity.ok(makeupTicketPolicyService.updatePolicy(academyId, request));
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
