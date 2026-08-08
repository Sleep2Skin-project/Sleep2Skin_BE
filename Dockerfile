# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

# JVM 기본 타임존을 UTC 로 고정한다.
#
# 베이스 이미지가 이미 UTC 이지만, 로컬 개발 머신은 KST 라서 둘이 다르다.
# 코드에 LocalDate.now() 같은 것이 섞여 들어가면 로컬에서는 멀쩡하고
# EC2 에서만 하루 밀리는 — 그것도 한국 시간 오전 9시 이전에만 나는 —
# 재현 불가능한 버그가 된다.
#
# 그래서 IDE 실행 설정에도 -Duser.timezone=UTC 를 넣어 로컬을 여기에 맞춘다
# (docs/workflow.md §1). 값 자체보다 두 환경이 같다는 것이 요점이다.
ENV TZ=UTC

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
