package com.academy.reschedu.integration;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.makeup.MakeupRequestService;
import com.academy.reschedu.domain.makeup.MakeupTicketService;
import com.academy.reschedu.domain.makeup.dto.MakeupRequestCreateRequest;
import com.academy.reschedu.domain.makeup.dto.ManualTicketGrantRequest;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.MemberService;
import com.academy.reschedu.domain.member.dto.SignUpRequest;
import com.academy.reschedu.domain.member.dto.StudentManualRegisterRequest;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.domain.regularclass.dto.RegularClassCreateRequest;
import com.academy.reschedu.domain.regularclass.dto.TimeSlotRequest;
import com.academy.reschedu.global.security.jwt.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Postgres/Redis/Kafka를 Testcontainers로 띄워, "정원 4석에 10명이 동시에 보강 신청을
 * 몰아넣어도 정확히 4명만 성공한다"는 동시성 수정 사항을 자동화된 테스트로 증명한다.
 * (이 세션에서 curl로 수동 검증했던 시나리오를 그대로 재현한다.)
 */
@SpringBootTest
@Testcontainers
class MakeupRequestConcurrencyIntegrationTest {

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

    private static final int CAPACITY = 4;
    private static final int APPLICANT_COUNT = 10;

    @Autowired
    private AcademyRepository academyRepository;
    @Autowired
    private MemberService memberService;
    @Autowired
    private RegularClassService regularClassService;
    @Autowired
    private MakeupTicketService makeupTicketService;
    @Autowired
    private MakeupRequestService makeupRequestService;

    private Long academyId;
    private String adminEmail;
    private UUID targetClassUuid;
    private LocalDate targetDate;
    private final List<UUID> studentUuids = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        Academy academy = academyRepository.save(Academy.builder()
                .name("동시성테스트학원-" + UUID.randomUUID())
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

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        targetDate = nextMonday;

        List<UUID> classUuids = regularClassService.createRegularClass(academyId, new RegularClassCreateRequest(
                "동시성테스트반", teacherUuid, "999", CAPACITY,
                List.of(new TimeSlotRequest(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0))),
                List.of()));
        targetClassUuid = classUuids.get(0);

        for (int i = 0; i < APPLICANT_COUNT; i++) {
            UUID studentUuid = memberService.registerStudentManual(academyId, new StudentManualRegisterRequest(
                    "테스트학부모" + i, "010-" + randomPhoneSuffix(), "password123",
                    "테스트학생" + i, LocalDate.of(2015, 1, 1), "MALE", null,
                    null, null, "테스트초등학교", null, null, null, null,
                    LocalDate.now().minusDays(1), LocalDate.now().plusYears(1)));
            studentUuids.add(studentUuid);

            makeupTicketService.grantTicketsManually(academyId, new ManualTicketGrantRequest(
                    studentUuid, 1, "동시성테스트", 30, false));
        }
    }

    @Test
    void 정원_4석에_10명이_동시에_신청하면_정확히_4명만_성공한다() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(APPLICANT_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(APPLICANT_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(APPLICANT_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger capacityRejectedCount = new AtomicInteger();
        List<String> unexpectedFailures = new CopyOnWriteArrayList<>();

        for (UUID studentUuid : studentUuids) {
            executor.submit(() -> {
                authenticateAs(adminEmail, MemberRole.ADMIN);
                readyLatch.countDown();
                try {
                    startLatch.await();
                    makeupRequestService.createRequest(new MakeupRequestCreateRequest(
                            studentUuid, targetClassUuid, targetDate));
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("정원이 가득")) {
                        capacityRejectedCount.incrementAndGet();
                    } else {
                        unexpectedFailures.add(e.getMessage());
                    }
                } catch (Exception e) {
                    unexpectedFailures.add(e.toString());
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpectedFailures).isEmpty();
        assertThat(successCount.get()).isEqualTo(CAPACITY);
        assertThat(capacityRejectedCount.get()).isEqualTo(APPLICANT_COUNT - CAPACITY);
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
