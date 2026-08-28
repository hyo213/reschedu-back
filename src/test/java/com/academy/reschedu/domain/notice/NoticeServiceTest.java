package com.academy.reschedu.domain.notice;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.notice.dto.NoticeCreateRequest;
import com.academy.reschedu.domain.notice.dto.NoticeResponse;
import com.academy.reschedu.domain.notice.dto.NoticeUpdateRequest;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;
    @Mock
    private AcademyRepository academyRepository;
    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @InjectMocks
    private NoticeService noticeService;

    private Academy academy;
    private Member admin;
    private Member parent;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        admin = new Member("admin@test.com", "e", "원장", "010-0000-0001", MemberRole.ADMIN, academy);
        parent = new Member("parent@test.com", "e", "학부모", "010-0000-0002", MemberRole.PARENT, null);
    }

    private Notice buildNotice(boolean visible, LocalDate visibleFrom, LocalDate visibleUntil) {
        Notice notice = Notice.builder()
                .academy(academy).author(admin).title("제목").content("내용")
                .visible(visible).visibleFrom(visibleFrom).visibleUntil(visibleUntil)
                .build();
        ReflectionTestUtils.setField(notice, "uuid", UUID.randomUUID());
        ReflectionTestUtils.setField(notice, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(notice, "updatedAt", LocalDateTime.now());
        return notice;
    }

    @Nested
    class CreateNotice {

        @Test
        void 원장이_공지를_작성한다() {
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

            NoticeCreateRequest request = new NoticeCreateRequest("제목", "내용", true, null, null);
            NoticeResponse response = noticeService.createNotice(1L, request);

            assertThat(response.title()).isEqualTo("제목");
        }

        @Test
        void 학부모는_공지를_작성할_수_없다() {
            Member parentInAcademy = new Member("p2@test.com", "e", "학부모", "010-0000-0003", MemberRole.PARENT, academy);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(parentInAcademy);

            NoticeCreateRequest request = new NoticeCreateRequest("제목", "내용", true, null, null);

            assertThatThrownBy(() -> noticeService.createNotice(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("원장 또는 강사만");
        }
    }

    @Nested
    class GetNotice {

        @Test
        void 작성자는_노출_꺼진_공지도_조회할_수_있다() {
            Notice hidden = buildNotice(false, null, null);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(noticeRepository.findByUuidAndAcademyId(hidden.getUuid(), 1L)).thenReturn(Optional.of(hidden));

            NoticeResponse response = noticeService.getNotice(1L, hidden.getUuid());

            assertThat(response.visible()).isFalse();
        }

        @Test
        void 학부모는_노출_꺼진_공지를_조회할_수_없다() {
            Notice hidden = buildNotice(false, null, null);
            Member parentInAcademy = new Member("p2@test.com", "e", "학부모", "010-0000-0003", MemberRole.PARENT, academy);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(parentInAcademy);
            when(noticeRepository.findByUuidAndAcademyId(hidden.getUuid(), 1L)).thenReturn(Optional.of(hidden));

            assertThatThrownBy(() -> noticeService.getNotice(1L, hidden.getUuid()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 공지");
        }
    }

    @Nested
    class GetActiveNoticesForMyChildren {

        @Test
        void 자녀가_다니는_여러_학원의_공지를_최신순으로_합친다() {
            Academy academy2 = Academy.builder().name("두번째 학원").build();
            ReflectionTestUtils.setField(academy2, "id", 2L);

            var registration1 = new com.academy.reschedu.domain.member.AcademyStudent(
                    academy, mockStudent(parent), "아이1", null, true, null, null, null, null, null);
            var registration2 = new com.academy.reschedu.domain.member.AcademyStudent(
                    academy2, mockStudent(parent), "아이2", null, true, null, null, null, null, null);

            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(parent.getId()))
                    .thenReturn(List.of(registration1, registration2));

            Notice older = buildNotice(true, null, null);
            ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.now().minusDays(1));
            Notice newer = buildNotice(true, null, null);
            ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.now());

            when(noticeRepository.findActiveByAcademyId(org.mockito.ArgumentMatchers.eq(1L), any()))
                    .thenReturn(List.of(older));
            when(noticeRepository.findActiveByAcademyId(org.mockito.ArgumentMatchers.eq(2L), any()))
                    .thenReturn(List.of(newer));

            List<NoticeResponse> result = noticeService.getActiveNoticesForMyChildren();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).uuid()).isEqualTo(newer.getUuid());
            assertThat(result.get(1).uuid()).isEqualTo(older.getUuid());
        }

        @Test
        void 학부모가_아니면_예외() {
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);

            assertThatThrownBy(() -> noticeService.getActiveNoticesForMyChildren())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("학부모 계정만");
        }

        private com.academy.reschedu.domain.member.Student mockStudent(Member parentMember) {
            return new com.academy.reschedu.domain.member.Student(
                    "아이", LocalDate.of(2015, 1, 1), "MALE", null, parentMember);
        }
    }

    @Nested
    class UpdateAndDelete {

        @Test
        void 원장이_공지를_수정한다() {
            Notice notice = buildNotice(true, null, null);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(noticeRepository.findByUuidAndAcademyId(notice.getUuid(), 1L)).thenReturn(Optional.of(notice));

            NoticeUpdateRequest request = new NoticeUpdateRequest("새 제목", "새 내용", true, null, null);
            NoticeResponse response = noticeService.updateNotice(1L, notice.getUuid(), request);

            assertThat(response.title()).isEqualTo("새 제목");
        }

        @Test
        void 원장이_공지를_삭제한다() {
            Notice notice = buildNotice(true, null, null);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(noticeRepository.findByUuidAndAcademyId(notice.getUuid(), 1L)).thenReturn(Optional.of(notice));

            noticeService.deleteNotice(1L, notice.getUuid());

            org.mockito.Mockito.verify(noticeRepository).delete(notice);
        }
    }
}
