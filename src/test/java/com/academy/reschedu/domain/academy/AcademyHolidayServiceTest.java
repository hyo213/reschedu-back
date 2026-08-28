package com.academy.reschedu.domain.academy;

import com.academy.reschedu.domain.academy.dto.HolidayCreateRequest;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.regularclass.RegularClassService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademyHolidayServiceTest {

    @Mock
    private AcademyHolidayRepository academyHolidayRepository;
    @Mock
    private AcademyRepository academyRepository;
    @Mock
    private RegularClassService regularClassService;
    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @InjectMocks
    private AcademyHolidayService academyHolidayService;

    private Academy academy;
    private Member admin;
    private LocalDate holidayDate;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        admin = new Member("admin@test.com", "encoded", "원장", "010-0000-0001", MemberRole.ADMIN, academy);
        lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);

        holidayDate = LocalDate.now().plusDays(10);
    }

    @Nested
    class CreateHoliday {

        @Test
        void 정상적으로_휴무일을_등록하고_보강권을_발급한다() {
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(academyHolidayRepository.existsByAcademyIdAndDate(1L, holidayDate)).thenReturn(false);
            when(regularClassService.applyHolidayToSessions(academy, holidayDate)).thenReturn(3);

            HolidayCreateRequest request = new HolidayCreateRequest(holidayDate, "임시 휴무", null);
            var response = academyHolidayService.createHoliday(1L, request);

            assertThat(response.issuedTicketCount()).isEqualTo(3);
            verify(academyHolidayRepository).save(any(AcademyHoliday.class));
        }

        @Test
        void 이미_등록된_휴무일이면_예외() {
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(academyHolidayRepository.existsByAcademyIdAndDate(1L, holidayDate)).thenReturn(true);

            HolidayCreateRequest request = new HolidayCreateRequest(holidayDate, "임시 휴무", null);

            assertThatThrownBy(() -> academyHolidayService.createHoliday(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 등록된 휴무일");
            verify(academyHolidayRepository, never()).save(any());
        }

        @Test
        void 학부모는_휴무일을_등록할_수_없다() {
            Member parent = new Member("parent@test.com", "e", "학부모", "010-0000-0002", MemberRole.PARENT, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);

            HolidayCreateRequest request = new HolidayCreateRequest(holidayDate, "임시 휴무", null);

            assertThatThrownBy(() -> academyHolidayService.createHoliday(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("원장 또는 강사만");
        }

        @Test
        void 소속되지_않은_학원의_휴무일은_등록할_수_없다() {
            Academy otherAcademy = Academy.builder().name("다른학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member otherAdmin = new Member("other@test.com", "e", "다른원장", "010-0000-0003", MemberRole.ADMIN, otherAcademy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(otherAdmin);

            HolidayCreateRequest request = new HolidayCreateRequest(holidayDate, "임시 휴무", null);

            assertThatThrownBy(() -> academyHolidayService.createHoliday(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소속 학원의 휴무일만");
        }
    }

    @Nested
    class DeleteHoliday {

        @Test
        void 정상적으로_휴무일을_취소하고_보강권을_회수한다() {
            AcademyHoliday holiday = AcademyHoliday.builder().academy(academy).date(holidayDate).reason("임시 휴무").build();
            UUID holidayUuid = UUID.randomUUID();
            ReflectionTestUtils.setField(holiday, "uuid", holidayUuid);

            when(academyHolidayRepository.findByUuidAndAcademyId(holidayUuid, 1L)).thenReturn(Optional.of(holiday));
            when(regularClassService.revertHolidayForSessions(academy, holidayDate, admin)).thenReturn(2);

            int retracted = academyHolidayService.deleteHoliday(1L, holidayUuid);

            assertThat(retracted).isEqualTo(2);
            verify(academyHolidayRepository).delete(holiday);
        }

        @Test
        void 존재하지_않는_휴무일이면_예외() {
            UUID holidayUuid = UUID.randomUUID();
            when(academyHolidayRepository.findByUuidAndAcademyId(holidayUuid, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> academyHolidayService.deleteHoliday(1L, holidayUuid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
