package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
