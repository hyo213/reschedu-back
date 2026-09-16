package com.academy.reschedu.domain.aisummary.dto;

import java.util.UUID;

/**
 * 학부모 대시보드 "이번 달 자녀 리포트" 카드 한 건. 자녀 한 명이 여러 학원에 다니면 학원마다 하나씩 내려간다.
 * summaryText는 Gemini가 생성한 2~3문장 요약 — 키 미설정/호출 실패 시 null이며, 그 경우 프론트는 문장
 * 없이 숫자 배지만 보여주면 된다(수치 자체는 전부 서버가 결정론적으로 계산해 절대 null이 아니다).
 */
public record ChildAiSummaryResponse(
        UUID studentUuid,
        String studentName,
        Long academyId,
        String academyName,
        long absenceCountThisMonth,
        long availableTicketCount,
        long expiringSoonTicketCount,
        long usedTicketCount,
        Long enrollmentDaysRemaining,
        String summaryText
) {
}
