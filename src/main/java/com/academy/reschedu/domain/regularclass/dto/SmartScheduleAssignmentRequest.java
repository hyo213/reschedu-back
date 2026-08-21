package com.academy.reschedu.domain.regularclass.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * [수강생 관리] 화면에서 학생에게 새 요일/시간을 배정할 때 쓰는 요청 DTO.
 * 요청된 timeSlots 중 이 강사가 이미 같은 요일·시간으로 진행 중인 반과 겹치는 요일은 그 기존 반에
 * 합류시키고, 겹치지 않는 나머지 요일만 모아 새 반을 만든다(RegularClassService.smartAssignStudentSchedule 참고).
 * fromRegularClassUuid를 지정하면 요일 변경(기존 배정을 effectiveFrom 전날로 종료)도 함께 처리한다.
 */
public record SmartScheduleAssignmentRequest(
        @NotNull(message = "수강생 정보는 필수입니다.")
        UUID studentUuid,

        @NotNull(message = "담당 강사는 필수 입력 값입니다.")
        UUID teacherUuid,

        // 겹치지 않는 요일이 있어 새 반을 만들어야 할 때 쓸 정보 (선택 입력)
        String title,
        String roomNumber,

        @Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
        int maxCapacity,

        @NotEmpty(message = "수업 요일별 시간을 최소 1개 이상 입력해야 합니다.")
        @Valid
        List<TimeSlotRequest> timeSlots,

        @NotNull(message = "적용 시작일은 필수입니다.")
        LocalDate effectiveFrom,

        UUID fromRegularClassUuid
) {
}
