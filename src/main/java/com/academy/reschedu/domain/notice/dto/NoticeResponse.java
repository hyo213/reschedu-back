package com.academy.reschedu.domain.notice.dto;

import com.academy.reschedu.domain.notice.Notice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record NoticeResponse(
        UUID uuid,
        Long academyId,
        String academyName,
        String title,
        String content,
        String authorName,
        String authorRole,
        boolean visible,
        LocalDate visibleFrom,
        LocalDate visibleUntil,
        // 오늘 날짜 기준으로 실제 화면에 노출되는 상태인지 (visible + 기간 조건을 합친 값)
        boolean currentlyVisible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoticeResponse of(Notice notice) {
        return new NoticeResponse(
                notice.getUuid(),
                notice.getAcademy().getId(),
                notice.getAcademy().getName(),
                notice.getTitle(),
                notice.getContent(),
                notice.getAuthor().getName(),
                notice.getAuthor().getRole().name(),
                notice.isVisible(),
                notice.getVisibleFrom(),
                notice.getVisibleUntil(),
                notice.isCurrentlyVisible(LocalDate.now()),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
