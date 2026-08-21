package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * [정규 수업 종료(효력일 지정)] 이 반(요일·시간 슬롯)을 effectiveFrom부터 더 이상 진행하지 않는다.
 * 현재 이 반에 배정되어 있던 학생들은 effectiveFrom 전날까지만 이 반으로 남고, 그 이후 기록은 없어진다
 * (다른 반으로 자동 이관하지 않는다 — 필요하면 별도로 요일 변경/스케줄 배정을 해야 한다).
 */
public record RegularClassDiscontinueRequest(
        @NotNull(message = "적용 시작일은 필수입니다.")
        LocalDate effectiveFrom
) {}
