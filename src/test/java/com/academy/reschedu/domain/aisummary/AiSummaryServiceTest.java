package com.academy.reschedu.domain.aisummary;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.aisummary.dto.ChildAiSummaryResponse;
import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicy;
import com.academy.reschedu.domain.makeup.MakeupTicketSource;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicyRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        // 정책 행이 없으면(기본값) 만료 후 사용 허용 — 이 값을 검증하는 테스트에서만 개별적으로 다시 스텁한다.
        lenient().when(makeupTicketPolicyRepository.findByAcademy_Id(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
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
        }

        @Test
        void AI_문장이_캐시에_있으면_그대로_쓰되_통계는_항상_새로_계산한다() {
            // 결석 신청 직후 대시보드로 돌아와도 숫자는 바로 반영돼야 하므로, 캐시는 Gemini 문장에만
            // 적용되고 통계(결석 수 등)는 캐시 히트여도 매번 리포지토리에서 다시 읽어야 한다.
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn("캐시된 요약");
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(5L); // 캐시 문장 생성 이후 새로 결석 신청이 들어와 늘어난 값이라고 가정
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).summaryText()).isEqualTo("캐시된 요약");
            assertThat(result.get(0).absenceCountThisMonth()).isEqualTo(5L); // 캐시가 아니라 방금 계산된 최신 값
            verify(geminiClient, never()).generate(anyString(), anyString());
            verify(bucket, never()).set(anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class));
        }

        @Test
        void 캐시가_없으면_통계를_계산하고_Gemini_응답을_요약으로_담아_캐시에_저장한다() {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);

            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, monthStart, today)).thenReturn(2L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(3L);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("AI가 생성한 제법 긴 요약 문장입니다"));

            List<ChildAiSummaryResponse> result = aiSummaryService.getMyChildrenAiSummaries();

            assertThat(result).hasSize(1);
            ChildAiSummaryResponse summary = result.get(0);
            assertThat(summary.absenceCountThisMonth()).isEqualTo(2L);
            assertThat(summary.usedTicketCount()).isEqualTo(3L);
            assertThat(summary.availableTicketCount()).isZero();
            assertThat(summary.summaryText()).isEqualTo("AI가 생성한 제법 긴 요약 문장입니다");
            verify(bucket).set(anyString(), eq(java.time.Duration.ofHours(26)));
        }

        @Test
        void 첫_응답이_너무_짧으면_재시도해서_유효한_문장이_나오면_그걸_캐시에_저장한다() {
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
            when(geminiClient.generate(anyString(), anyString()))
                    .thenReturn(Optional.of("테스트자녀1 학생")) // 비정상적으로 짧게 끊긴 첫 응답
                    .thenReturn(Optional.of("재시도 끝에 정상적으로 완성된 요약 문장입니다"));

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.summaryText()).isEqualTo("재시도 끝에 정상적으로 완성된 요약 문장입니다");
            verify(geminiClient, org.mockito.Mockito.times(2)).generate(anyString(), anyString());
            verify(bucket).set(eq("재시도 끝에 정상적으로 완성된 요약 문장입니다"), eq(java.time.Duration.ofHours(26)));
        }

        @Test
        void 재시도까지_모두_짧으면_summaryText는_null이고_캐시에도_저장하지_않는다() {
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("너무 짧음"));

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.summaryText()).isNull();
            verify(geminiClient, org.mockito.Mockito.times(2)).generate(anyString(), anyString());
            verify(bucket, never()).set(anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class));
        }

        @Test
        void Gemini_호출이_실패하면_summaryText는_null이고_통계는_정상_반환된다() {
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
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
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(validFar, validSoon)); // expired는 UNUSED 조회 대상이 아니므로 제외
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

            ChildAiSummaryResponse summary = aiSummaryService.getMyChildrenAiSummaries().get(0);

            assertThat(summary.availableTicketCount()).isEqualTo(2L);
            assertThat(summary.expiringSoonTicketCount()).isEqualTo(1L); // validSoon(3일 후)만 7일 임박에 해당
        }

        @Test
        void 수강_기간이_없으면_남은_일수는_null이다() {
            LocalDate today = LocalDate.now();
            when(currentMemberProvider.getCurrentMember()).thenReturn(parent);
            when(academyStudentRepository.findByStudent_Parent_Id(10L)).thenReturn(List.of(academyStudent));
            when(bucket.get()).thenReturn(null);
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
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
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of());
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
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
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(validTicket));
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.empty()); // 정책 없음 = 기본 허용
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
            when(makeupTicketRepository.countByAcademyStudent_IdAndAbsentDateBetween(40L, today.withDayOfMonth(1), today))
                    .thenReturn(0L);
            when(makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(40L, MakeupTicketStatus.UNUSED))
                    .thenReturn(List.of(validTicket));
            when(makeupTicketRepository.countByAcademyStudent_IdAndStatus(40L, MakeupTicketStatus.USED)).thenReturn(0L);
            when(makeupTicketPolicyRepository.findByAcademy_Id(1L)).thenReturn(Optional.of(policy));
            when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("충분히 긴 테스트용 요약 문장입니다"));

            aiSummaryService.getMyChildrenAiSummaries();

            ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).generate(anyString(), userMessageCaptor.capture());
            assertThat(userMessageCaptor.getValue()).doesNotContain("수강기간만료후보강권사용가능");
        }
    }
}
