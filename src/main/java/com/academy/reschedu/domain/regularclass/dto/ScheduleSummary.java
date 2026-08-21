package com.academy.reschedu.domain.regularclass.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * [수강생 목록] 화면 전용 요약: 한 학생의 활성 배정 중 "요일 하나·시작 시각 하나"를 나타낸다.
 * 한 반이라도 요일마다 시간이 다를 수 있으므로 요일 단위로 펼쳐서 담는다.
 * 화면에서는 이를 요일 순으로 정렬해 "월3수4금5" 같은 압축 표기로 렌더링한다.
 */
public record ScheduleSummary(
        DayOfWeek dayOfWeek,
        LocalTime startTime
) {
}
