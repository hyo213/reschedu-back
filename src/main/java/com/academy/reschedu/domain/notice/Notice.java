package com.academy.reschedu.domain.notice;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.common.BaseEntity;
import com.academy.reschedu.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * [Notice] 학원 공지사항
 *
 * 원장/강사가 작성하며, 해당 학원 소속 회원 전원(원장/강사/학부모)이 읽을 수 있다.
 * 노출 여부(visible)와 노출 기간(visibleFrom~visibleUntil)을 함께 관리한다 —
 * 노출 기간을 지정하지 않으면 visible이 true인 한 계속 노출된다.
 */
@Entity
@Table(
        name = "notice",
        uniqueConstraints = @UniqueConstraint(name = "uq_notice_uuid", columnNames = "uuid")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notice_academy"))
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notice_author"))
    private Member author;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    /** 노출 여부 수동 스위치. false면 노출 기간과 무관하게 항상 숨김. */
    @Column(name = "visible", nullable = false)
    private boolean visible;

    /** 노출 시작일 (선택). null이면 등록 즉시부터 노출. */
    @Column(name = "visible_from")
    private LocalDate visibleFrom;

    /** 노출 종료일 (선택, 포함). null이면 계속 노출. */
    @Column(name = "visible_until")
    private LocalDate visibleUntil;

    @Builder
    private Notice(Academy academy, Member author, String title, String content,
                    boolean visible, LocalDate visibleFrom, LocalDate visibleUntil) {
        this.academy = academy;
        this.author = author;
        this.title = title;
        this.content = content;
        this.visible = visible;
        this.visibleFrom = visibleFrom;
        this.visibleUntil = visibleUntil;
    }

    public void update(String title, String content, boolean visible,
                        LocalDate visibleFrom, LocalDate visibleUntil) {
        this.title = title;
        this.content = content;
        this.visible = visible;
        this.visibleFrom = visibleFrom;
        this.visibleUntil = visibleUntil;
    }

    /** 오늘 날짜 기준으로 실제 노출 대상인지 계산한다(수동 스위치 + 기간 둘 다 충족해야 함). */
    public boolean isCurrentlyVisible(LocalDate today) {
        if (!visible) {
            return false;
        }
        if (visibleFrom != null && today.isBefore(visibleFrom)) {
            return false;
        }
        return visibleUntil == null || !today.isAfter(visibleUntil);
    }
}
