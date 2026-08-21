# 1단계: Gradle 빌드 — 의존성 레이어와 소스 레이어를 분리해 소스만 바뀌었을 때 의존성 다운로드를 재사용한다.
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 전용 런타임 이미지 — JDK가 아닌 JRE만 담아 이미지 크기를 줄인다.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r reschedu && useradd -r -g reschedu reschedu
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar
RUN chown reschedu:reschedu app.jar
USER reschedu

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/localhost/8080' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
