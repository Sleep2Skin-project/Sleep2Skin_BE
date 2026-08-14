package com.allday.sleep2skin_be.domain.report;

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
 * {@code report} 도메인의 API 문서 검증.
 *
 * <p>도메인 무관 규칙(실패 스키마·미디어 타입·에러 예시 생성)은 {@code SwaggerConfigTest}가 문서
 * 전체를 순회하며 본다. 여기서는 이 도메인만의 것을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiDocsTest {

    private static final String DAILY_GET = "$.paths.['/api/v1/report/daily'].get";
    private static final String TIMELINE_GET = "$.paths.['/api/v1/report/daily/timeline'].get";
    private static final String WEEKLY_GET = "$.paths.['/api/v1/report/weekly'].get";
    private static final String MONTHLY_GET = "$.paths.['/api/v1/report/monthly'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DAILY_GET + ".summary").value(containsString("일간 리포트 조회")))
                .andExpect(jsonPath(DAILY_GET + ".summary").value(containsString("REP-02")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Report')].description")
                        .value(hasItem(containsString("리포트 API"))));
    }

    /**
     * <b>두 섹션이 각자 빈 상태일 수 있다는 것이 이 API의 핵심 계약이다.</b> 문서에서 사라지면
     * 프론트가 응답 전체를 하나의 상태로 오해해 있는 데이터까지 숨기는 화면을 만들 수 있다.
     */
    @Test
    @DisplayName("두 섹션이 독립적으로 빈 상태일 수 있다는 것이 문서에 있다")
    void 섹션별_빈_상태_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DAILY_GET + ".description")
                        .value(containsString("응답 전체를 하나의 상태로 감싸지 않는다")))
                .andExpect(jsonPath(DAILY_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(DAILY_GET + ".responses.['200'].description")
                        .value(containsString("두 섹션이 각자 빈 상태일 수 있고")));
    }

    /**
     * {@code sleepScore}가 예보 점수(가중평균)와 다른 단순평균이라는 것이 빠지면 REP-04와
     * HOME-03의 숫자가 다른 이유를 프론트가 알 방법이 없다.
     */
    @Test
    @DisplayName("sleepScore가 예보 점수와 다른 계산이라는 것이 문서에 있다")
    void 수면_점수_계산_근거가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DAILY_GET + ".description")
                        .value(containsString("예보 점수(HOME-03)와 다른 계산이다")))
                .andExpect(jsonPath(DAILY_GET + ".description").value(containsString("§10.8")))
                .andExpect(jsonPath(DAILY_GET + ".description")
                        .value(containsString("lightSleepMinutes")));
    }

    @Test
    @DisplayName("X-User-Id 헤더와 baseDate 쿼리 파라미터가 둘 다 필수로 문서에 붙는다")
    void 기준일_파라미터가_문서에_붙는다() throws Exception {
        String userIdParameter = DAILY_GET + ".parameters[?(@.name == 'X-User-Id')]";
        String baseDateParameter = DAILY_GET + ".parameters[?(@.name == 'baseDate')]";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(userIdParameter + ".in").value(hasItem("header")))
                .andExpect(jsonPath(userIdParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(baseDateParameter + ".in").value(hasItem("query")))
                .andExpect(jsonPath(baseDateParameter + ".required").value(hasItem(true)));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DAILY_GET + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(DAILY_GET + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("성공 응답이 각 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DAILY_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseDailyReportResponse"))
                .andExpect(jsonPath(TIMELINE_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseDailyTimelineResponse"));
    }

    // ===== 타임라인 (REP-03) =====

    @Test
    @DisplayName("타임라인 문서가 반영되고 정렬·집계 규칙이 남아 있다")
    void 타임라인_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TIMELINE_GET + ".summary").value(containsString("타임라인")))
                .andExpect(jsonPath(TIMELINE_GET + ".summary").value(containsString("REP-03")))
                .andExpect(jsonPath(TIMELINE_GET + ".description")
                        .value(containsString("오름차순이다(리포지토리 조회가 보장)")))
                .andExpect(jsonPath(TIMELINE_GET + ".description")
                        .value(containsString("집계는 여기서 다시 계산하지 않는다")));
    }

    @Test
    @DisplayName("타임라인도 빈 상태가 200이라는 것이 문서에 있다")
    void 타임라인_빈_상태_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TIMELINE_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(TIMELINE_GET + ".responses.['200'].description")
                        .value(containsString("수면 데이터가 없는 경우도")));
    }

    // ===== 주간 (REP-06) =====

    @Test
    @DisplayName("주간 리포트 문서가 반영되고 최상위 단일 status라는 것이 남아 있다")
    void 주간_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WEEKLY_GET + ".summary").value(containsString("주간 리포트")))
                .andExpect(jsonPath(WEEKLY_GET + ".summary").value(containsString("REP-06")))
                .andExpect(jsonPath(WEEKLY_GET + ".description")
                        .value(containsString("최상위 단일 `status`")))
                .andExpect(jsonPath(WEEKLY_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseWeeklyReportResponse"));
    }

    /**
     * {@code INSUFFICIENT_DATA}가 가입일 기준이지 "그 주에 기록이 있었는가"가 아니라는 구분이
     * 빠지면, 프론트가 오래된 사용자의 안 잔 주를 신규 사용자 화면으로 잘못 보여줄 수 있다.
     */
    @Test
    @DisplayName("INSUFFICIENT_DATA가 가입일 기준이라는 것과 hitRate가 지표 기준이라는 것이 문서에 있다")
    void 주간_빈_상태와_적중률_기준이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WEEKLY_GET + ".description")
                        .value(containsString("가입일부터 baseDate까지의 일수 + 1")))
                .andExpect(jsonPath(WEEKLY_GET + ".description")
                        .value(containsString("여전히 `FULL`이다")))
                .andExpect(jsonPath(WEEKLY_GET + ".description")
                        .value(containsString("날짜 기준이 아니라 지표 기준이다")));
    }

    // ===== 월간 (REP-07) =====

    @Test
    @DisplayName("월간 리포트 문서가 반영되고 baseDate 기준 역산이라는 것이 남아 있다")
    void 월간_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MONTHLY_GET + ".summary").value(containsString("월간 리포트")))
                .andExpect(jsonPath(MONTHLY_GET + ".summary").value(containsString("REP-07")))
                .andExpect(jsonPath(MONTHLY_GET + ".description")
                        .value(containsString("가입일 앵커가 아니다")))
                .andExpect(jsonPath(MONTHLY_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseMonthlyReportResponse"));
    }

    /**
     * "주 평균의 평균이 아니다"가 문서에서 사라지면, 프론트가 4주 숫자를 단순 평균해 서버 값과
     * 다른 결과를 계산해 보여줄 수 있다.
     */
    @Test
    @DisplayName("summary.avgSleepScore가 주 평균의 평균이 아니라는 것과 isHighest 동점 규칙이 문서에 있다")
    void 월간_평균_계산_방식이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MONTHLY_GET + ".description")
                        .value(containsString("주 평균의 평균이 아니다")))
                .andExpect(jsonPath(MONTHLY_GET + ".description")
                        .value(containsString("동점이면 해당하는 주 전부 `true`")));
    }

}
