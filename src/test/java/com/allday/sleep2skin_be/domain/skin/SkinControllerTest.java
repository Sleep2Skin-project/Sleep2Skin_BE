package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelUpdateResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelUpdateResponse.WeightChangeResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.UnavailableMetricResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkippedMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkinController.class)
class SkinControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    private static final String PATH = "/api/v1/skin/forecast";
    private static final String SELFIE_PATH = "/api/v1/skin/selfie";
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinForecastService skinForecastService;

    @MockitoBean
    private SelfieAnalysisService selfieAnalysisService;

    @Test
    @DisplayName("예보가 있으면 200과 함께 점수·등급을 반환한다")
    void 예보를_반환한다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE)).willReturn(available());

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.baseDate").value("2026-08-07"))
                .andExpect(jsonPath("$.data.forecast.darkCircle.score").value(67))
                .andExpect(jsonPath("$.data.forecast.barrier.grade").value("STABLE"));
    }

    /**
     * 4xx로 내리면 경로 오타·잘못된 {@code userId}와 섞여 모니터링에서 신규 유입이 에러 급증으로
     * 보인다. <b>빈 상태는 이 서비스의 정상 흐름이다.</b>
     */
    @Test
    @DisplayName("예보가 없어도 200이고 status로 알린다")
    void 빈_상태도_200이다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willReturn(SkinForecastQueryResponse.empty(BASE_DATE));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NO_SLEEP_DATA"))
                .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    /**
     * 래퍼의 {@code error}만 사라지고 <b>페이로드 안쪽의 {@code null}은 그대로 나간다.</b>
     * 키가 통째로 없어지면 클라이언트가 "산출 불가"와 "키 이름 오류"를 구분할 수 없다.
     */
    @Test
    @DisplayName("빈 상태에서도 forecast 키가 null로 실려 나간다")
    void 페이로드_안쪽_null은_직렬화된다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willReturn(SkinForecastQueryResponse.empty(BASE_DATE));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(content().string(containsString("\"forecast\":null")))
                .andExpect(content().string(not(containsString("\"error\""))));
    }

    @Test
    @DisplayName("산출하지 못한 지표는 null로 나가고 사유가 함께 실린다")
    void 빈_지표는_사유와_함께_나간다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE)).willReturn(
                new SkinForecastQueryResponse(QueryStatus.AVAILABLE, null, BASE_DATE,
                        new SkinForecastResponse(
                                new MetricScore(67, SkinGrade.NORMAL), null,
                                new MetricScore(81, SkinGrade.STABLE),
                                List.of(new UnavailableMetricResponse(SkinMetric.COMPLEXION,
                                        UnavailableReason.MISSING_FEATURES)))));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(content().string(containsString("\"complexion\":null")))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].metric").value("COMPLEXION"))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].reason").value("MISSING_FEATURES"));
    }

    /**
     * 서버는 "오늘"을 모른다 — 타임존을 저장하지 않기 때문이다. 기본값을 넣어주면 UTC 기준이 되어
     * <b>한국 시간 오전 9시 이전에 하루 밀린 예보</b>가 나간다. 그래서 필수다.
     */
    @Test
    @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
    void 기준일이_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    @Test
    @DisplayName("baseDate 형식이 틀리면 400이다")
    void 기준일_형식이_틀리면_400이다() throws Exception {
        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026/08/07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400이고 서비스를 호출하지 않는다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).param("baseDate", "2026-08-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    private static SkinForecastQueryResponse available() {
        return new SkinForecastQueryResponse(QueryStatus.AVAILABLE, null, BASE_DATE,
                new SkinForecastResponse(
                        new MetricScore(67, SkinGrade.NORMAL),
                        new MetricScore(62, SkinGrade.NORMAL),
                        new MetricScore(81, SkinGrade.STABLE),
                        List.of()));
    }

    @Nested
    @DisplayName("셀피 분석·검증 (POST /skin/selfie)")
    class VerifySelfie {

        @Test
        @DisplayName("판정 결과와 적중률을 반환한다")
        void 검증_결과를_반환한다() throws Exception {
            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), any()))
                    .willReturn(verified());

            mockMvc.perform(multipart(SELFIE_PATH).file(image())
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.data.baseDate").value("2026-08-07"))
                    .andExpect(jsonPath("$.data.verifications[0].metric").value("DARK_CIRCLE"))
                    .andExpect(jsonPath("$.data.verifications[0].difference").value(6))
                    .andExpect(jsonPath("$.data.verifications[0].verdict").value("CLOSE"))
                    .andExpect(jsonPath("$.data.skipped[0].metric").value("COMPLEXION"))
                    .andExpect(jsonPath("$.data.skipped[0].measured.score").value(55))
                    .andExpect(jsonPath("$.data.hitRate").value(50))
                    .andExpect(jsonPath("$.data.model.updated").value(true))
                    .andExpect(jsonPath("$.data.model.changes[0].feature").value("AWAKE_COUNT"))
                    .andExpect(jsonPath("$.data.model.changes[0].metric").value("DARK_CIRCLE"))
                    .andExpect(jsonPath("$.data.model.changes[0].label").value("야간 각성"))
                    .andExpect(jsonPath("$.data.model.changes[0].after").value(1.0110));
        }

        /**
         * 서버는 "오늘"을 모른다. 기본값을 넣어주면 UTC 기준이 되어 <b>한국 시간 오전 9시 이전에
         * 하루 밀린 예보와 대조</b>하게 되고, 값 범위는 정상이라 적중률만 조용히 무너진다.
         *
         * <p>⚠️ <b>"폼 필드가 아니라 쿼리"는 계약이지 서버가 막는 것이 아니다.</b> 멀티파트의 텍스트
         * 파트는 {@code @RequestParam}에 그대로 바인딩되므로 폼 필드로 보내도 동작한다. 위치를
         * 통일하는 이유는 앱이 API마다 다른 규칙을 외우지 않게 하려는 것이고(conventions.md §8),
         * 그건 문서가 지킨다 — 여기서 강제하는 척하지 않는다.
         */
        @Test
        @DisplayName("baseDate가 없으면 400이고 분석하지 않는다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(multipart(SELFIE_PATH).file(image()).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(selfieAnalysisService, never()).analyzeAndVerify(anyLong(), any(), any());
        }

        /**
         * 기본값({@code required = true})으로 두면 파트 누락이 컨트롤러 앞에서 터져
         * {@code INVALID_INPUT}이 된다. 앱은 "다시 촬영"과 "요청 버그"를 구분하지 못한다.
         */
        @Test
        @DisplayName("image 파트가 없으면 SELFIE_IMAGE_INVALID다 — INVALID_INPUT이 아니다")
        void 파트가_없으면_이미지_에러다() throws Exception {
            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), isNull()))
                    .willThrow(new BusinessException(ErrorCode.SELFIE_IMAGE_INVALID));

            mockMvc.perform(multipart(SELFIE_PATH)
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SELFIE_IMAGE_INVALID"));
        }

        @Test
        @DisplayName("그날 예보가 없으면 404 SKIN_FORECAST_NOT_FOUND다 — 여기서는 빈 상태가 아니다")
        void 예보가_없으면_404다() throws Exception {
            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), any()))
                    .willThrow(new BusinessException(ErrorCode.SKIN_FORECAST_NOT_FOUND));

            mockMvc.perform(multipart(SELFIE_PATH).file(image())
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SKIN_FORECAST_NOT_FOUND"));
        }

        @Test
        @DisplayName("같은 날 두 번째 검증은 409다")
        void 중복_검증은_409다() throws Exception {
            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), any()))
                    .willThrow(new BusinessException(ErrorCode.VERIFICATION_ALREADY_DONE));

            mockMvc.perform(multipart(SELFIE_PATH).file(image())
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("VERIFICATION_ALREADY_DONE"));
        }

        /** 앱이 재시도해도 되는지 판단하려면 502·504가 그대로 나가야 한다. */
        @Test
        @DisplayName("분석 실패는 502, 지연은 504로 나간다")
        void 외부_실패는_502와_504다() throws Exception {
            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), any()))
                    .willThrow(new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED));

            mockMvc.perform(multipart(SELFIE_PATH).file(image())
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("SELFIE_ANALYSIS_FAILED"));

            given(selfieAnalysisService.analyzeAndVerify(eq(USER_ID), eq(BASE_DATE), any()))
                    .willThrow(new BusinessException(ErrorCode.SELFIE_ANALYSIS_TIMEOUT));

            mockMvc.perform(multipart(SELFIE_PATH).file(image())
                            .header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.error.code").value("SELFIE_ANALYSIS_TIMEOUT"));
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 분석하지 않고 400이다")
        void 헤더가_없으면_400이다() throws Exception {
            mockMvc.perform(multipart(SELFIE_PATH).file(image()).param("baseDate", "2026-08-07"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

            verify(selfieAnalysisService, never()).analyzeAndVerify(anyLong(), any(), any());
        }

        private MockMultipartFile image() {
            return new MockMultipartFile("image", "selfie.jpg", "image/jpeg",
                    "셀피".getBytes(StandardCharsets.UTF_8));
        }

        /** 혈색 예보가 없던 날 — 대조 2건, 제외 1건이라 적중률 분모가 2다. */
        private SelfieVerificationResponse verified() {
            return new SelfieVerificationResponse(BASE_DATE,
                    OffsetDateTime.parse("2026-08-07T12:33:12Z"),
                    List.of(
                            MetricVerificationResponse.of(SkinMetric.DARK_CIRCLE, 67, 61),
                            MetricVerificationResponse.of(SkinMetric.BARRIER, 81, 78)),
                    List.of(SkippedMetricResponse.of(SkinMetric.COMPLEXION, 55, true)),
                    50,
                    new PersonalModelUpdateResponse(true,
                            "야간 각성을(를) 조금 더 중요하게 보도록 학습했어요.",
                            List.of(new WeightChangeResponse(SleepFeature.AWAKE_COUNT,
                                    SkinMetric.DARK_CIRCLE, "야간 각성",
                                    new BigDecimal("1.0000"), new BigDecimal("1.0110")))));
        }
    }

}
