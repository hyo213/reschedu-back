package com.academy.reschedu.domain.notice;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.notice.dto.NoticeCreateRequest;
import com.academy.reschedu.domain.notice.dto.NoticeResponse;
import com.academy.reschedu.domain.notice.dto.NoticeUpdateRequest;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final AcademyRepository academyRepository;
    private final AcademyStudentRepository academyStudentRepository;
    private final CurrentMemberProvider currentMemberProvider;

    /** 원장/강사 전용: 공지 작성. */
    @Transactional
    public NoticeResponse createNotice(Long academyId, NoticeCreateRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        requireWriter(requester);

        Academy academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원입니다."));

        Notice notice = Notice.builder()
                .academy(academy)
                .author(requester)
                .title(request.title())
                .content(request.content())
                .visible(request.visible() == null || request.visible())
                .visibleFrom(request.visibleFrom())
                .visibleUntil(request.visibleUntil())
                .build();
        noticeRepository.save(notice);

        return NoticeResponse.of(notice);
    }

    /** 원장/강사 전용: 관리용 전체 목록 (노출 꺼짐/기간 만료 포함 전부). */
    public List<NoticeResponse> getNotices(Long academyId) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        requireWriter(requester);

        return noticeRepository.findByAcademyIdOrderByCreatedAtDesc(academyId).stream()
                .map(NoticeResponse::of)
                .toList();
    }

    /** 소속 회원 전원 전용: 오늘 기준 노출 중인 공지만. 학부모는 자녀가 다니는 학원이면 조회 가능. */
    public List<NoticeResponse> getActiveNotices(Long academyId) {
        validateReaderCanAccessAcademy(academyId);

        return noticeRepository.findActiveByAcademyId(academyId, LocalDate.now()).stream()
                .map(NoticeResponse::of)
                .toList();
    }

    /** 학부모 전용: 자녀들이 다니는 모든 학원의 노출 중인 공지를 모아 최신순으로 반환한다. */
    public List<NoticeResponse> getActiveNoticesForMyChildren() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있습니다.");
        }

        Set<Long> academyIds = new LinkedHashSet<>();
        for (AcademyStudent registration : academyStudentRepository.findByStudent_Parent_Id(parent.getId())) {
            academyIds.add(registration.getAcademy().getId());
        }

        LocalDate today = LocalDate.now();
        List<NoticeResponse> merged = new ArrayList<>();
        for (Long academyId : academyIds) {
            noticeRepository.findActiveByAcademyId(academyId, today).stream()
                    .map(NoticeResponse::of)
                    .forEach(merged::add);
        }
        return merged.stream()
                .sorted(Comparator.comparing(NoticeResponse::createdAt).reversed())
                .toList();
    }

    /** 소속 회원 전원 전용: 공지 상세 조회. 학부모는 현재 노출 중인 공지만, 자녀가 다니는 학원이면 조회 가능. */
    public NoticeResponse getNotice(Long academyId, UUID noticeUuid) {
        Member requester = validateReaderCanAccessAcademy(academyId);

        Notice notice = getNoticeOrThrow(noticeUuid, academyId);

        boolean isWriter = requester.getRole() == MemberRole.ADMIN || requester.getRole() == MemberRole.TEACHER;
        if (!isWriter && !notice.isCurrentlyVisible(LocalDate.now())) {
            throw new IllegalArgumentException("존재하지 않는 공지입니다.");
        }

        return NoticeResponse.of(notice);
    }

    /** 원장/강사 전용: 공지 수정. */
    @Transactional
    public NoticeResponse updateNotice(Long academyId, UUID noticeUuid, NoticeUpdateRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        requireWriter(requester);

        Notice notice = getNoticeOrThrow(noticeUuid, academyId);

        notice.update(
                request.title(),
                request.content(),
                request.visible() == null || request.visible(),
                request.visibleFrom(),
                request.visibleUntil()
        );

        return NoticeResponse.of(notice);
    }

    /** 원장/강사 전용: 공지 삭제. */
    @Transactional
    public void deleteNotice(Long academyId, UUID noticeUuid) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        requireWriter(requester);

        noticeRepository.delete(getNoticeOrThrow(noticeUuid, academyId));
    }

    private Notice getNoticeOrThrow(UUID noticeUuid, Long academyId) {
        return noticeRepository.findByUuidAndAcademyId(noticeUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지입니다."));
    }

    private void requireWriter(Member requester) {
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("공지 작성/수정/삭제는 원장 또는 강사만 할 수 있습니다.");
        }
    }

    private Member validateRequesterBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 공지만 조회하거나 관리할 수 있습니다.");
        }
        return requester;
    }

    /** 조회 전용 완화 버전 — 학부모는 본인 소속 학원과 무관하게 자녀가 다니는 학원이면 조회를 허용한다. */
    private Member validateReaderCanAccessAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() != null && requester.getAcademy().getId().equals(academyId)) {
            return requester;
        }
        if (requester.getRole() == MemberRole.PARENT
                && academyStudentRepository.existsByAcademyIdAndStudent_Parent_Id(academyId, requester.getId())) {
            return requester;
        }
        throw new IllegalStateException("소속 학원의 공지만 조회할 수 있습니다.");
    }
}
