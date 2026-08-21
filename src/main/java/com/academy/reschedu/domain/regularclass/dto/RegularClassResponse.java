package com.academy.reschedu.domain.regularclass.dto;

import com.academy.reschedu.domain.regularclass.RegularClass;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record RegularClassResponse(
        UUID uuid,
        String title,
        UUID teacherUuid,
        String teacherName,
        String roomNumber,
        // 🎯 학부모 시간표 뷰 간소화용 — 수업 정보(학원명/수업명/강의실)를 함께 표시하기 위함.
        String academyName,
        int maxCapacity,
        int currentCount,
        // 🎯 요일마다 서로 다른 시간대를 가질 수 있다(예: 월 15시, 수 17시, 금 20시) — 요일 순으로 정렬해 내려준다.
        List<TimeSlotResponse> timeSlots,
        List<RosterStudentResponse> students,
        // 특정 주(week)를 지정해 조회했을 때만 채워진다. 지정하지 않으면 빈 리스트.
        List<WeeklyOccurrenceResponse> weeklyOccurrences
) {
    private static final Comparator<TimeSlotResponse> BY_DAY_OF_WEEK =
            Comparator.comparing((TimeSlotResponse t) -> t.dayOfWeek().getValue());

    /**
     * @param currentCount "현재 인원"으로 표시할 값. 학생별 수강 기간에 따라 달라지므로
     *                     엔티티가 아니라 호출자(서비스 계층)가 기준 날짜를 정해 계산해서 넘긴다.
     */
    public static RegularClassResponse of(RegularClass regularClass, List<RosterStudentResponse> students,
                                           int currentCount, List<WeeklyOccurrenceResponse> weeklyOccurrences) {
        // 🚨 지연 로딩(LAZY) 컬렉션 참조를 그대로 담으면 트랜잭션(Hibernate Session) 종료 후
        // 컨트롤러 계층에서 JSON 직렬화할 때 LazyInitializationException이 발생한다.
        // 트랜잭션이 살아있는 이 시점(서비스 계층)에서 실제 값으로 즉시 복사해 안전하게 분리한다.
        List<TimeSlotResponse> timeSlots = regularClass.getTimeSlots().stream()
                .map(TimeSlotResponse::from)
                .sorted(BY_DAY_OF_WEEK)
                .toList();

        return new RegularClassResponse(
                regularClass.getUuid(),
                regularClass.getTitle(),
                regularClass.getTeacher().getUuid(),
                regularClass.getTeacher().getName(),
                regularClass.getRoomNumber(),
                regularClass.getAcademy().getName(),
                regularClass.getMaxCapacity(),
                currentCount,
                timeSlots,
                students,
                weeklyOccurrences
        );
    }
}
