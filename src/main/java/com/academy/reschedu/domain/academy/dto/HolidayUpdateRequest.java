package com.academy.reschedu.domain.academy.dto;

/**
 * 휴무일 사유 수정 요청. 날짜는 변경하지 않는다 — 날짜를 바꾸려면 취소 후 재등록해야 한다.
 */
public record HolidayUpdateRequest(
        String reason
) {
}
