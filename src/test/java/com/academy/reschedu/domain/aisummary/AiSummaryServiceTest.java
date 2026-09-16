package com.academy.reschedu.domain.aisummary;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.aisummary.dto.ChildAiSummaryResponse;
import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicy;
import com.academy.reschedu.domain.makeup.MakeupTicketSource;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicyRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketRepository;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock
    private AcademyStudentRepository academyStudentRepository;
    @Mock
    private MakeupTicketRepository makeupTicketRepository;
    @Mock
    private MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    @Mock
    private CurrentMemberProvider currentMemberProvider;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> bucket;
    @Mock
    private GeminiClient geminiClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiSummaryService aiSummaryService;

    private Academy academy;
    private Member parent;
    private Student child;
    private AcademyStudent academyStudent;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        parent = new Member("parent@test.com", "encoded", "학부모", "010-0000-0001", MemberRole.PARENT, academy);
        ReflectionTestUtils.setField(parent, "id", 10L);

        child = new Student("아이", LocalDate.of(2015, 1, 1), "MALE", null, parent);

        academyStudent = new AcademyStudent(academy, child, "아이", null, true, null, null, null, null, null);
        ReflectionTestUtils.setField(academyStudent, "id", 40L);

        lenient().when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        // 기본값: 보강권 없음 / 정책 없음(=만료 후 사용 허용). 개별 테스트에서 필요할 때만 다시 스텁한다.
        lenient().when(makeupTicketRepository.findByAcademyStudent_IdIn(anyList())).thenReturn(List.of());
        lenient().when(makeupTicketPolicyRepository.findByAcademy_IdIn(anyList())).thenReturn(List.of());
    }

    /** 결석 집계용 — absentDate만 채우고 결과에는 영향 없게 사용 완료 처리해서 반환한다. */
    private MakeupTicket absenceTicket(LocalDate absentDate) {
        MakeupTicket ticket = MakeupTicket.builder()
                .academyStudent(academyStudent).absentDate(absentDate).source(MakeupTicketSource.STUDENT_ABSENCE)
                .build();
        ticket.use();
        return ticket;
    }

    /** 사용완료 집계용 — absentDate 없이(수동 지급) 사용 완료 처리해서 반환한다. */
    private MakeupTicket usedTicket() {
        MakeupTicket ticket = MakeupTicket.builder()
                .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
        ticket.use();
        return ticket;
    }

    @Nested
    class GetMyChildrenAiSummaries {

        @Test
        void 학부모가_아니면_예외() {
            Member teacher = new Member("t@test.com", "e", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
            when(currentMemberProvider.getCurrentMember()).thenReturn(teacher);

            assertThatThrownBy(() -> aiSummaryService.getMyChildrenAiSummaries())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("학부모");
        }

        @Test
        void 승인되지_않은_등록은_제외한다() {
            AcademyStudent notApproved = new AcademyStudent(academy, child, "아이", null, false, null, null, null, null, null);

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(notApproved));

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).isEmpty();
            verify(makeupTicketRepository, never()).findByAcademyStudent_IdIn(anyList());
        }

        @Test
        void 자녀가_여러_명이어도_보강권과_정책_조회는_한_번씩만_한다() {
            Student child2 = new Student("아이2", LocalDate.of(2016, 1, 1), "FEMALE", null, parent);
            AcademyStudent academyStudent2 = new AcademyStudent(academy, child2, "아이2", null, true, null, null, null, null, null);
            ReflectionTestUtils.setField(academyStudent2, "id", 41L);

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L))
                    .thenReturn(List.of(academyStudent, academyStudent2));
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).hasSize(2);
            // N+1 회피 확인: 학생이 2명이어도 보강권/정책 조회는 각각 1번(학생 id 목록/학원 id 목록을 한 번에)만 나가야 한다.
            verify(makeupTicketRepository, times(1)).findByAcademyStudent_IdIn(eq(List.of(40L, 41L)));
            verify(makeupTicketPolicyRepository, times(1)).findByAcademy_IdIn(eq(List.of(1L)));
        }

        @Test
        void AI_문장이_캐시에_있으면_그대로_쓰되_통계는_항상_새로_계산한다() {
            // 결석 신청 직후 대시보드로 돌아와도 숫자는 바로 반영돼야 하므로, 캐시는 Gemini 문장에만
            // 적용되고 통계(결석 수 등)는 캐시 히트여도 매번 리포지토리에서 다시 읽어야 한다.
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn("캐시된 요약");
            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L))).thenReturn(List.of(
                    absenceTicket(today), absenceTicket(today), absenceTicket(today),
                    absenceTicket(today), absenceTicket(today))); // 캐시 문장 생성 이후 새로 결석 신청이 들어와 5회로 늘어났다고 가정

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).summaryText()).isEqualTo("캐시된 요약");
            assertThat(result.get(0).absenceCountThisMonth()).isEqualTo(5L); // 캐시가 아니라 방금 계산된 최신 값
            verify(geminiClient, never()).generate(anyString(), anyString());
            verify(bucket, never()).set(anyString(), any(Duration.class));
        }

        @Test
        void 캐시가_없으면_통계를_계산하고_Gemini_응답을_요약으로_담아_캐시에_저장한다() {
            LocalDate today = LocalDate.now();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L))).thenReturn(List.of(
                    absenceTicket(today), absenceTicket(today), // 이번 달 결석 2회(둘 다 사용 완료 처리됨)
                    usedTicket())); // 결석과 무관하게 사용 완료된 보강권 1장 추가 → 총 사용완료 3장
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("AI가 생성한 제법 긴 요약 문장입니다"));

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).hasSize(1);
            ChildAiSummaryResponse summary = result.get(0);
            assertThat(summary.absenceCountThisMonth()).isEqualTo(2L);
            assertThat(summary.usedTicketCount()).isEqualTo(3L);
            assertThat(summary.availableTicketCount()).isZero();
            assertThat(summary.summaryText()).isEqualTo("AI가 생성한 제법 긴 요약 문장입니다");
            verify(bucket).set(anyString(), eq(Duration.ofHours(26)));
        }

        @Test
        void 학생_상황이_바뀌면_같은_날짜여도_다른_캐시_키로_Gemini를_다시_호출한다() {
            // 결석 신청/재등록 등으로 하루 안에 상황이 바뀌어도, 날짜 기반 캐시였다면 다음날까지 옛
            // 문장이 남아있었을 시나리오 — 해시 기반 캐시는 상황이 바뀌면 키 자체가 달라져야 한다.
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("충분히 긴 테스트용 요약 문장입니다"));

            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L))).thenReturn(List.of());
            aiSummaryService.getMyChildrenAiSummaries(); // 1차: 결석 0회일 때

            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L)))
                    .thenReturn(List.of(absenceTicket(today)));
            aiSummaryService.getMyChildrenAiSummaries(); // 2차: 그사이 결석 신청이 들어와 1회로 바뀜

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(redissonClient, times(2)).getBucket(keyCaptor.capture());
            assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
            verify(geminiClient, times(2)).generate(anyString(), anyString());
        }

        @Test
        void 첫_응답이_너무_짧으면_재시도해서_유효한_문장이_나오면_그걸_캐시에_저장한다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString()))
                    .thenReturn(Optional.of("테스트자녀1 학생")) // 비정상적으로 짧게 끊긴 첫 응답
                    .thenReturn(Optional.of("재시도 끝에 정상적으로 완성된 요약 문장입니다"));

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.summaryText()).isEqualTo("재시도 끝에 정상적으로 완성된 요약 문장입니다");
            verify(geminiClient, times(2)).generate(anyString(), anyString());
            verify(bucket).set(eq("재시도 끝에 정상적으로 완성된 요약 문장입니다"), eq(Duration.ofHours(26)));
        }

        @Test
        void 재시도까지_모두_짧으면_summaryText는_null이고_캐시에도_저장하지_않는다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("너무 짧음"));

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.summaryText()).isNull();
            verify(geminiClient, times(2)).generate(anyString(), anyString());
            verify(bucket, never()).set(anyString(), any(Duration.class));
        }

        @Test
        void Gemini_호출이_실패하면_summaryText는_null이고_통계는_정상_반환된다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result.get(0).summaryText()).isNull();
        }

        @Test
        void 보강권_상태와_만료임박을_정확히_분류한다() {
            LocalDate today = LocalDate.now();
            MakeupTicket validFar = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT)
                    .expiredAt(today.plusDays(30).atStartOfDay()).build();
            MakeupTicket validSoon = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT)
                    .expiredAt(today.plusDays(3).atStartOfDay()).build();
            MakeupTicket expired = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT)
                    .expiredAt(today.minusDays(1).atStartOfDay()).build();
            expired.expire();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            // EXPIRED 상태인 티켓도 함께 내려줘서, 이제는 서버 쿼리가 아니라 서비스가 메모리에서
            // UNUSED만 걸러내는지(isCurrentlyValid 로직)까지 검증한다.
            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L)))
                    .thenReturn(List.of(validFar, validSoon, expired));
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.availableTicketCount()).isEqualTo(2L);
            assertThat(summary.expiringSoonTicketCount()).isEqualTo(1L); // validSoon(3일 후)만 7일 임박에 해당
        }

        @Test
        void 수강_기간이_없으면_남은_일수는_null이다() {
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.enrollmentDaysRemaining()).isNull();
        }

        @Test
        void 수강_기간이_있으면_남은_일수를_계산한다() {
            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusDays(15);
            academyStudent.updateEnrollmentPeriod(today.minusDays(10), endDate);

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.enrollmentDaysRemaining()).isEqualTo(ChronoUnit.DAYS.between(today, endDate));
        }

        @Test
        void 수강기간_만료후_사용이_정책상_허용되면_프롬프트에_해당_신호를_넣는다() {
            LocalDate today = LocalDate.now();
            academyStudent.updateEnrollmentPeriod(today.minusMonths(3), today.minusDays(10));
            MakeupTicket validTicket = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L))).thenReturn(List.of(validTicket));
            // 정책 조회 결과가 비어있음(기본값 = 만료 후 사용 허용)은 setUp()의 기본 스텁을 그대로 쓴다.
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("충분히 긴 테스트용 요약 문장입니다"));

            aiSummaryService.getMyChildrenAiSummaries();

            ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).generate(anyString(), userMessageCaptor.capture());
            assertThat(userMessageCaptor.getValue()).contains("수강기간만료후보강권사용가능");
        }

        @Test
        void 수강기간_만료후_사용이_정책상_금지되면_프롬프트에_해당_신호를_넣지_않는다() {
            LocalDate today = LocalDate.now();
            academyStudent.updateEnrollmentPeriod(today.minusMonths(3), today.minusDays(10));
            MakeupTicket validTicket = MakeupTicket.builder()
                    .academyStudent(academyStudent).source(MakeupTicketSource.MANUAL_GRANT).build();
            MakeupTicketPolicy policy = MakeupTicketPolicy.builder()
                    .academy(academy).allowUseAfterEnrollmentExpired(false).build();

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.findByAcademyStudent_IdIn(List.of(40L))).thenReturn(List.of(validTicket));
            when(makeupTicketPolicyRepository.findByAcademy_IdIn(List.of(1L))).thenReturn(List.of(policy));
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("충분히 긴 테스트용 요약 문장입니다"));

            aiSummaryService.getMyChildrenAiSummaries();

            ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).generate(anyString(), userMessageCaptor.capture());
            assertThat(userMessageCaptor.getValue()).doesNotContain("수강기간만료후보강권사용가능");
        }
    }
}
