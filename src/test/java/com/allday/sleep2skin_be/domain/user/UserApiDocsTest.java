package com.allday.sleep2skin_be.domain.user;

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
 * {@code user} 도메인의 API 문서 검증.
 *
 * <p>도메인과 무관한 규칙(실패 스키마·미디어 타입·에러 예시 생성)은
 * {@code SwaggerConfigTest}가 문서 전체를 순회하며 본다. 여기서는 <b>이 도메인만의 것</b>을 본다 —
 * 어떤 설명이 실렸는지, 어떤 에러 예시를 골랐는지.
 *
 * <p>도메인별로 파일을 나눈 이유는 나중에 도메인 단위로 작업을 나누기 위해서다(workflow.md §4).
 * 한 파일에 모아두면 두 사람이 계속 같은 파일을 건드린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiDocsTest {

    private static final String CONSENTS_POST = "$.paths.['/api/v1/users/me/consents'].post";
    private static final String ONBOARDING_PATCH = "$.paths.['/api/v1/users/me/onboarding'].patch";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 문서는 {@link UserControllerSpec} 인터페이스에 있다. springdoc이 인터페이스의 어노테이션을
     * 못 찾으면 <b>설명만 조용히 사라지고 API는 그대로 뜬다.</b> 컴파일도 테스트도 통과하므로
     * 프론트가 빈 문서를 보기 전까지 아무도 모른다.
     */
    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".summary").value(containsString("동의")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("멱등")))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].description")
                        .value(containsString("newlyAgreed")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".summary").value(containsString("온보딩")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("ONB-02")));
    }

    /**
     * {@code @Tag} 설명은 Swagger UI에서 태그를 접어도 계속 펼쳐진 채 목록 맨 위를 차지한다.
     * 공통 규약을 거기 넣으면 API 목록을 훑기가 불편해져, 태그는 한 줄로 두고 규약은 각 API 설명에
     * 되풀이해 적기로 했다.
     */
    @Test
    @DisplayName("공통 규약은 태그가 아니라 각 API 설명에 들어 있다")
    void 공통_규약이_API마다_적혀_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[?(@.name == 'User')].description")
                        .value(hasItem("사용자 · 동의 · 온보딩 API")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("USER_NOT_FOUND")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['400'].content.['application/json'].examples.USER_ID_HEADER_INVALID.['$ref']")
                        .value("#/components/examples/USER_ID_HEADER_INVALID"))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("@CurrentUserId를 쓰는 API에 X-User-Id 헤더가 붙는다")
    void 헤더_파라미터가_문서에_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].name").value("X-User-Id"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].in").value("header"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].required").value(true))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".parameters[0].name").value("X-User-Id"));
    }

    @Test
    @DisplayName("성공 응답은 각 API의 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseOnboardingCompleteResponse"));
    }

}
