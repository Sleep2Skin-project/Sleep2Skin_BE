package com.allday.sleep2skin_be.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 문서가 실제로 나가는 응답과 같은 모양인지 확인한다.
 *
 * <p><b>이 검증이 없으면 어긋나도 아무도 모른다.</b> 문서는 앱 팀만 보고 서버 테스트는 통과하므로,
 * 틀린 스키마는 앱이 그걸 보고 잘못 구현한 뒤에야 드러난다.
 *
 * <p>슬라이스가 아니라 {@code @SpringBootTest}인 이유는 springdoc이 전체 컨텍스트를 훑어
 * 문서를 만들기 때문이다 — 컨트롤러 하나만 띄우면 검증할 문서 자체가 나오지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerConfigTest {

    private static final String CONSENTS_POST = "$.paths.['/api/v1/users/me/consents'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("성공 스키마에는 error가 없고 실패 응답은 실패 전용 스키마를 가리킨다")
    void 문서의_응답_모양이_실제_응답과_같다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())

                // 성공 래퍼 — data는 있고 error는 없다. @JsonInclude(NON_NULL)로 실제로 나가지 않는다.
                .andExpect(jsonPath("$.components.schemas.ApiResponseConsentAgreeResponse.properties.data").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponseConsentAgreeResponse.properties.error").doesNotExist())

                // 실패 래퍼 — success와 error만 있다.
                .andExpect(jsonPath("$.components.schemas.ErrorApiResponse.properties.success").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorApiResponse.properties.error").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorApiResponse.properties.data").doesNotExist())

                // 4xx가 성공 스키마를 가리키면 앱이 없는 data를 기대하게 된다.
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['404'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ErrorApiResponse"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['400'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ErrorApiResponse"))

                // 성공 코드는 스키마를 건드리지 않는다. 미디어 타입은 성공·실패가 같아야 한다 —
                // springdoc 기본값(*/*)을 그대로 두면 성공만 다른 모양으로 문서화된다.
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath("$.paths.['/api/v1/health'].get.responses.['200'].content.['application/json']").exists());
    }

    @Test
    @DisplayName("@CurrentUserId를 쓰는 API에만 X-User-Id 헤더가 문서에 붙는다")
    void 헤더_파라미터가_문서에_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].name").value("X-User-Id"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].in").value("header"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].required").value(true))

                // 헬스체크는 사용자와 무관하므로 붙지 않는다. userId가 쿼리 파라미터로
                // 새어 나오지 않는지도 여기서 걸린다.
                //
                // 아래 doesNotExist는 경로 표현식이 틀려도 통과하므로, 경로가 실제로 잡히는지를
                // 먼저 확인한다. 이게 없으면 오타 하나로 검증이 조용히 무력해진다.
                .andExpect(jsonPath("$.paths.['/api/v1/health'].get.operationId").exists())
                .andExpect(jsonPath("$.paths.['/api/v1/health'].get.parameters").doesNotExist());
    }

}
