# ReschEdu

학원의 정규 수업 시간표와 보강(결석/휴무 대체 수업) 매칭을 관리하는 백엔드입니다.
원장·강사·학부모 세 역할이 한 시스템을 다르게 씁니다. 결석/휴무로 정규 수업을 못 들은 학생에게는
**보강권**(다른 반의 남는 자리에 대신 참석할 수 있는 티켓)이 발급되고, 이 보강권으로 여석을 신청·매칭하는 것이
이 시스템의 핵심 흐름입니다.

- 프론트엔드: https://github.com/hyo213/reschedu-front
- Spring Boot 3.5 / Java 21 · QueryDSL · Redis(Redisson) · Kafka · PostgreSQL · Prometheus/Grafana · Testcontainers

> 개발자가 로컬에서 실행 중일 때만 아래 ngrok 주소로 접속할 수 있습니다:
> **https://bronchial-exonerate-antiques.ngrok-free.dev/**
> (접속되지 않는다면 [실행 방법](#실행-방법)을 참고해 주세요)

## 주요 기능

| 기능 | 내용 |
|---|---|
| 정규 수업 | 요일·시간대별 반 편성. 같은 강사의 동일 요일·시간 반 있으면 합류, 없으면 신규 생성 |
| 학원 휴무 → 보강권 | 휴무일 지정 시 보강권 발급 여부 선택 가능(체크 시 지정일 학생 전원 자동 발급), 취소 시 미사용은 회수 + 이미 매칭된 미래 보강도 자동 취소·복원(과거 보강은 유지) |
| 보강 신청/매칭 | 보강권으로 여석 신청, 원장/강사는 직접 매칭 → [동시성](#redis-분산락-기반-보강-신청-동시성-제어) |
| 실시간 알림 | 가입 승인 대기 / 보강권 발급을 SSE로 즉시 push |
| AI 학부모 리포트 | 자녀 출결·보강권·수강 기간 현황을 Gemini가 자연어로 요약 → [설계](#ai-학부모-리포트) |

---

## Redis 분산락 기반 보강 신청 동시성 제어

보강 수업은 잔여석이 한정돼 있는데, 여러 학생이 동시에 신청하면 잔여석 계산의 일관성이 깨져 정원보다 많은
신청이 승인될 수 있습니다. 그래서 `makeup-slot:{반UUID}:{날짜}` 키 단위로 Redisson 분산락을 걸고,
대기(PENDING) 상태도 정원 카운트에 즉시 반영해 초과 신청을 차단했습니다.

락을 언제 푸느냐도 문제였습니다. 트랜잭션이 DB에 최종 커밋되기 전에 락이 해제되면 다른 스레드가 변경 전
데이터를 읽습니다. 트랜잭션 종료 시점이 아니라 DB 커밋이 끝난 뒤 락을 해제하도록(`releaseLockAfterCommit`)
로직을 옮겼습니다.

```java
private void acquireSlotLock(UUID regularClassUuid, LocalDate targetDate) {
    RLock lock = redissonClient.getLock("makeup-slot:" + regularClassUuid + ":" + targetDate);
    lock.tryLock(SLOT_LOCK_WAIT_SECONDS, SLOT_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
    // ... 비즈니스 로직 및 잔여석 검증
    releaseLockAfterCommit(lock); // 커밋 완료 시점에 락 해제 (정합성 보장)
}
```

### 동시성 검증 테스트 (정원 4석에 10명 동시 신청)

10개 스레드가 동시에 요청하면 정확히 정원 수인 **4건만 성공**하고 나머지 6건은 초과로 실패합니다.
Testcontainers(Postgres/Redis/Kafka) 기반 통합 테스트(`MakeupRequestConcurrencyIntegrationTest`)라
CI 파이프라인에서 매 푸시마다 자동으로 돌아갑니다.

---

## Kafka — 인증 메일 / 실시간 알림

학원 휴무일을 지정하면 그날 수강생 전원에게 보강권이 한꺼번에 발급됩니다. 이 대량 일괄 발급을 동기로
처리하면 API가 지연되고 알림이 유실될 위험이 있어 Kafka로 뺐습니다.

인증 메일은 SMTP를 동기 호출하는 만큼 API가 늦어지고, DB가 롤백돼도 이미 나간 메일 때문에 유효하지 않은
코드가 발송됩니다. 응답 경로에서 SMTP 발송을 비동기로 분리하고 `@TransactionalEventListener(AFTER_COMMIT)`을
적용해 커밋이 끝난 뒤에만 Kafka 이벤트를 발행합니다.

SSE 알림은 다중 인스턴스 환경이 걸림돌이었습니다. 기본 `groupId`를 쓰면 파티션이 나뉘어 특정 서버에 붙은
접속자에게만 알림이 갑니다. 인스턴스별로 고유 `groupId`(`reschedu-notification-{uuid}`)를 동적으로 부여해
모든 노드가 이벤트를 수신하는 fan-out 구조로 바꿨고, 그래서 접속자 전원에게 브로드캐스팅됩니다.

### 부하 테스트 실측 지표 (휴무일 보강권 100건 동시 발급)

`NotificationBulkLoadTest`(Testcontainers 기반, 실제 SSE 연결 100개 + 실제 HTTP 요청)로 직접 측정했습니다.

- **API 응답 속도** `385.7ms` (티켓/세션-학생 INSERT를 `JdbcTemplate` 배치로 처리해 100건 개별 INSERT 대비 단축)
- **알림 수신 지연** API 응답 후 평균 `48.5ms` 내 실시간 전달
- **전송 성공률** **100% (100/100건)**

---

## CORS 와일드카드와 자격증명 취약점 해결

로컬 터널(ngrok)로 테스트하는 환경이라 CORS 설정에서 생길 수 있는 보안 취약점을 미리 차단해야 했습니다.

먼저 Origin 검증. 브라우저는 요청에 `Origin` 헤더를 자동으로 붙이는데(`curl`은 안 붙임), 서버가 이 헤더를
허용 목록과 대조해 403으로 차단했습니다. 환경 변수로 테스트용 Origin을 유연하게 등록할 수 있도록 구조를 바꿨습니다.

```java
@Value("${app.cors.extra-allowed-origins:}")
private String extraAllowedOrigins; // 환경 변수로 정확한 Origin만 콤마(,) 구분 주입
```

그 다음은 자격증명 탈취 위험입니다. 와일드카드(`*.ngrok-free.dev`)를 허용하면 `allowCredentials(true)`
환경에서 공격자 도메인까지 신뢰되어 쿠키가 노출됩니다. 와일드카드는 전면 배제하고 **검증된 정확한 Origin만
명시적 화이트리스트로 관리**합니다. `curl` 통과가 브라우저 보안 정책 통과를 보장하지 않고,
`allowCredentials(true)`를 쓰는 한 와일드카드(`*`) Origin은 엄격히 제한해야 한다는 걸 여기서 배웠습니다.

---

## AI 학부모 리포트

학부모 대시보드에 자녀의 출결(결석)·보강권·수강 기간 현황을 Gemini API로 2~3문장 요약해 보여줍니다.
캐시 무효화 코드 없이 항상 최신 상태를 유지하는 게 핵심입니다.
LLM 호출은 비용·지연이 커서 캐싱이 필요했습니다. 처음엔 "학생당 하루 1번"처럼 시간 기준으로
캐싱해봤는데, 그 사이 결석 신청·보강권 사용처럼 실제 상황이 바뀌어도 캐시된 옛 문장이 그대로 남아
반영이 안 되는 문제가 있었습니다.

캐시 키를 **날짜 대신 Gemini에 보내는 입력 데이터의 SHA-256 해시**로 바꿔 해결했습니다. 학생 상황이
실제로 바뀌면 해시가 자동으로 달라져 새 문장이 생성되고, 안 바뀌었으면 캐시를 그대로 재사용합니다.
결석/보강권 변동, 재등록 등 캐시를 무효화해야 할 모든 지점에 무효화 코드를 심을 필요가 없습니다.
숫자(출결/보강권/D-day)는 캐시하지 않고 매번 DB에서 실시간 계산해, 문장만 캐시로 인해 방금 한
행동이 반영 안 된 것처럼 보이는 상황도 없앴습니다.

```java
private String cacheKey(AcademyStudent academyStudent, String userMessage) {
    return CACHE_KEY_PREFIX + academyStudent.getId() + ":" + sha256(userMessage);
}
```

추론을 끈(`thinkingBudget: 0`) 상태에서는 Gemini가 드물게 문장이 채 끝나기도 전에 비정상적으로 짧게
끊긴 응답(예: "OO 학생"에서 종료)을 낼 때가 있습니다. 응답 길이가 임계치 미만이면 자동으로 한 번 더
재시도하고, 그래도 실패하면 AI 문장 없이 숫자만 보여주는 graceful degradation으로 처리했습니다 —
LLM 호출 실패가 화면 전체를 깨뜨리지 않도록 한 겹 방어선을 둔 셈입니다.

---

## Tech Stack

| 영역 | 스택 |
|---|---|
| API | Spring Boot 3.5, Java 21, Spring Security(JWT httpOnly 쿠키) |
| 조회 | Spring Data JPA + QueryDSL |
| 동시성 | Redis(Redisson 분산락) |
| 비동기 | Kafka(인증 메일, SSE 알림) |
| AI | Google Gemini API (`gemini-3.6-flash`) — 입력 해시 기반 캐싱으로 호출 최소화 |
| DB | PostgreSQL 16 |
| 관측 | Micrometer + Prometheus + Grafana (락 대기/실패, Kafka 컨슈머 랙만 선택 계측) |
| 테스트 | JUnit5 + Mockito + Testcontainers |
| 인프라 | Docker Compose, GitHub Actions |

## Getting Started

### 실행 전 필요한 환경 구성

- JDK 21
- Docker / Docker Compose
- PostgreSQL 16 (아래 두 실행 방법 모두 Redis/Kafka와 달리 컨테이너로 띄우지 않으므로, 로컬에 직접
  설치하고 `reschedu` 데이터베이스를 미리 만들어둬야 합니다 — `docker-compose.yml`도 앱 컨테이너에서
  `host.docker.internal`로 호스트의 Postgres에 접속하도록 되어 있습니다)

### 실행 방법

| 변수 | 필수 | 설명 |
|---|---|---|
| `RESCHEDU_DB_USER` / `RESCHEDU_DB_PASS` | ✅ | PostgreSQL 계정 |
| `JWT_SECRET` | ✅ | Base64 인코딩 HMAC 키 |
| `RESCHEDU_MAIL_USERNAME` / `RESCHEDU_MAIL_PASSWORD` | 선택 | 없으면 이메일 인증만 비활성 |
| `APP_CORS_EXTRA_ALLOWED_ORIGINS` | 선택 | 외부 테스트용 정확한 오리진(콤마 구분) |
| `GEMINI_API_KEY` | 선택 | 없으면 AI 리포트가 문장 없이 숫자만 표시 |

```bash
docker compose up -d redis kafka prometheus grafana   # Postgres는 로컬에 이미 떠 있어야 함
./gradlew bootRun
```

전부 컨테이너로 (앱까지 포함 — Postgres만 여전히 로컬):

```bash
cp .env.example .env
docker compose --profile full up -d --build
```

→ API `localhost:8080` · Grafana `localhost:3001`(admin/admin) · Prometheus `localhost:9090`

## 테스트

```bash
./gradlew test --tests "com.academy.reschedu.domain.*" --tests "com.academy.reschedu.global.*" --tests "com.academy.reschedu.integration.*"
```

서비스 11개 클래스 전부 단위 테스트. `MakeupRequestConcurrencyIntegrationTest`는 Testcontainers
통합 테스트. GitHub Actions에서 push/PR마다 동일 실행(`RescheduApplicationTests`는 Postgres
실환경 의존이라 로컬 전용 제외).
