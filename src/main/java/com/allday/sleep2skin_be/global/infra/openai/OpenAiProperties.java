package com.allday.sleep2skin_be.global.infra.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI 연동 설정 (architecture.md §7).
 *
 * <p><b>모델 ID를 코드에 박지 않는다.</b> 비용 문제로 {@code gpt-5.6-luna} 로 갈아탈 수 있어야
 * 하고, 그건 배포 설정 변경이지 코드 변경이 아니다.
 *
 * @param apiKey         {@code OPENAI_API_KEY}. <b>비어 있어도 앱은 뜬다</b> — 키가 없는 팀원도
 *                       수면·예보 쪽을 개발할 수 있어야 하기 때문이다. 대신 셀피 분석만
 *                       {@code SELFIE_ANALYSIS_FAILED} 로 떨어지고, 운영에서 이 상황이 생기지
 *                       않도록 <b>CD 가 배포 전에 {@code app.env} 를 선검사한다</b>
 *                       (workflow.md §7)
 * @param baseUrl        Responses API 호스트. 테스트에서 갈아끼울 수 있게 프로퍼티로 둔다
 * @param visionModel    셀피 분석 모델
 * @param timeout        읽기 타임아웃. 초과하면 {@code SELFIE_ANALYSIS_TIMEOUT}(504)
 * @param connectTimeout 연결 타임아웃. 읽기와 따로 두는 이유는 <b>연결 실패는 재시도가 의미
 *                       있고 분석 지연은 그렇지 않기</b> 때문이다 — 30초를 연결에도 주면
 *                       OpenAI 가 죽었을 때 앱이 30초를 기다린다
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(

        String apiKey,

        String baseUrl,

        String visionModel,

        Duration timeout,

        Duration connectTimeout
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

}
