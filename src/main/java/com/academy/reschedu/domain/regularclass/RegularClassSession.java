package com.academy.reschedu.domain.regularclass;

import com.academy.reschedu.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * [RegularClassSession] 정규 수업의 "특정 날짜 회차" 개별 인스턴스. RegularClass(마스터 템플릿)와 달리
 * 실제 달력의 한 날짜를 나타내며, 조회 시점에 그 주의 회차를 없으면 생성해 영속화한다. 생성 시점에
 * title/roomNumber/maxCapacity/startTime/endTime을 템플릿에서 복사하므로, 이후 템플릿이 바뀌어도 이미
 * 생성된 회차는 유지된다. 로스터는 RegularClassSessionStudent로 별도 관리한다.
 */
@Entity
@Table(
        name = "regular_class_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_regular_class_session_uuid", columnNames = "uuid"),
                @UniqueConstraint(name = "uq_regular_class_session_date", columnNames = {"regular_class_id", "date"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegularClassSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regular_class_session_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regular_class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_regular_class_session_class"))
    private RegularClass regularClass;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "room_number", length = 30)
    private String roomNumber;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    /** 학원 휴무일로 인해 이 회차 전체가 취소되었는지 여부 */
    @Column(name = "holiday_cancelled", nullable = false)
    private boolean holidayCancelled;

    @Builder
    private RegularClassSession(RegularClass regularClass, LocalDate date, String title, String roomNumber,
                                 LocalTime startTime, LocalTime endTime, int maxCapacity, boolean holidayCancelled) {
        this.regularClass = regularClass;
        this.date = date;
        this.title = title;
        this.roomNumber = roomNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxCapacity = maxCapacity;
        this.holidayCancelled = holidayCancelled;
    }

    /** 템플릿 정원 변경을 이미 생성된 회차 스냅샷(지난 회차 포함)에도 반영한다. */
    public void updateMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    /** 이미 생성된 회차가 뒤늦게 학원 휴무일로 지정되었을 때 호출한다. */
    public void markHolidayCancelled() {
        this.holidayCancelled = true;
    }

    /** 휴무일 지정 취소 시 원래 상태로 되돌린다. */
    public void revertHolidayCancelled() {
        this.holidayCancelled = false;
    }
}
