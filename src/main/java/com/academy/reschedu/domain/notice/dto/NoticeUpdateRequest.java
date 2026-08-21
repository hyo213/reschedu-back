package com.academy.reschedu.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record NoticeUpdateRequest(
        @NotBlank(message = "제목은 필수 입력 값입니다.")
        String title,

        @NotBlank(message = "내용은 필수 입력 값입니다.")
        String content,

        Boolean visible,

        LocalDate visibleFrom,
        LocalDate visibleUntil
) {
}
