package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyUpdateRequest;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakeupTicketPolicyServiceTest {

    @Mock
    private MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    @Mock
    private AcademyRepository academyRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @InjectMocks
    private MakeupTicketPolicyService makeupTicketPolicyService;

    private Academy academy;
    private Member admin;
    private Member teacher;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        admin = new Member("admin@test.com", "e", "원장", "010-0000-0001", MemberRole.ADMIN, academy);
        teacher = new Member("teacher@test.com", "e", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
    }

    @Nested
    class GetPolicy {

        @Test
        void 정책이_없으면_전부_null인_응답을_반환한다() {
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.empty());

            MakeupTicketPolicyResponse response = makeupTicketPolicyService.getPolicy(1L);

            assertThat(response.maxOutstandingTickets()).isNull();
            assertThat(response.monthlyIssueLimit()).isNull();
            assertThat(response.defaultValidityDays()).isNull();
        }

        @Test
        void 소속_학원이_아니면_예외() {
            Academy otherAcademy = Academy.builder().name("다른 학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member otherAdmin = new Member("other@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, otherAcademy);
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(otherAdmin);

            assertThatThrownBy(() -> makeupTicketPolicyService.getPolicy(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소속 학원의 보강권 정책만 조회");
        }
    }

    @Nested
    class UpdatePolicy {

        @Test
        void 원장이_새_정책을_생성한다() {
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.empty());
            when(academyRepository.findById(1L)).thenReturn(Optional.of(academy));
            when(makeupTicketPolicyRepository.save(any(MakeupTicketPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

            MakeupTicketPolicyUpdateRequest request = new MakeupTicketPolicyUpdateRequest(5, 3, 60);
            MakeupTicketPolicyResponse response = makeupTicketPolicyService.updatePolicy(1L, request);

            assertThat(response.maxOutstandingTickets()).isEqualTo(5);
            assertThat(response.monthlyIssueLimit()).isEqualTo(3);
            assertThat(response.defaultValidityDays()).isEqualTo(60);
        }

        @Test
        void 이미_정책이_있으면_값을_갱신한다() {
            MakeupTicketPolicy existing = MakeupTicketPolicy.builder()
                    .academy(academy).maxOutstandingTickets(2).monthlyIssueLimit(1).defaultValidityDays(30)
                    .build();
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(admin);
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.of(existing));
            when(makeupTicketPolicyRepository.save(any(MakeupTicketPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

            MakeupTicketPolicyUpdateRequest request = new MakeupTicketPolicyUpdateRequest(null, null, null);
            MakeupTicketPolicyResponse response = makeupTicketPolicyService.updatePolicy(1L, request);

            assertThat(response.maxOutstandingTickets()).isNull();
            assertThat(response.monthlyIssueLimit()).isNull();
            assertThat(response.defaultValidityDays()).isNull();
        }

        @Test
        void 강사는_정책을_변경할_수_없다() {
            lenient().when(currentMemberProvider.getCurrentMember()).thenReturn(teacher);

            MakeupTicketPolicyUpdateRequest request = new MakeupTicketPolicyUpdateRequest(5, 3, 60);

            assertThatThrownBy(() -> makeupTicketPolicyService.updatePolicy(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("원장만 보강권 전체 정책을 설정");
        }
    }
}
