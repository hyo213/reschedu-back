package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [RegularClass] 정규 반 엔티티(마스터 정보) — 학원의 고정 수업 반. 특정 날짜의 수업 세션은
 * RegularClassSession이 별도로 담당한다. 정원(인원 수)은 고정 카운트 컬럼 없이, 서비스 계층에서
 * 로스터(RegularClassStudent)를 기준 날짜로 동적 계산한다.
 */
@Entity
@Table(
        name = "regular_class",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_regular_class_uuid", columnNames = "uuid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegularClass extends BaseEntity {

    // ─── 식별자 ────────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regular_class_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    // ─── 반 기본 정보 ──────────────────────────────────────────────────────────

    /** 소속 학원 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_academy"))
    private Academy academy;

    /** 수업명 (선택 입력) */
    @Column(name = "title", length = 100)
    private String title;

    /** 담당 강사 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_teacher"))
    private Member teacher;

    /** 강의실 호수 (예: "3강의실", "B-201") — 선택 입력 */
    @Column(name = "room_number", length = 30)
    private String roomNumber;

    // ─── 시간표 정보 ──────────────────────────────────────────────────────────

    /** 요일별 개별 시간대(복수 가능). 별도 컬렉션 테이블(regular_class_time_slot)에 저장된다. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "regular_class_time_slot",
            joinColumns = @JoinColumn(name = "regular_class_id",
                    foreignKey = @ForeignKey(name = "fk_regular_class_time_slot"))
    )
    private Set<RegularClassTimeSlot> timeSlots = new LinkedHashSet<>();

    // ─── 정원 관리 ──────────────────────────────────────────────────────────────

    /** 이 반의 최대 정원. 보강생도 이 정원을 함께 소비한다. */
    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    /** 이 반을 종료한 날짜(효력일 지정). null이면 계속 진행 중인 반이다. */
    @Column(name = "discontinued_from")
    private LocalDate discontinuedFrom;

    // ─── 생성자 (Builder) ──────────────────────────────────────────────────────

    @Builder
    private RegularClass(Academy academy, String title, Member teacher, String roomNumber, int maxCapacity,
                          Set<RegularClassTimeSlot> timeSlots) {
        this.academy      = academy;
        this.title        = title;
        this.teacher      = teacher;
        this.roomNumber   = roomNumber;
        this.maxCapacity  = maxCapacity;
        this.timeSlots    = timeSlots != null ? new LinkedHashSet<>(timeSlots) : new LinkedHashSet<>();
    }

    // ─── 비즈니스 메서드 ────────────────────────────────────────────────────────

    /** 시간표 기본 정보 수정. 수강생 명단/정원 검증은 서비스 계층에서 관리한다. */
    public void update(String title, Member teacher, String roomNumber, int maxCapacity,
                        Set<RegularClassTimeSlot> timeSlots) {
        this.title       = title;
        this.teacher     = teacher;
        this.roomNumber  = roomNumber;
        this.maxCapacity = maxCapacity;
        this.timeSlots   = timeSlots != null ? new LinkedHashSet<>(timeSlots) : new LinkedHashSet<>();
    }

    /** 반 종료(효력일 지정). effectiveFrom 이전 기록(과거 시간표/수강 히스토리)은 그대로 유지된다. */
    public void discontinue(LocalDate effectiveFrom) {
        this.discontinuedFrom = effectiveFrom;
    }

    /** 주어진 날짜가 종료 효력일 이후(포함)인지 — 종료된 반은 그 이후로 시간표/보강매칭 어디에도 노출되지 않는다. */
    public boolean isDiscontinuedOn(LocalDate date) {
        return discontinuedFrom != null && !date.isBefore(discontinuedFrom);
    }

    /** 이 반이 진행되는 요일 집합 — timeSlots에서 파생된 값이다(직접 저장하지 않음). */
    public Set<DayOfWeek> getDaysOfWeek() {
        return timeSlots.stream().map(RegularClassTimeSlot::getDayOfWeek).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 특정 요일에 이 반이 진행되는 시간대를 찾는다. 그 요일에 수업이 없으면 비어 있다. */
    public Optional<RegularClassTimeSlot> getTimeSlotFor(DayOfWeek dayOfWeek) {
        return timeSlots.stream().filter(slot -> slot.getDayOfWeek() == dayOfWeek).findFirst();
    }
}