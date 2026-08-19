package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.report.dto.MetricTrend;
import com.allday.sleep2skin_be.domain.report.dto.VolatileDirection;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse.SegmentResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.CorrelationGroup;
import com.allday.sleep2skin_be.domain.report.dto.response.FeatureCorrelation;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse.WeekScore;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.ClinicNeeded;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.MetricTrendResult;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.Trends;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection.MetricDiff;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection.SleepSummary;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.DailyScore;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyReportService dailyReportService;

    @MockitoBean
    private DailyTimelineService dailyTimelineService;

    @MockitoBean
    private WeeklyReportService weeklyReportService;

    @MockitoBean
    private MonthlyReportService monthlyReportService;

    @MockitoBean
    private OverallReportService overallReportService;

    @Nested
    @DisplayName("일간 리포트 (GET /report/daily)")
    class DailyReport {

        private static final String PATH = "/api/v1/report/daily";

        @Test
        @DisplayName("두 섹션 다 있으면 그대로 내려간다")
        void 두_섹션을_반환한다() throws Exception {
            given(dailyReportService.getDailyReport(USER_ID, BASE_DATE)).willReturn(
                    DailyReportResponse.of(BASE_DATE,
                            SleepSummarySection.of(new SleepSummary(432, 70, 126, 71, 2, 7, 36, 42.0, 55)),
                            new SkinForecastSection(QueryStatus.AVAILABLE, null,
                                    new MetricDiff(44, 1), new MetricDiff(63, 7),
                                    new MetricDiff(79, null))));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.data.baseDate").value("2026-08-14"))
                    .andExpect(jsonPath("$.data.sleepSummary.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.totalSleepMinutes").value(432))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.sleepScore").value(70))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.lightSleepMinutes").value(71))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.remSleepMinutes").value(36))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.hrv").value(42.0))
                    .andExpect(jsonPath("$.data.sleepSummary.summary.restingHeartRate").value(55))
                    .andExpect(jsonPath("$.data.skinForecast.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.data.skinForecast.darkCircle.today").value(44))
                    .andExpect(jsonPath("$.data.skinForecast.darkCircle.diffFromYesterday").value(1))
                    .andExpect(jsonPath("$.data.skinForecast.barrier.diffFromYesterday").doesNotExist());
        }

        /**
         * 빈 상태에서도 각 섹션 안쪽의 {@code null}은 그대로 직렬화된다 — 사라지는 것은 래퍼의
         * {@code error}뿐이다.
         */
        @Test
        @DisplayName("두 섹션이 독립적으로 빈 상태일 수 있고 200이다")
        void 섹션별로_독립적인_빈_상태다() throws Exception {
            given(dailyReportService.getDailyReport(USER_ID, BASE_DATE)).willReturn(
                    DailyReportResponse.of(BASE_DATE, SleepSummarySection.empty(),
                            new SkinForecastSection(QueryStatus.AVAILABLE, null,
                                    new MetricDiff(44, null), new MetricDiff(63, null),
                                    new MetricDiff(79, null))));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sleepSummary.status").value("NO_SLEEP_DATA"))
                    .andExpect(jsonPath("$.data.sleepSummary.message").isNotEmpty())
                    .andExpect(content().string(containsString("\"summary\":null")))
                    .andExpect(jsonPath("$.data.skinForecast.status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(dailyReportService, never()).getDailyReport(anyLong(), any());
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400이다")
        void 헤더가_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).param("baseDate", "2026-08-14"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

            verify(dailyReportService, never()).getDailyReport(anyLong(), any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() throws Exception {
            given(dailyReportService.getDailyReport(USER_ID, BASE_DATE))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("일간 타임라인 (GET /report/daily/timeline)")
    class DailyTimeline {

        private static final String PATH = "/api/v1/report/daily/timeline";

        @Test
        @DisplayName("구간이 시간순으로 내려간다")
        void 구간을_반환한다() throws Exception {
            OffsetDateTime onset = OffsetDateTime.parse("2026-08-13T14:40:00Z");
            OffsetDateTime wake = OffsetDateTime.parse("2026-08-13T22:10:00Z");
            given(dailyTimelineService.getTimeline(USER_ID, BASE_DATE)).willReturn(
                    new DailyTimelineResponse(QueryStatus.AVAILABLE, null, BASE_DATE, onset, wake,
                            List.of(new SegmentResponse(SleepStage.DEEP, onset, onset.plusMinutes(30)),
                                    new SegmentResponse(SleepStage.AWAKE,
                                            onset.plusMinutes(30), onset.plusMinutes(37)))));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.data.segments[0].stage").value("DEEP"))
                    .andExpect(jsonPath("$.data.segments[1].stage").value("AWAKE"));
        }

        @Test
        @DisplayName("세션이 없으면 200이고 빈 배열이다")
        void 빈_상태도_200이다() throws Exception {
            given(dailyTimelineService.getTimeline(USER_ID, BASE_DATE))
                    .willReturn(DailyTimelineResponse.empty(BASE_DATE));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("NO_SLEEP_DATA"))
                    .andExpect(jsonPath("$.data.segments").isArray())
                    .andExpect(jsonPath("$.data.segments").isEmpty())
                    .andExpect(content().string(containsString("\"sleepOnsetTime\":null")));
        }

        @Test
        @DisplayName("baseDate가 없으면 400이다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(dailyTimelineService, never()).getTimeline(anyLong(), any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() throws Exception {
            given(dailyTimelineService.getTimeline(USER_ID, BASE_DATE))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("주간 리포트 (GET /report/weekly)")
    class WeeklyReport {

        private static final String PATH = "/api/v1/report/weekly";

        @Test
        @DisplayName("FULL이면 일별 점수·요약·상관 강도를 반환한다")
        void 일별_점수와_요약을_반환한다() throws Exception {
            given(weeklyReportService.getWeeklyReport(USER_ID, BASE_DATE)).willReturn(
                    WeeklyReportResponse.of(BASE_DATE.minusDays(6), BASE_DATE,
                            List.of(new DailyScore(BASE_DATE.minusDays(6), 62),
                                    new DailyScore(BASE_DATE.minusDays(5), null)),
                            new WeeklyReportResponse.Summary(70, 126),
                            CorrelationGroup.groupBySkinMetric(List.of(
                                    new FeatureCorrelation(SleepFeature.AWAKE_COUNT, "야간 각성",
                                            SkinMetric.DARK_CIRCLE, "다크서클 회복",
                                            CorrelationStrength.VERY_STRONG, 6, false),
                                    new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                                            SkinMetric.COMPLEXION, "혈색", null, 2, true),
                                    new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                                            SkinMetric.BARRIER, "장벽",
                                            CorrelationStrength.MODERATE, 6, false)))));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("FULL"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-08-08"))
                    .andExpect(jsonPath("$.data.periodEnd").value("2026-08-14"))
                    .andExpect(jsonPath("$.data.dailyScores[0].sleepScore").value(62))
                    .andExpect(jsonPath("$.data.dailyScores[1].sleepScore").doesNotExist())
                    .andExpect(jsonPath("$.data.summary.avgSleepScore").value(70))
                    .andExpect(jsonPath("$.data.summary.avgDeepSleepMinutes").value(126))
                    .andExpect(jsonPath("$.data.summary.hitRate").doesNotExist())
                    .andExpect(jsonPath("$.data.summary.verifiedDays").doesNotExist())
                    .andExpect(jsonPath("$.data.correlations", hasSize(3)))
                    .andExpect(jsonPath("$.data.correlations[0].skinMetric").value("DARK_CIRCLE"))
                    .andExpect(jsonPath("$.data.correlations[0].topCorrelation.sleepFeature")
                            .value("AWAKE_COUNT"))
                    .andExpect(jsonPath("$.data.correlations[0].topCorrelation.strength")
                            .value("VERY_STRONG"))
                    .andExpect(jsonPath("$.data.correlations[0].topCorrelation.insufficientSample")
                            .value(false))
                    .andExpect(jsonPath("$.data.correlations[1].skinMetric").value("COMPLEXION"))
                    .andExpect(jsonPath("$.data.correlations[1].topCorrelation.strength").doesNotExist())
                    .andExpect(jsonPath("$.data.correlations[1].topCorrelation.insufficientSample")
                            .value(true));
        }

        @Test
        @DisplayName("가입한 지 얼마 안 됐으면 INSUFFICIENT_DATA이고 빈 배열이다")
        void 신규_사용자는_빈_상태다() throws Exception {
            given(weeklyReportService.getWeeklyReport(USER_ID, BASE_DATE)).willReturn(
                    WeeklyReportResponse.insufficientData(BASE_DATE.minusDays(6), BASE_DATE));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_DATA"))
                    .andExpect(jsonPath("$.data.dailyScores").isEmpty())
                    .andExpect(jsonPath("$.data.correlations").isEmpty())
                    .andExpect(content().string(containsString("\"summary\":null")));
        }

        @Test
        @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(weeklyReportService, never()).getWeeklyReport(anyLong(), any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() throws Exception {
            given(weeklyReportService.getWeeklyReport(USER_ID, BASE_DATE))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("월간 리포트 (GET /report/monthly)")
    class MonthlyReport {

        private static final String PATH = "/api/v1/report/monthly";

        @Test
        @DisplayName("FULL이면 주별 평균·최고 주·상관 강도를 반환한다")
        void 주별_평균과_최고_주를_반환한다() throws Exception {
            given(monthlyReportService.getMonthlyReport(USER_ID, BASE_DATE)).willReturn(
                    MonthlyReportResponse.of(BASE_DATE.minusDays(27), BASE_DATE,
                            List.of(new WeekScore("W1", 65, 110, false), new WeekScore("W2", 58, 105, false),
                                    new WeekScore("W3", 52, 98, false), new WeekScore("W4", 70, 132, true)),
                            new MonthlyReportResponse.Summary(61, 118),
                            CorrelationGroup.groupBySkinMetric(List.of(
                                    new FeatureCorrelation(SleepFeature.AWAKE_COUNT, "야간 각성",
                                    SkinMetric.DARK_CIRCLE, "다크서클 회복",
                                    CorrelationStrength.STRONG, 22, false),
                                    new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                                    SkinMetric.COMPLEXION, "혈색",
                                    CorrelationStrength.WEAK, 22, false),
                                    new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                                    SkinMetric.BARRIER, "장벽",
                                    CorrelationStrength.MODERATE, 22, false)))));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FULL"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-07-18"))
                    .andExpect(jsonPath("$.data.weeks[0].weekLabel").value("W1"))
                    .andExpect(jsonPath("$.data.weeks[3].weekLabel").value("W4"))
                    .andExpect(jsonPath("$.data.weeks[3].avgSleepScore").value(70))
                    .andExpect(jsonPath("$.data.weeks[3].avgDeepSleepMinutes").value(132))
                    .andExpect(jsonPath("$.data.weeks[3].isHighest").value(true))
                    .andExpect(jsonPath("$.data.weeks[0].isHighest").value(false))
                    .andExpect(jsonPath("$.data.summary.avgSleepScore").value(61))
                    .andExpect(jsonPath("$.data.summary.avgDeepSleepMinutes").value(118))
                    .andExpect(jsonPath("$.data.summary.hitRate").doesNotExist())
                    .andExpect(jsonPath("$.data.summary.verifiedDays").doesNotExist())
                    .andExpect(jsonPath("$.data.correlations", hasSize(3)))
                    .andExpect(jsonPath("$.data.correlations[0].skinMetric").value("DARK_CIRCLE"))
                    .andExpect(jsonPath("$.data.correlations[0].topCorrelation.sampleSize").value(22));
        }

        @Test
        @DisplayName("가입한 지 얼마 안 됐으면 INSUFFICIENT_DATA이고 빈 배열이다")
        void 신규_사용자는_빈_상태다() throws Exception {
            given(monthlyReportService.getMonthlyReport(USER_ID, BASE_DATE)).willReturn(
                    MonthlyReportResponse.insufficientData(BASE_DATE.minusDays(27), BASE_DATE));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_DATA"))
                    .andExpect(jsonPath("$.data.weeks").isEmpty())
                    .andExpect(jsonPath("$.data.correlations").isEmpty())
                    .andExpect(content().string(containsString("\"summary\":null")));
        }

        @Test
        @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(monthlyReportService, never()).getMonthlyReport(anyLong(), any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() throws Exception {
            given(monthlyReportService.getMonthlyReport(USER_ID, BASE_DATE))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("종합 리포트 (GET /report/overall)")
    class OverallReport {

        private static final String PATH = "/api/v1/report/overall";

        @Test
        @DisplayName("trends·appManaged·clinicNeeded·clinicLink을 반환한다")
        void 종합_리포트를_반환한다() throws Exception {
            given(overallReportService.getOverallReport(USER_ID, BASE_DATE)).willReturn(
                    OverallReportResponse.of(BASE_DATE.minusDays(20), BASE_DATE,
                            new Trends(
                                    new MetricTrendResult(MetricTrend.IMPROVED, null, 48, 79),
                                    new MetricTrendResult(MetricTrend.VOLATILE,
                                            VolatileDirection.RISE_THEN_FALL, 61, 58),
                                    new MetricTrendResult(MetricTrend.INSUFFICIENT_SAMPLE, null, null, 65)),
                            List.of(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER),
                            new ClinicNeeded(false, false, true)));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FULL"))
                    .andExpect(jsonPath("$.data.message").doesNotExist())
                    .andExpect(jsonPath("$.data.periodStart").value("2026-07-25"))
                    .andExpect(jsonPath("$.data.periodEnd").value("2026-08-14"))
                    .andExpect(jsonPath("$.data.trends.darkCircle.trend").value("IMPROVED"))
                    .andExpect(jsonPath("$.data.trends.darkCircle.w1Average").value(48))
                    .andExpect(jsonPath("$.data.trends.darkCircle.w3Average").value(79))
                    .andExpect(jsonPath("$.data.trends.complexion.trend").value("VOLATILE"))
                    .andExpect(jsonPath("$.data.trends.complexion.volatileDirection").value("RISE_THEN_FALL"))
                    .andExpect(jsonPath("$.data.trends.barrier.trend").value("INSUFFICIENT_SAMPLE"))
                    .andExpect(jsonPath("$.data.trends.barrier.w1Average").doesNotExist())
                    .andExpect(jsonPath("$.data.appManaged[0]").value("DARK_CIRCLE"))
                    .andExpect(jsonPath("$.data.appManaged[1]").value("COMPLEXION"))
                    .andExpect(jsonPath("$.data.appManaged[2]").value("BARRIER"))
                    .andExpect(jsonPath("$.data.clinicNeeded.pigmentationDetected").value(false))
                    .andExpect(jsonPath("$.data.clinicNeeded.agingDetected").value(true))
                    .andExpect(jsonPath("$.data.clinicLink").value("https://amredclinic.com/ko"));
        }

        @Test
        @DisplayName("가입한 지 21일 미만이면 INSUFFICIENT_DATA이고 trends는 없다")
        void 가입_21일_미만이면_INSUFFICIENT_DATA다() throws Exception {
            given(overallReportService.getOverallReport(USER_ID, BASE_DATE)).willReturn(
                    OverallReportResponse.insufficientData(BASE_DATE.minusDays(20), BASE_DATE,
                            List.of(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER), null));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_DATA"))
                    .andExpect(jsonPath("$.data.message").isNotEmpty())
                    .andExpect(content().string(containsString("\"trends\":null")))
                    .andExpect(content().string(containsString("\"clinicNeeded\":null")));
        }

        @Test
        @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
        void 기준일이_없으면_400이다() throws Exception {
            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

            verify(overallReportService, never()).getOverallReport(anyLong(), any());
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() throws Exception {
            given(overallReportService.getOverallReport(USER_ID, BASE_DATE))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

}
