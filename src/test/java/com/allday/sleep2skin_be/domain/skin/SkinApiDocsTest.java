package com.allday.sleep2skin_be.domain.skin;

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
 * {@code skin} 도메인의 API 문서 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkinApiDocsTest {

    private static final String FORECAST_GET = "$.paths.['/api/v1/skin/forecast'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".summary").value(containsString("피부 예보 조회")))
                .andExpect(jsonPath(FORECAST_GET + ".summary").value(containsString("HOME-03")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Skin')].description")
                        .value(hasItem("피부 예보 · 검증 · 개인 모델 API")));
    }

    /**
     * 빈 상태를 4xx로 오해하면 앱이 신규 사용자에게 에러 화면을 띄운다. <b>가장 흔한 오독</b>이라
     * 문서에서 사라지지 않게 붙들어 둔다.
     */
    @Test
    @DisplayName("빈 상태가 200이라는 것과 status로 분기하라는 안내가 문서에 있다")
    void 빈_상태_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("404가 아니다")))
                .andExpect(jsonPath(FORECAST_GET + ".responses.['200'].description")
                        .value(containsString("예보가 없는 경우도")));
    }

    /**
     * {@code DARK_CIRCLE}의 방향이 뒤집히면 값 범위는 정상이라 어떤 검증에도 안 걸리고
     * 화면 문구만 정반대로 나간다. 프론트가 읽는 유일한 방어선이 이 문서다.
     */
    @Test
    @DisplayName("지표 방향과 등급 구간이 문서에 있다")
    void 지표_방향이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("회복된 정도")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("높을수록 좋은 상태")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("STABLE")));
    }

    /**
     * <b>인덱스가 아니라 이름으로 찾는다.</b> springdoc이 매기는 파라미터 순서는 보장되지 않아
     * (여기서는 쿼리 파라미터가 헤더보다 앞에 온다) 인덱스로 단언하면 순서만 바뀌어도 깨진다.
     */
    @Test
    @DisplayName("X-User-Id 헤더와 baseDate 쿼리 파라미터가 둘 다 필수로 문서에 붙는다")
    void 기준일_파라미터가_문서에_붙는다() throws Exception {
        String userIdParameter = FORECAST_GET + ".parameters[?(@.name == 'X-User-Id')]";
        String baseDateParameter = FORECAST_GET + ".parameters[?(@.name == 'baseDate')]";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(userIdParameter + ".in").value(hasItem("header")))
                .andExpect(jsonPath(userIdParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(baseDateParameter + ".in").value(hasItem("query")))
                .andExpect(jsonPath(baseDateParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("하루 밀린다")));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(FORECAST_GET + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("성공 응답이 조회 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseSkinForecastQueryResponse"));
    }

}
