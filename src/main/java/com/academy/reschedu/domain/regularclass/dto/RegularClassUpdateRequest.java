package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 원장/강사가 기존 정규 수업 시간표를 수정할 때 사용하는 요청 DTO.
 * studentUuids는 "수정 후 최종 명단"을 의미하며, 서비스 계층에서 기존 명단과 비교해 추가/제거를 반영한다(전체 교체).
 * 요일마다 서로 다른 시간대를 가질 수 있으므로, 요일 하나하나의 시간을 timeSlots에 개별로 담는다.
 * 수강생의 수강 기간은 여기서 다루지 않는다 — [수강생 관리] 화면에서 별도로 관리한다.
 */
public record RegularClassUpdateRequest(
        // 수업명 (선택 입력)
        String title,

        @NotNull(message = "담당 강사는 필수 입력 값입니다.")
        UUID teacherUuid,

        // 강의실 호수 (선택 입력)
        String roomNumber,

        @Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
        int maxCapacity,

        @NotEmpty(message = "수업 요일별 시간을 최소 1개 이상 입력해야 합니다.")
        @Valid
        List<TimeSlotRequest> timeSlots,

        List<UUID> studentUuids
) {
}
