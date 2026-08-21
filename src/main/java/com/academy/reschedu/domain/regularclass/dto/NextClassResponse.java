package com.academy.reschedu.domain.regularclass.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 메인 대시보드 "다음 수업" 카드용: 현재 시각 기준으로 가장 가까운 다가오는 시간표 회차.
 */
public record NextClassResponse(
        UUID classUuid,
        String title,
        String teacherName,
        String roomNumber,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int currentCount,
        int maxCapacity,
        List<String> studentNames
) {
}
