plugins {
   java
   id("org.springframework.boot") version "3.5.14"
   id("io.spring.dependency-management") version "1.1.7"
}

group = "com.academy"
version = "0.0.1-SNAPSHOT"

java {
   toolchain {
      languageVersion = JavaLanguageVersion.of(21)
   }
}

repositories {
   mavenCentral()
}

dependencies {
   implementation("org.springframework.boot:spring-boot-starter-data-jpa")
   implementation("org.springframework.boot:spring-boot-starter-security")
   implementation("org.springframework.boot:spring-boot-starter-validation")
   implementation("org.springframework.boot:spring-boot-starter-web")
   implementation("org.springframework.boot:spring-boot-starter-mail:3.5.14")
   implementation("org.springframework.kafka:spring-kafka")

   // Observability — Redis 락 대기/실패, Kafka 컨슈머 랙 등을 Prometheus로 노출
   implementation("org.springframework.boot:spring-boot-starter-actuator")
   implementation("io.micrometer:micrometer-registry-prometheus")

   implementation("io.jsonwebtoken:jjwt-api:0.12.6")
   runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
   runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

   // QueryDSL (Jakarta 계열 — Spring Boot 3.x가 javax가 아닌 jakarta.persistence를 쓰므로 classifier 필수)
   implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
   annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
   annotationProcessor("jakarta.annotation:jakarta.annotation-api")
   annotationProcessor("jakarta.persistence:jakarta.persistence-api")

   // Redis 분산 락(보강 매칭 동시성 제어용)
   implementation("org.redisson:redisson:3.40.1")

   compileOnly("org.projectlombok:lombok")
   runtimeOnly("org.postgresql:postgresql")
   annotationProcessor("org.projectlombok:lombok")
   testImplementation("org.springframework.boot:spring-boot-starter-test")
   testImplementation("org.springframework.kafka:spring-kafka-test")

   // Testcontainers — 실제 Postgres/Redis/Kafka를 띄워 검증하는 통합테스트용
   // (버전은 Spring Boot의 dependency-management BOM이 맞춰 관리한다)
   testImplementation("org.testcontainers:junit-jupiter")
   testImplementation("org.testcontainers:postgresql")
   testImplementation("org.testcontainers:kafka")

   testCompileOnly("org.projectlombok:lombok")
   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
   testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
   useJUnitPlatform()
}

// QueryDSL이 애노테이션 프로세싱으로 생성하는 Q-클래스를 컴파일/IDE 소스 경로에 포함시킨다.
sourceSets {
   main {
      java {
         srcDirs += file("build/generated/sources/annotationProcessor/java/main")
      }
   }
}