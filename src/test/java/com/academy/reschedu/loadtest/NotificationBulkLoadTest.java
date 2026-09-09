package com.academy.reschedu.loadtest;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.MemberService;
import com.academy.reschedu.domain.member.dto.SignUpRequest;
import com.academy.reschedu.domain.member.dto.StudentManualRegisterRequest;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.domain.regularclass.dto.RegularClassCreateRequest;
import com.academy.reschedu.domain.regularclass.dto.TimeSlotRequest;
import com.academy.reschedu.global.security.jwt.JwtPrincipal;
import com.academy.reschedu.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * README의 "부하 테스트 실측 지표(휴무일 보강권 100건 동시 발급)" 수치를 실제로 재현하는 테스트.
 * 학부모 100명을 실제 JWT로 인증시켜 SSE 스트림에 붙여두고, 원장이 휴무일을 지정(보강권 자동
 * 발급 ON)하면 그 순간부터 각 학부모에게 실시간 알림이 도착하기까지 걸리는 시간과 API 자체의
 * 응답 속도를 측정한다.
 *
 * 도메인/통합 테스트와 달리 CI 기본 필터(domain.*, global.*, integration.*)에는 포함되지 않는다
 * (BCrypt 해싱 100회 + 동시 SSE 연결 100개라 매 푸시마다 돌리기엔 무겁다). 수치를 다시 재려면:
 *   ./gradlew test --tests "com.academy.reschedu.loadtest.*"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationBulkLoadTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> "Ddx5DscAye4hOThNHTFa/ypC6rXGM/x4lIjzZ6S8wzo=");
        registry.add("spring.mail.username", () -> "test@test.com");
        registry.add("spring.mail.password", () -> "test");
    }

    private static final int PARENT_COUNT = 100;
    private static final int CONNECT_WAIT_SECONDS = 20;
    private static final int EVENT_WAIT_SECONDS = 15;

    @LocalServerPort
    private int port;

    @Autowired
    private AcademyRepository academyRepository;
    @Autowired
    private MemberService memberService;
    @Autowired
    private RegularClassService regularClassService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long academyId;
    private String adminEmail;
    private LocalDate targetDate;
    private final List<String> parentPhones = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Academy academy = academyRepository.save(Academy.builder()
                .name("부하테스트학원-" + UUID.randomUUID())
                .address("테스트 주소")
                .build());
        academyId = academy.getId();

        adminEmail = "admin-" + UUID.randomUUID() + "@test.com";
        memberService.signUp(new SignUpRequest(
                adminEmail, "password123", "테스트원장", MemberRole.ADMIN, academyId, "010-" + randomPhoneSuffix(), null));
        authenticateAs(adminEmail, MemberRole.ADMIN);

        String teacherEmail = "teacher-" + UUID.randomUUID() + "@test.com";
        UUID teacherUuid = memberService.signUp(new SignUpRequest(
                teacherEmail, "password123", "테스트강사", MemberRole.TEACHER, academyId, "010-" + randomPhoneSuffix(), null));
        memberService.approveMember(teacherUuid);

        targetDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // 학생(=학부모 계정) 100명을 먼저 만들고, 그 UUID를 그대로 반 편성에 태워
        // "이 반 로스터 전원"이 휴무일 자동 발급 대상이 되도록 한다.
        List<UUID> studentUuids = new ArrayList<>();
        for (int i = 0; i < PARENT_COUNT; i++) {
            String parentPhone = "010-" + randomPhoneSuffix();
            UUID studentUuid = memberService.registerStudentManual(academyId, new StudentManualRegisterRequest(
                    "부하테스트학부모" + i, parentPhone, "password123",
                    "부하테스트학생" + i, LocalDate.of(2015, 1, 1), "MALE", null,
                    null, teacherUuid, "테스트초등학교", null, null, null, null,
                    LocalDate.now().minusDays(1), LocalDate.now().plusYears(1)));
            studentUuids.add(studentUuid);
            parentPhones.add(parentPhone);
        }

        regularClassService.createRegularClass(academyId, new RegularClassCreateRequest(
                "부하테스트반", teacherUuid, "999", PARENT_COUNT + 10,
                List.of(new TimeSlotRequest(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0))),
                studentUuids));

        SecurityContextHolder.clearContext();
    }

    @Test
    void 휴무일_지정시_100명에게_보강권_발급과_실시간_알림이_전달된다() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ExecutorService listenerPool = Executors.newFixedThreadPool(PARENT_COUNT);
        CountDownLatch connectedLatch = new CountDownLatch(PARENT_COUNT);

        List<Callable<Long>> listeners = new ArrayList<>();
        for (String phone : parentPhones) {
            String token = jwtTokenProvider.createAccessToken(phone, MemberRole.PARENT);
            listeners.add(() -> listenForTicketIssued(httpClient, token, connectedLatch));
        }

        List<Future<Long>> futures = new ArrayList<>();
        for (Callable<Long> listener : listeners) {
            futures.add(listenerPool.submit(listener));
        }

        // 100개 SSE 연결이 전부 "connected" 이벤트를 받을 때까지 대기 — 그래야 그 이후에 쏘는
        // 휴무일 등록 요청의 알림을 전부 놓치지 않고 받을 수 있다.
        boolean allConnected = connectedLatch.await(CONNECT_WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(allConnected).as("100개 SSE 연결이 제한 시간 안에 모두 연결됨").isTrue();

        String adminToken = jwtTokenProvider.createAccessToken(adminEmail, MemberRole.ADMIN);
        TestRestTemplate restTemplate = new TestRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "access_token=" + adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"date\":\"%s\",\"reason\":\"부하테스트\",\"issueMakeupTickets\":true}", targetDate);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        long apiStartNanos = System.nanoTime();
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/academy-holidays?academyId=" + academyId,
                HttpMethod.POST, entity, String.class);
        long apiEndNanos = System.nanoTime();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        double apiResponseMs = (apiEndNanos - apiStartNanos) / 1_000_000.0;

        int successCount = 0;
        long totalLatencyNanos = 0;
        for (Future<Long> future : futures) {
            try {
                Long receivedAtNanos = future.get(EVENT_WAIT_SECONDS, TimeUnit.SECONDS);
                if (receivedAtNanos != null) {
                    successCount++;
                    totalLatencyNanos += Math.max(0, receivedAtNanos - apiEndNanos);
                }
            } catch (Exception e) {
                // 타임아웃/연결 오류 — 실패로 집계
            }
        }
        listenerPool.shutdownNow();

        double successRate = successCount / (double) PARENT_COUNT;
        double avgLatencyMs = successCount == 0 ? -1 : (totalLatencyNanos / (double) successCount) / 1_000_000.0;

        System.out.println("========== 보강권 " + PARENT_COUNT + "건 동시 발급 부하테스트 실측 ==========");
        System.out.printf("API 응답 속도: %.1fms%n", apiResponseMs);
        System.out.printf("알림 수신 지연(API 응답 후 평균): %.1fms%n", avgLatencyMs);
        System.out.printf("전송 성공률: %.0f%% (%d/%d건)%n", successRate * 100, successCount, PARENT_COUNT);
        System.out.println("=================================================================");

        // 하드웨어마다 절대값은 달라질 수 있어 느슨한 기준으로만 회귀를 감시한다.
        assertThat(successRate).isGreaterThanOrEqualTo(0.9);
        assertThat(apiResponseMs).isLessThan(5000);
    }

    /** SSE 스트림에 붙어 "connected"를 받으면 latch를 풀고, 이후 이 회원 앞으로 온 MAKEUP_TICKET_ISSUED를 기다린다. */
    private Long listenForTicketIssued(HttpClient httpClient, String token, CountDownLatch connectedLatch) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/notifications/stream"))
                .header("Cookie", "access_token=" + token)
                .timeout(Duration.ofSeconds(CONNECT_WAIT_SECONDS + EVENT_WAIT_SECONDS + 5))
                .GET()
                .build();

        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                var it = lines.iterator();
                boolean countedDown = false;
                while (it.hasNext()) {
                    String line = it.next();
                    if (!countedDown && line.startsWith("event:connected")) {
                        connectedLatch.countDown();
                        countedDown = true;
                        continue;
                    }
                    if (line.startsWith("data:") && line.contains("MAKEUP_TICKET_ISSUED")) {
                        return System.nanoTime();
                    }
                }
            }
        } catch (Exception e) {
            // 인터럽트/타임아웃으로 인한 조기 종료 — null 반환으로 실패 처리
        }
        return null;
    }

    private void authenticateAs(String email, MemberRole role) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        JwtPrincipal principal = new JwtPrincipal(email, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private String randomPhoneSuffix() {
        return String.format("%04d-%04d", (int) (Math.random() * 10000), (int) (Math.random() * 10000));
    }
}
