# ReschEdu

학원의 정규 수업 시간표와 보강(결석/휴무 대체 수업) 매칭을 관리하는 백엔드입니다.
원장·강사·학부모 세 역할이 한 시스템을 다르게 씁니다. 결석/휴무로 정규 수업을 못 들은 학생에게는
**보강권**(다른 반의 남는 자리에 대신 참석할 수 있는 티켓)이 발급되고, 이 보강권으로 여석을 신청·매칭하는 것이
이 시스템의 핵심 흐름입니다.

- 🔗 프론트엔드: https://github.com/hyo213/reschedu-front
- 🧱 Spring Boot 3.5 / Java 21 · QueryDSL · Redis(Redisson) · Kafka · PostgreSQL · Prometheus/Grafana · Testcontainers

> 개발자가 로컬에서 실행 중일 때만 아래 ngrok 주소로 접속할 수 있습니다:
> **https://bronchial-exonerate-antiques.ngrok-free.dev/**
> (접속되지 않는다면 [실행 방법](#-실행-방법)을 참고해주세요.)

## ✨ 주요 기능

| 기능 | 내용 |
|---|---|
| 정규 수업 | 요일·시간대별 반 편성. 같은 강사의 동일 요일·시간 반 있으면 합류, 없으면 신규 생성 |
| 학원 휴무 → 보강권 | 휴무일 지정 시 보강권 발급 여부 선택 가능(체크 시 지정일 학생 전원 자동 발급), 취소 시 미사용은 회수 + 이미 매칭된 미래 보강도 자동 취소·복원(과거 보강은 유지) |
| 보강 신청/매칭 | 보강권으로 여석 신청, 원장/강사는 직접 매칭 → [동시성](#-redis-분산락-기반-보강-신청-동시성-제어) |
| 실시간 알림 | 가입 승인 대기 / 보강권 발급을 SSE로 즉시 push |

---

## 🔒 Redis 분산락 기반 보강 신청 동시성 제어

> **도입 배경:** 잔여석이 한정된 보강 수업에 여러 학생이 동시에 신청할 경우, 정원 초과(Overbooking)가 발생하는 동시성 문제 방지

| 구분 | 기술적 과제 (Challenge) | 해결 전략 (Architecture) |
| :--- | :--- | :--- |
| **정원 초과 방지** | 동시 요청 시 잔여석 계산의 일관성이 깨져 정원보다 많은 신청이 승인될 위험 | • `makeup-slot:{반UUID}:{날짜}` 키 단위로 Redisson 분산락 적용<br>• 대기(PENDING) 상태도 정원 카운트에 즉시 반영해 초과 신청 차단 |
| **데이터 정합성 보장** | 트랜잭션이 DB에 최종 커밋되기 전 락이 해제되면, 다른 스레드가 변경 전 데이터를 읽는 문제 | • 트랜잭션 종료 시점이 아닌 **DB 커밋 완료 후 락 해제(`releaseLockAfterCommit`)** 로직 적용 |

### 💻 핵심 구현 로직

```java
private void acquireSlotLock(UUID regularClassUuid, LocalDate targetDate) {
    RLock lock = redissonClient.getLock("makeup-slot:" + regularClassUuid + ":" + targetDate);
    lock.tryLock(SLOT_LOCK_WAIT_SECONDS, SLOT_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
    // ... 비즈니스 로직 및 잔여석 검증
    releaseLockAfterCommit(lock); // 커밋 완료 시점에 락 해제 (정합성 보장)
}
```

### 🧪 동시성 검증 테스트 (정원 4석에 10명 동시 신청 시나리오)

- 🎯 **검증 결과:** 10개 스레드 동시 요청 시 **정확히 정원 수(4건)만 성공**, 나머지 6건 초과 실패 처리
- 🔄 **CI 연동:** Testcontainers(Postgres/Redis/Kafka) 기반 통합 테스트(`MakeupRequestConcurrencyIntegrationTest`)를 CI 파이프라인에 구축하여 매 푸시마다 자동 검증

---

## 📨 Kafka — 인증 메일 / 실시간 알림

> **도입 배경:** 학원 휴무일 지정 시 전체 수강생 대상 보강권 대량 일괄 발급으로 인한 API 지연 및 알림 유실 위험

| 구분 | 기술적 과제 (Challenge) | 해결 전략 (Architecture) |
| :--- | :--- | :--- |
| **인증 메일** | SMTP 동기 호출로 인한 API 지연 및 DB 롤백 시 유효하지 않은 코드 발송 위험 | • 응답 경로에서 SMTP 발송 비동기 분리<br>• `@TransactionalEventListener(AFTER_COMMIT)` 적용으로 커밋 완료 시에만 Kafka 이벤트 발행 |
| **실시간 알림 (SSE)** | 다중 인스턴스 환경에서 기본 `groupId` 사용 시 파티션 분할로 특정 서버 접속자에게만 알림 전송 | • 인스턴스별 고유 `groupId`(`reschedu-notification-{uuid}`) 동적 부여<br>• 모든 노드가 이벤트를 수신(Fan-out)하여 전 접속자 브로드캐스팅 보장 |

### 📊 부하 테스트 실측 지표 (휴무일 보강권 100건 동시 발급)

- ⏱️ **API 응답 속도:** `~500ms` 유지 (메인 트랜잭션 블로킹 완전 해소)
- ⚡ **알림 수신 지연:** API 응답 후 평균 `~100ms` 내 실시간 전달
- 🎯 **전송 성공률:** **99% (99/100건)** 도달 검증

---

## 🛡️ CORS 와일드카드 및 자격증명(Credentials) 취약점 해결

> **도입 배경:** 로컬 터널(ngrok) 테스트 환경에서 CORS 설정 시 발생할 수 있는 보안 취약점 사전 차단

| 구분 | 기술적 과제 (Challenge) | 해결 전략 (Architecture) |
| :--- | :--- | :--- |
| **Origin 검증 실패** | 브라우저는 요청에 `Origin` 헤더를 자동으로 붙이는데(`curl`은 안 붙임), 서버가 이 헤더를 허용 목록과 대조해 403 차단 | • 환경 변수를 통해 테스트용 Origin을 유연하게 등록할 수 있도록 구조 개선 |
| **자격증명 탈취 위험** | 와일드카드(`*.ngrok-free.dev`) 허용 시, `allowCredentials(true)` 환경에서 공격자 도메인까지 신뢰되어 쿠키 노출 위험 | • 와일드카드를 전면 배제하고 **검증된 정확한 Origin만 명시적 화이트리스트로 관리** |

### 💻 핵심 구현 로직

```java
@Value("${app.cors.extra-allowed-origins:}")
private String extraAllowedOrigins; // 환경 변수로 정확한 Origin만 콤마(,) 구분 주입
```

### 💡 핵심 인사이트

- `curl` 통과가 브라우저 보안 정책 통과를 보장하지 않음
- `allowCredentials(true)` 사용 시 와일드카드(`*`) Origin 설정은 공격자 도메인까지 신뢰할 수 있으므로 엄격히 제한해야 함

---

## 🛠 Tech Stack

| 영역 | 스택 |
|---|---|
| API | Spring Boot 3.5, Java 21, Spring Security(JWT httpOnly 쿠키) |
| 조회 | Spring Data JPA + QueryDSL |
| 동시성 | Redis(Redisson 분산락) |
| 비동기 | Kafka(인증 메일, SSE 알림) |
| DB | PostgreSQL 16 |
| 관측 | Micrometer + Prometheus + Grafana (락 대기/실패, Kafka 컨슈머 랙만 선택 계측) |
| 테스트 | JUnit5 + Mockito + Testcontainers |
| 인프라 | Docker Compose, GitHub Actions |

## ✈️ Getting Started

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

```bash
docker compose up -d redis kafka prometheus grafana   # Postgres는 로컬에 이미 떠 있어야 함
./gradlew bootRun
```

전부 컨테이너로 (앱까지 포함 — Postgres만 여전히 로컬):

```bash
cp .env.example .env
docker compose up -d --build
```

→ API `localhost:8080` · Grafana `localhost:3001`(admin/admin) · Prometheus `localhost:9090`

## ✅ 테스트

```bash
./gradlew test --tests "com.academy.reschedu.domain.*" --tests "com.academy.reschedu.integration.*"
```

서비스 11개 클래스 전부 단위 테스트. `MakeupRequestConcurrencyIntegrationTest`는 Testcontainers
통합 테스트. GitHub Actions에서 push/PR마다 동일 실행(`RescheduApplicationTests`는 Postgres
실환경 의존이라 로컬 전용 제외).
