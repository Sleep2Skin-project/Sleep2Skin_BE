package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.infra.openai.OpenAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenAI HTTP 클라이언트 (architecture.md §2).
 *
 * <p><b>SDK 를 쓰지 않고 {@link RestClient} 로 직접 호출한다.</b> 우리가 보내는 것은 Responses API
 * 한 엔드포인트뿐이고, 대신 Structured Outputs 스키마를 우리 손으로 통제해야 한다 — 점수 방향이
 * 뒤집히면 아무 제약에도 안 걸리고 적중률만 무너지는 구조라 그 자리가 라이브러리 뒤에 숨으면
 * 안 된다. 의존성도 늘지 않는다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    /**
     * <b>타임아웃을 반드시 지정한다.</b> 기본값은 무제한이라, OpenAI 가 응답하지 않으면 톰캣
     * 워커 스레드가 영구히 묶인다. 셀피가 몰리는 시간대에 그러면 <b>수면 업로드까지 같이 막힌다</b> —
     * 스레드 풀이 하나이기 때문이다.
     */
    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties) {
        if (!properties.hasApiKey()) {
            // 앱을 못 뜨게 하지는 않는다. 키가 없는 팀원도 수면·예보 쪽을 개발할 수 있어야 한다.
            // 운영에서 이 로그가 보이면 EC2 app.env 에 키가 빠진 것이다 (workflow.md §7)
            log.warn("OPENAI_API_KEY 가 비어 있다 — 셀피 분석 API 만 502 로 실패한다");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

}
