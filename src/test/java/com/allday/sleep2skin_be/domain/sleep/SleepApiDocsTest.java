package com.allday.sleep2skin_be.domain.sleep;

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
 * {@code sleep} 도메인의 API 문서 검증.
 *
 * <p>도메인 무관 규칙(실패 스키마·미디어 타입·에러 예시 생성)은 {@code SwaggerConfigTest}가 문서
 * 전체를 순회하며 본다. 여기서는 이 도메인만의 것을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SleepApiDocsTest {

    private static final String SESSIONS_POST = "$.paths.['/api/v1/sleep/sessions'].post";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 문서는 {@link SleepControllerSpec} 인터페이스에 있다. springdoc이 인터페이스의 어노테이션을
     * 못 찾으면 <b>설명만 조용히 사라지고 API는 그대로 뜬다.</b>
     */
    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".summary").value(containsString("수면 세션 업로드")))
                .andExpect(jsonPath(SESSIONS_POST + ".summary").value(containsString("HOME-03")))
                .andExpect(jsonPath(SESSIONS_POST + ".description")
                        .value(containsString("검증을 마친 날의 예보는 절대 바뀌지 않는다")))
                .andExpect(jsonPath(SESSIONS_POST + ".responses.['201'].description")
                        .value(containsString("첫 수신")));
    }

    @Test
    @DisplayName("공통 규약은 태그가 아니라 API 설명에 들어 있다")
    void 공통_규약이_API에_적혀_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[?(@.name == 'Sleep')].description")
                        .value(hasItem("수면 수집 · 해석 API")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("USER_NOT_FOUND")));
    }

    /**
     * <b>앱이 이 세 가지를 어기면 값 범위는 정상인 채로 결과만 틀린다.</b> 문서가 유일한 방어선이라
     * 사라지지 않게 테스트로 붙들어 둔다.
     */
    @Test
    @DisplayName("앱이 지켜야 할 페이로드 규칙이 문서에 남아 있다")
    void 페이로드_규칙이_문서에_남아_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("UNSPECIFIED")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("오프셋")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("inBed")));
    }

    /**
     * 재수신 4분기를 앱이 모르면 {@code processed: false}를 실패로 오해해 재시도 루프에 빠진다.
     */
    @Test
    @DisplayName("재수신 4분기와 빈 상태 사유가 문서에 있다")
    void 재수신과_빈_상태가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("processed")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("MISSING_FEATURES")))
                .andExpect(jsonPath(SESSIONS_POST + ".description").value(containsString("NO_SLEEP_STAGES")));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(SESSIONS_POST + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 붙고 baseDate 파라미터는 없다")
    void 헤더만_있고_기준일_파라미터는_없다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".parameters[0].name").value("X-User-Id"))
                .andExpect(jsonPath(SESSIONS_POST + ".parameters[0].in").value("header"))
                .andExpect(jsonPath(SESSIONS_POST + ".parameters[0].required").value(true))
                // 이 API만 기준일을 서버가 정한다 — 파라미터가 생겼다면 규약을 잘못 옮긴 것이다
                .andExpect(jsonPath(SESSIONS_POST + ".parameters[1]").doesNotExist());
    }

    @Test
    @DisplayName("성공 응답이 업로드 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SESSIONS_POST + ".responses.['201'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseSleepSessionUploadResponse"))
                .andExpect(jsonPath(SESSIONS_POST + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseSleepSessionUploadResponse"));
    }

}
