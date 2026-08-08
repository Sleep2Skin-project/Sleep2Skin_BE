package com.allday.sleep2skin_be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * JPA 설정.
 *
 * <p>Auditing을 켜서 {@code BaseTimeEntity}·{@code BaseCreatedEntity}의
 * 생성·수정 시각이 자동으로 채워지게 한다.
 *
 * <p>{@code @EnableJpaAuditing}을 메인 애플리케이션 클래스에 붙이지 않는다.
 * 거기 붙이면 {@code @WebMvcTest} 같은 슬라이스 테스트가 JPA 컨텍스트를 요구하게 된다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaConfig {

    /**
     * Auditing 시각을 {@link OffsetDateTime}(UTC)으로 공급한다.
     *
     * <p><b>기본 provider를 쓰면 저장이 실패한다.</b> Spring Data의 기본값은
     * {@code LocalDateTime}을 주는데 {@code BaseTimeEntity}의 필드는 {@link OffsetDateTime}이고,
     * 둘 사이의 변환은 오프셋이 정해지지 않아 지원되지 않는다.
     *
     * <p>필드 타입이 아니라 이쪽을 맞춘 이유는 erd.md §3.1이 <b>도메인 시각 컬럼을
     * {@code OffsetDateTime}으로 쓰기로 확정했기 때문</b>이다 — {@code LocalDateTime}으로 받으면
     * 요청의 오프셋이 컨트롤러 단계에서 버려져 {@code NORMALIZE_UTC} 설정이 개입할 여지가 없다.
     *
     * <p>UTC로 고정한 것도 같은 문서의 결정을 따른 것이다. 시각 저장 기준은 JDBC URL의
     * {@code serverTimezone} · Hibernate 저장 정책 · JVM 타임존이 함께 정하며, 여기서 JVM 기본
     * 타임존을 타면 로컬(KST)과 운영(UTC)의 {@code created_at}이 서로 다른 기준을 갖게 된다.
     */
    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

}
