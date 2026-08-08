package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.UnavailableMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse.SleepSummary;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResult;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                true, new SleepSessionUploadResponse(true, LocalDate.of(2026, 8, 7), sleep(), forecast)));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.forecast.complexion").doesNotExist())
                .andExpect(jsonPath("$.data.forecast.unavailable[0].metric").value("COMPLEXION"))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].reason").value("MISSING_FEATURES"));
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

    // ===== 픽스처 =====

    private static SleepSessionUploadResponse response(boolean processed) {
        return new SleepSessionUploadResponse(processed, LocalDate.of(2026, 8, 7), sleep(),
                new SkinForecastResponse(
                        new MetricScore(68, SkinGrade.NORMAL),
                        new MetricScore(69, SkinGrade.NORMAL),
                        new MetricScore(98, SkinGrade.STABLE),
                        List.of()));
    }

    private static SleepSummary sleep() {
        return new SleepSummary(
                OffsetDateTime.parse("2026-08-06T14:40:00Z"),
                OffsetDateTime.parse("2026-08-06T22:10:00Z"),
                402, 54, 71, 277, 3, 21);
    }

}
