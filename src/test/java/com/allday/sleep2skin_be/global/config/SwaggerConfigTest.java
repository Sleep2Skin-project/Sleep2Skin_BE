package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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

    /**
     * 문서를 {@code *ControllerSpec} 인터페이스에 두는 구조가 실제로 동작하는지 본다.
     *
     * <p>springdoc이 인터페이스의 어노테이션을 못 찾으면 <b>설명만 조용히 사라지고 API는 그대로
     * 뜬다.</b> 컴파일도 테스트도 통과하므로 프론트가 빈 문서를 보기 전까지 아무도 모른다.
     */
    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".summary").value(containsString("동의")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("멱등")))
                .andExpect(jsonPath("$.paths.['/api/v1/users/me/onboarding'].patch.summary")
                        .value(containsString("온보딩")))
                .andExpect(jsonPath("$.paths.['/api/v1/users/me/onboarding'].patch.description")
                        .value(containsString("ONB-02")))

                // 상태 코드별 설명도 인터페이스에서 온다.
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].description")
                        .value(containsString("newlyAgreed")));
    }

    /**
     * {@code @Tag} 설명은 Swagger UI에서 태그를 접어도 계속 펼쳐진 채 목록 맨 위를 차지한다.
     * 공통 규약을 거기 넣으면 API 목록을 훑기가 불편해져, 태그는 한 줄로 두고 규약은 각 API 설명에
     * 되풀이해 적기로 했다.
     */
    @Test
    @DisplayName("공통 규약은 태그가 아니라 각 API 설명에 들어 있다")
    void 공통_규약이_API마다_적혀_있다() throws Exception {
        String onboardingPatch = "$.paths.['/api/v1/users/me/onboarding'].patch";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())

                // 태그는 목록에서 한 줄로만 보인다.
                .andExpect(jsonPath("$.tags[?(@.name == 'User')].description")
                        .value(hasItem("사용자 · 동의 · 온보딩 API")))

                // 규약은 API를 펼치면 그 안에 다 있다.
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("USER_NOT_FOUND")))
                .andExpect(jsonPath(onboardingPatch + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(onboardingPatch + ".description").value(containsString("USER_NOT_FOUND")));
    }

    /**
     * 예시 문구를 손으로 적으면 {@link ErrorCode}가 바뀌어도 문서만 조용히 틀린 채 남는다.
     * 실제로 그런 적이 있어서 생성으로 바꿨고, 그 생성이 계속 유지되는지를 여기서 본다.
     */
    @Test
    @DisplayName("에러 예시는 ErrorCode에서 생성되어 상황별로 다르게 붙는다")
    void 에러_예시가_상황별로_다르다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())

                // 예시의 메시지는 enum에서 나온다 — 손으로 적은 문구와 대조하지 않는다.
                .andExpect(jsonPath("$.components.examples.USER_NOT_FOUND.value.error.code")
                        .value(ErrorCode.USER_NOT_FOUND.name()))
                .andExpect(jsonPath("$.components.examples.USER_NOT_FOUND.value.error.message")
                        .value(ErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.components.examples.USER_NOT_FOUND.value.success").value(false))
                .andExpect(jsonPath("$.components.examples.USER_NOT_FOUND.value.data").doesNotExist())

                // ErrorCode를 추가하면 예시도 저절로 생긴다.
                .andExpect(jsonPath("$.components.examples.length()").value(ErrorCode.values().length))

                // 상태 코드마다 다른 예시가 붙는다.
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['400'].content.['application/json'].examples.USER_ID_HEADER_INVALID.['$ref']")
                        .value("#/components/examples/USER_ID_HEADER_INVALID"))

                // 예시를 붙여도 스키마는 실패 래퍼여야 한다 — 커스터마이저가 둘 다 챙기는지 본다.
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['404'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ErrorApiResponse"));
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
