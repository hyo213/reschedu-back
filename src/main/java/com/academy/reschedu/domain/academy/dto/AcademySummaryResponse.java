package com.academy.reschedu.domain.academy.dto;

import com.academy.reschedu.domain.academy.Academy;

/**
 * 🎯 [다학원 자녀 지원] 학부모가 자녀들이 다니는 학원 중 하나를 골라 조회할 때 쓰는 가벼운 목록용 DTO.
 */
public record AcademySummaryResponse(
        Long id,
        String name
) {
    public static AcademySummaryResponse from(Academy academy) {
        return new AcademySummaryResponse(academy.getId(), academy.getName());
    }
}
