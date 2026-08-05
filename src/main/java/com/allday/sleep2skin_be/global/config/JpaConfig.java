package com.allday.sleep2skin_be.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

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
@EnableJpaAuditing
public class JpaConfig {
}
