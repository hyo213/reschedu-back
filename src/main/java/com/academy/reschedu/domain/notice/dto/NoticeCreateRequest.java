package com.academy.reschedu.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record NoticeCreateRequest(
        @NotBlank(message = "제목은 필수 입력 값입니다.")
        String title,

        @NotBlank(message = "내용은 필수 입력 값입니다.")
        String content,

        // 노출 여부 — 미입력 시 true(노출)로 처리
        Boolean visible,

        // 노출 시작일/종료일 (선택). 둘 다 비우면 계속 노출.
        LocalDate visibleFrom,
        LocalDate visibleUntil
) {
}
