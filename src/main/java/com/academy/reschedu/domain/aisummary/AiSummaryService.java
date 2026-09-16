package com.academy.reschedu.domain.aisummary;

import com.academy.reschedu.domain.aisummary.dto.ChildAiSummaryResponse;
import com.academy.reschedu.domain.makeup.MakeupTicket;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicy;
import com.academy.reschedu.domain.makeup.MakeupTicketPolicyRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketRepository;
import com.academy.reschedu.domain.makeup.MakeupTicketStatus;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 학부모 대시보드 "이번 달 자녀 리포트" — 출결(결석)/보강권/수강 기간 통계는 매번 DB에서 새로 계산하고
 * (가벼운 조회라 캐싱할 이유가 없고, 그래야 결석 신청 직후 대시보드로 돌아와도 바로 반영된다),
 * {@link GeminiClient}가 그 숫자를 자연어로 서술한 "문장"만 Redis에 캐싱한다(비용/지연이 큰 건 LLM
 * 호출뿐이므로). 캐시 키는 날짜가 아니라 Gemini에 보내는 입력(userMessage)의 해시다 — 그래야 결석 신청,
 * 보강권 변동, 재등록 등으로 이 학생의 실제 상황이 바뀔 때만 자동으로 새 문장이 생성되고, 안 바뀌었으면
 * 계속 캐시를 재사용한다. 모든 mutation 지점에 캐시 무효화 코드를 흩뿌리지 않아도 되는 대신이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSummaryService {

    private static final String CACHE_KEY_PREFIX = "ai-summary-text:";
    // 프레시함은 이제 해시가 보장하므로, TTL은 순수히 Redis 저장 공간 정리용(오래 안 쓰인 캐시 자연 소멸).
    // 수강기간남은일수가 매일 -1씩 바뀌는 학생은 어차피 해시가 매일 갈리므로 TTL을 길게 잡아도 이득이
    // 없다 — 길게 잡을수록 도달 불가능해진 옛 키가 Redis에 더 오래 남을 뿐이라 짧게 유지한다.
    private static final Duration CACHE_TTL = Duration.ofHours(26);
    private static final long EXPIRING_SOON_DAYS = 7;
    private static final int MIN_VALID_SUMMARY_LENGTH = 15;
    private static final int MAX_GENERATION_ATTEMPTS = 2;

    private final AcademyStudentRepository academyStudentRepository;
    private final MakeupTicketRepository makeupTicketRepository;
    private final MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    private final CurrentMemberProvider currentMemberProvider;
    private final RedissonClient redissonClient;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /** 학부모 전용: 본인 자녀 전원(자녀×학원 등록 단위)의 리포트를 반환한다. */
    public List<ChildAiSummaryResponse> getMyChildrenAiSummaries() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있습니다.");
        }

        return academyStudentRepository.findByStudent_Parent_Id(parent.getId()).stream()
                .filter(AcademyStudent::isApproved)
                .map(this::buildSummary)
                .toList();
    }

    private ChildAiSummaryResponse buildSummary(AcademyStudent academyStudent) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        long absenceCountThisMonth = makeupTicketRepository
                .countByAcademyStudent_IdAndAbsentDateBetween(academyStudent.getId(), monthStart, today);

        List<MakeupTicket> unusedTickets = makeupTicketRepository.findByAcademyStudent_IdAndStatusOrderByAbsentDateDesc(
                academyStudent.getId(), MakeupTicketStatus.UNUSED);
        long availableTicketCount = unusedTickets.stream().filter(MakeupTicket::isCurrentlyValid).count();
        long expiringSoonTicketCount = unusedTickets.stream()
                .filter(MakeupTicket::isCurrentlyValid)
                .filter(t -> t.getExpiredAt() != null
                        && !t.getExpiredAt().toLocalDate().isAfter(today.plusDays(EXPIRING_SOON_DAYS)))
                .count();
        long usedTicketCount = makeupTicketRepository
                .countByAcademyStudent_IdAndStatus(academyStudent.getId(), MakeupTicketStatus.USED);

        LocalDate enrollmentEndDate = academyStudent.getEnrollmentEndDate();
        Long enrollmentDaysRemaining = enrollmentEndDate != null
                ? ChronoUnit.DAYS.between(today, enrollmentEndDate)
                : null;

        String studentName = academyStudent.getStudent().getName();
        String academyName = academyStudent.getAcademy().getName();
        boolean allowUseAfterEnrollmentExpired = makeupTicketPolicyRepository
                .findByAcademy_Id(academyStudent.getAcademy().getId())
                .map(MakeupTicketPolicy::isAllowUseAfterEnrollmentExpired)
                .orElse(true);
        String userMessage = userMessage(studentName, academyName, absenceCountThisMonth, availableTicketCount,
                expiringSoonTicketCount, usedTicketCount, enrollmentDaysRemaining, allowUseAfterEnrollmentExpired);
        String summaryText = getOrGenerateSummaryText(academyStudent, userMessage);

        return new ChildAiSummaryResponse(
                academyStudent.getStudent().getUuid(),
                studentName,
                academyStudent.getAcademy().getId(),
                academyName,
                absenceCountThisMonth,
                availableTicketCount,
                expiringSoonTicketCount,
                usedTicketCount,
                enrollmentDaysRemaining,
                summaryText
        );
    }

    /**
     * 문장은 학생당 "입력이 바뀌지 않는 한" 캐싱한다 — 캐시 키에 날짜 대신 이 학생의 출결/보강권/수강기간
     * 데이터(userMessage)의 해시를 쓴다. 그래서 결석 신청, 보강권 변동, 재등록 등으로 이 학생의 상황이
     * 실제로 바뀌면 해시가 달라져 자동으로 새 문장이 생성되고, 아무것도 안 바뀌었으면 며칠이 지나도 그대로
     * 캐시를 재사용한다 — 매 mutation 지점마다 캐시 무효화 코드를 심어둘 필요가 없다.
     * thinkingBudget:0으로 추론을 꺼둔 상태에서는 가끔 "테스트자녀1 학생"처럼 문장이 채 끝나지도 않고
     * 비정상적으로 짧게 끊기는 응답이 드물게 온다. 그대로 캐싱하면 한참 깨진 문장이 노출되므로,
     * 너무 짧은 응답은 무효로 보고 한 번 더 재시도한다.
     */
    private String getOrGenerateSummaryText(AcademyStudent academyStudent, String userMessage) {
        RBucket<String> bucket = redissonClient.getBucket(cacheKey(academyStudent, userMessage));
        String cached = bucket.get();
        if (cached != null) {
            return cached;
        }

        String generated = null;
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            generated = geminiClient.generate(systemInstruction(), userMessage).orElse(null);
            if (isValidSummary(generated)) {
                break;
            }
            log.warn("Gemini 응답이 비정상적으로 짧아 재시도합니다 (시도 {}/{}): {}", attempt, MAX_GENERATION_ATTEMPTS, generated);
        }

        if (!isValidSummary(generated)) {
            return null;
        }

        bucket.set(generated, CACHE_TTL);
        return generated;
    }

    private boolean isValidSummary(String text) {
        return text != null && text.trim().length() >= MIN_VALID_SUMMARY_LENGTH;
    }

    private String cacheKey(AcademyStudent academyStudent, String userMessage) {
        return CACHE_KEY_PREFIX + academyStudent.getId() + ":" + sha256(userMessage);
    }

    /** 캐시 키 discriminator일 뿐 보안 용도가 아니라, 충돌 위험이 낮은 선에서 짧게(16자) 잘라 쓴다. */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 표준으로 제공하므로 사실상 도달하지 않는다.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private String systemInstruction() {
        return "너는 학원 보강 관리 시스템의 학부모용 현황 안내 도우미야. 사용자가 보내는 JSON 수치만 근거로 삼아 "
                + "자녀의 그 학원 출결·보강권·수강 기간 현황을 학부모에게 2~3문장으로 안내해.\n\n"

                + "[기본 규칙]\n"
                + "- 어느 학원에 대한 내용인지 알 수 있게 학원 이름을 문장에 자연스럽게 포함해.\n"
                + "- JSON에 없는 숫자나 사실은 절대 새로 지어내지 마.\n"
                + "- 수치를 그대로 나열하지 말고 자연스러운 문장으로 풀어써.\n"
                + "- JSON에 없는(0건이라 생략된) 항목은 '0건입니다' 같은 식으로 언급하지 말고 아예 빼.\n"
                + "- '~가 필요합니다', '~해주세요', '연락 바랍니다' 같은, 학원 운영진에게 할 법한 조치·안내 문구는 "
                + "절대 쓰지 마 — 그건 학부모가 할 일이 아니라 학원 몫이야.\n"
                + "- 기본적으로 지금 상태를 담백하게 전달만 하고, 스스로 판단해서 권유를 지어내지 마.\n\n"

                + "[예외적으로 허용되는 권유 — 해당 키가 JSON에 있을 때만]\n"
                + "- '보강권만료임박'이 있으면: 보강권을 곧 써야 한다고 학부모 본인에게 직접 짧게 알려줘. "
                + "몇 개 중 몇 개가 임박했는지 숫자로 대조하지 말고, 그냥 '곧 만료되니 사용해 달라'는 식으로 자연스럽게만 써.\n"
                + "- '수강기간만료임박'이 있으면: '고려해보라' 같은 소극적 제안이 아니라 '정해진 기간 안에 재등록해 "
                + "달라'고 직접 부탁하는 어투로 알려주고, 기간 안에 재등록하지 않으면 그 자리가 신규 수강생에게 "
                + "우선 배정될 수 있다는 점을 겁주듯이 말고 자연스러운 안내처럼 한 마디 덧붙여.\n"
                + "- '수강기간만료후보강권사용가능'이 있으면: 수강 기간은 끝났지만 보강 신청 메뉴를 통해 남은 "
                + "보강권을 사용할 수 있다고 학부모 본인에게 직접 짧게 알려줘.\n"
                + "- 이 세 키가 JSON에 없으면 해당 권유는 절대 넣지 마.\n"
                + "- 수강 기간이 끝났어도 보강권은 그 자체 유효기간이 남아있으면 별개로 계속 쓸 수 있으니, 두 가지를 "
                + "섞어서 말하지 마.\n\n"

                + "[형식]\n"
                + "인사말이나 서두 없이 요약 내용만 바로 작성해.";
    }

    private String userMessage(String studentName, String academyName, long absenceCountThisMonth,
                                long availableTicketCount, long expiringSoonTicketCount, long usedTicketCount,
                                Long enrollmentDaysRemaining, boolean allowUseAfterEnrollmentExpired) {
        try {
            // 0건인 항목은 굳이 "0건입니다"라고 서술할 필요가 없으므로 아예 프롬프트에서 뺀다 —
            // 이렇게 하면 모델이 "언급하지 마"라는 지시를 따르지 않아도 애초에 언급할 재료가 없다.
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("학생이름", studentName);
            stats.put("학원이름", academyName);
            if (absenceCountThisMonth > 0) {
                stats.put("이번달결석횟수", absenceCountThisMonth);
            }
            stats.put("사용가능보강권", availableTicketCount);
            if (expiringSoonTicketCount > 0) {
                // 이 값이 있을 때만 "곧 쓰세요" 재촉 문구를 허용한다 — systemInstruction 참고.
                stats.put("보강권만료임박", expiringSoonTicketCount);
            }
            if (usedTicketCount > 0) {
                stats.put("사용완료보강권", usedTicketCount);
            }
            stats.put("수강기간남은일수", enrollmentDaysRemaining == null ? "제한없음" : enrollmentDaysRemaining);
            // 아직 만료 전(0~7일 남음)일 때만 "재등록 고려" 재촉 문구를 허용한다 — 이미 지난 경우는
            // 재등록 권유보다 그냥 사실 전달(며칠 지났다)이 맞아 넣지 않는다.
            if (enrollmentDaysRemaining != null && enrollmentDaysRemaining >= 0 && enrollmentDaysRemaining <= EXPIRING_SOON_DAYS) {
                stats.put("수강기간만료임박", true);
            }
            // 수강 기간은 끝났지만(음수) 보강권 자체 유효기간은 남아있어 여전히 쓸 수 있는 경우 —
            // 수강 기간 만료와 보강권 만료는 서로 다른 생명주기라 별도로 안내해야 한다. 단, 학원 정책이
            // 만료 후 사용을 막아뒀다면(allowUseAfterEnrollmentExpired=false) 실제로 못 쓰니 권유하지 않는다.
            if (allowUseAfterEnrollmentExpired && enrollmentDaysRemaining != null && enrollmentDaysRemaining < 0
                    && availableTicketCount > 0) {
                stats.put("수강기간만료후보강권사용가능", true);
            }
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            return "";
        }
    }
}
