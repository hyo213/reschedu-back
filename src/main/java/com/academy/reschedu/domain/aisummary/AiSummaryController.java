package com.academy.reschedu.domain.aisummary;

import com.academy.reschedu.domain.aisummary.dto.ChildAiSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "/api/members/my-children" 경로 아래에 붙여서 SecurityConfig의 기존
 * "/api/members/my-children/**" → hasRole("PARENT") 규칙을 그대로 재사용한다(별도 설정 불필요).
 */
@RestController
@RequestMapping("/api/members/my-children")
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;

    /**
     * 학부모 전용: 대시보드 "이번 달 자녀 리포트" 카드용 — 자녀별(자녀×학원) 출결/보강권/수강기간 요약.
     * GET /api/members/my-children/ai-summary
     */
    @GetMapping("/ai-summary")
    public ResponseEntity<List<ChildAiSummaryResponse>> getAiSummaries() {
        return ResponseEntity.ok(aiSummaryService.getMyChildrenAiSummaries());
    }
}
