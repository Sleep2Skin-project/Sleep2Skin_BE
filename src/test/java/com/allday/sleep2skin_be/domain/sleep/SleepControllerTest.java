package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.UnavailableMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse.Interpretation;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse.SleepSummary;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResult;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수면 세션 업로드의 요청·응답 계약 검증.
 */
@WebMvcTest(SleepController.class)
class SleepControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    private static final String PATH = "/api/v1/sleep/sessions";
    private static final String INTERPRETATION_PATH = "/api/v1/sleep/interpretation";
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    private static final String VALID_BODY = """
            { "segments": [
                { "stage": "CORE", "startTime": "2026-08-06T23:40:00+09:00",
                  "endTime": "2026-08-07T07:10:00+09:00" } ],
              "hrv": 41.2, "restingHeartRate": 63 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SleepSessionService sleepSessionService;

    @MockitoBean
    private SleepInterpretationService sleepInterpretationService;

    @Test
    @DisplayName("그날 첫 수신은 201과 함께 수면 집계·예보를 반환한다")
    void 첫_수신은_201이다() throws Exception {
        given(sleepSessionService.upload(anyLong(), any()))
                .willReturn(new SleepSessionUploadResult(true, response(true)));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.processed").value(true))
                .andExpect(jsonPath("$.data.sleepDate").value("2026-08-07"))
                .andExpect(jsonPath("$.data.sleep.totalSleepMinutes").value(402))
                .andExpect(jsonPath("$.data.sleep.awakeCount").value(3))
                .andExpect(jsonPath("$.data.forecast.darkCircle.score").value(68))
                .andExpect(jsonPath("$.data.forecast.darkCircle.grade").value("NORMAL"));
    }

    @Test
    @DisplayName("이미 그날 세션이 있으면 200이다 — 재수신도 갱신도 새 행이 아니다")
    void 재수신은_200이다() throws Exception {
        given(sleepSessionService.upload(anyLong(), any()))
                .willReturn(new SleepSessionUploadResult(false, response(false)));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(false))
                .andExpect(jsonPath("$.data.forecast.darkCircle.score").value(68));
    }

    @Test
    @DisplayName("빈 지표는 null로 나가고 사유가 함께 실린다")
    void 빈_지표는_사유와_함께_나간다() throws Exception {
        SkinForecastResponse forecast = new SkinForecastResponse(
                new MetricScore(68, SkinGrade.NORMAL), null, new MetricScore(98, SkinGrade.STABLE),
                List.of(new UnavailableMetricResponse(SkinMetric.COMPLEXION,
                        UnavailableReason.MISSING_FEATURES)));
        given(sleepSessionService.upload(anyLong(), any())).willReturn(new SleepSessionUploadResult(
                true, new SleepSessionUploadResponse(true, LocalDate.of(2026, 8, 7), sleep(),
                forecast, exp())));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.forecast.complexion").doesNotExist())
                .andExpect(jsonPath("$.data.forecast.unavailable[0].metric").value("COMPLEXION"))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].reason").value("MISSING_FEATURES"));
    }

    /**
     * <b>이 테스트가 없어서 하루 밀린 예보가 그대로 나갔다.</b> 정규화 테스트는 자바에서 만든
     * {@code OffsetDateTime}을 쓰고, 컨트롤러 테스트는 서비스를 목으로 막아 <b>JSON을 실제로
     * 역직렬화한 값이 정규화에 닿는 경로만 아무도 보지 않았다.</b>
     *
     * <p>Jackson은 기본으로 {@code OffsetDateTime}을 컨텍스트 타임존(UTC)으로 옮긴다
     * ({@code ADJUST_DATES_TO_CONTEXT_TIME_ZONE}). 그러면 {@code 07:10+09:00}이 {@code 22:10Z}가
     * 되어 <b>기상일이 전날로 바뀐다</b> — 한국의 기상 시각은 거의 전부 09:00 이전이라
     * <b>사실상 매번 하루씩 밀린다.</b>
     */
    @Test
    @DisplayName("요청의 오프셋이 역직렬화에서 살아남는다 — 기상일이 하루 밀리면 안 된다")
    void 요청_오프셋이_역직렬화에서_보존된다() throws Exception {
        given(sleepSessionService.upload(anyLong(), any()))
                .willReturn(new SleepSessionUploadResult(true, response(true)));

        ArgumentCaptor<SleepNormalizationCommand> captured =
                ArgumentCaptor.forClass(SleepNormalizationCommand.class);

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(sleepSessionService).upload(anyLong(), captured.capture());
        SleepSegmentCommand segment = captured.getValue().segments().getFirst();

        assertThat(segment.endTime().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(segment.endTime()).isEqualTo(OffsetDateTime.parse("2026-08-07T07:10:00+09:00"));

        // 정규화까지 통과시켜 실제로 저장될 기준일을 확인한다 — 07:10 KST 기상은 8월 7일이다
        assertThat(new SleepSessionNormalizer().normalize(captured.getValue()).sleepDate())
                .isEqualTo(LocalDate.of(2026, 8, 7));
    }

    @Test
    @DisplayName("segments가 비어 있으면 서비스를 호출하지 않고 400이다")
    void 빈_구간은_400이다() throws Exception {
        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"segments\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(sleepSessionService, never()).upload(anyLong(), any());
    }

    /**
     * 핸들러가 없으면 이 케이스가 <b>500으로 나간다.</b> 서버 잘못이 아니라 페이로드 형식 오류다.
     */
    @Test
    @DisplayName("알 수 없는 수면 단계는 400이다")
    void 알_수_없는_단계는_400이다() throws Exception {
        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "segments": [ { "stage": "NAP",
                                  "startTime": "2026-08-06T23:40:00+09:00",
                                  "endTime": "2026-08-07T07:10:00+09:00" } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(sleepSessionService, never()).upload(anyLong(), any());
    }

    /**
     * 오프셋 없이 받으면 서버가 UTC로 해석해 {@code sleepDate}가 하루 밀리고 예보·검증 조인이
     * 전부 어긋난다. <b>조용히 통과하는 것보다 거절하는 편이 낫다.</b>
     */
    @Test
    @DisplayName("시각에 오프셋이 없으면 400이다")
    void 오프셋이_없으면_400이다() throws Exception {
        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "segments": [ { "stage": "CORE",
                                  "startTime": "2026-08-06T23:40:00",
                                  "endTime": "2026-08-07T07:10:00" } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(sleepSessionService, never()).upload(anyLong(), any());
    }

    @Test
    @DisplayName("구간이 겹치면 SLEEP_STAGE_INVALID로 400이다")
    void 겹치는_구간은_400이다() throws Exception {
        given(sleepSessionService.upload(anyLong(), any()))
                .willThrow(new BusinessException(ErrorCode.SLEEP_STAGE_INVALID));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SLEEP_STAGE_INVALID"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() throws Exception {
        given(sleepSessionService.upload(anyLong(), any()))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 서비스를 호출하지 않고 400이다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

        verify(sleepSessionService, never()).upload(anyLong(), any());
    }

    // ===== 수면 통역 카드 =====

    @Test
    @DisplayName("통역 카드는 짚어낸 피처와 헤드라인을 함께 반환한다")
    void 통역_카드를_반환한다() throws Exception {
        given(sleepInterpretationService.getInterpretation(USER_ID, BASE_DATE)).willReturn(
                SleepInterpretationResponse.of(BASE_DATE, Interpretation.improve(
                        "밤중에 3번 깼어요. 다크서클 회복이 더뎌질 수 있어요.",
                        SleepFeature.AWAKE_COUNT, 50)));

        mockMvc.perform(get(INTERPRETATION_PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.interpretation.tone").value("IMPROVE"))
                .andExpect(jsonPath("$.data.interpretation.headline").value(containsString("3번")))
                .andExpect(jsonPath("$.data.interpretation.focus.feature").value("AWAKE_COUNT"))
                .andExpect(jsonPath("$.data.interpretation.focus.label").value("야간 각성"))
                .andExpect(jsonPath("$.data.interpretation.focus.score").value(50));
    }

    @Test
    @DisplayName("잘 잔 밤은 focus가 null로 나간다 — 억지로 지적하지 않는다")
    void 칭찬일_때는_focus가_null이다() throws Exception {
        given(sleepInterpretationService.getInterpretation(USER_ID, BASE_DATE)).willReturn(
                SleepInterpretationResponse.of(BASE_DATE,
                        Interpretation.praise("어젯밤은 회복에 충분한 잠이었어요.")));

        mockMvc.perform(get(INTERPRETATION_PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interpretation.tone").value("PRAISE"))
                .andExpect(content().string(containsString("\"focus\":null")));
    }

    @Test
    @DisplayName("수면 데이터가 없어도 200이고 status로 알린다")
    void 통역_카드_빈_상태도_200이다() throws Exception {
        given(sleepInterpretationService.getInterpretation(USER_ID, BASE_DATE))
                .willReturn(SleepInterpretationResponse.empty(BASE_DATE));

        mockMvc.perform(get(INTERPRETATION_PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SLEEP_DATA"))
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                .andExpect(content().string(containsString("\"interpretation\":null")));
    }

    @Test
    @DisplayName("통역 카드도 baseDate가 없으면 400이다")
    void 통역_카드는_기준일이_필수다() throws Exception {
        mockMvc.perform(get(INTERPRETATION_PATH).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(sleepInterpretationService, never()).getInterpretation(anyLong(), any());
    }

    @Test
    @DisplayName("통역 카드도 존재하지 않는 사용자는 404다")
    void 통역_카드_없는_사용자는_404다() throws Exception {
        given(sleepInterpretationService.getInterpretation(USER_ID, BASE_DATE))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get(INTERPRETATION_PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", "2026-08-07"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ===== 픽스처 =====

    private static SleepSessionUploadResponse response(boolean processed) {
        return new SleepSessionUploadResponse(processed, LocalDate.of(2026, 8, 7), sleep(),
                new SkinForecastResponse(
                        new MetricScore(68, SkinGrade.NORMAL),
                        new MetricScore(69, SkinGrade.NORMAL),
                        new MetricScore(98, SkinGrade.STABLE),
                        List.of()),
                exp());
    }

    private static SleepSummary sleep() {
        return new SleepSummary(
                OffsetDateTime.parse("2026-08-06T14:40:00Z"),
                OffsetDateTime.parse("2026-08-06T22:10:00Z"),
                402, 54, 71, 277, 3, 21, 78);
    }

    /** 수면 점수 증가 보상이 붙은 응답 (HOME-04). */
    private static ExpResponse exp() {
        return ExpResponse.of(294, 320,
                List.of(new ExpReasonResponse(ExpReason.SLEEP_SCORE_IMPROVED, 26)));
    }

}
