package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse.SegmentResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse.WeekScore;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection.MetricDiff;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection.SleepSummary;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.DailyScore;
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

    @Nested
    @DisplayName("일간 리포트 (GET /report/daily)")
    class DailyReport {

        private static final String PATH = "/api/v1/report/daily";

        @Test
        @DisplayName("두 섹션 다 있으면 그대로 내려간다")
        void 두_섹션을_반환한다() throws Exception {
            given(dailyReportService.getDailyReport(USER_ID, BASE_DATE)).willReturn(
                    DailyReportResponse.of(BASE_DATE,
                            SleepSummarySection.of(new SleepSummary(432, 70, 126, 71, 2, 7)),
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
        @DisplayName("FULL이면 일별 점수와 요약을 반환한다")
        void 일별_점수와_요약을_반환한다() throws Exception {
            given(weeklyReportService.getWeeklyReport(USER_ID, BASE_DATE)).willReturn(
                    WeeklyReportResponse.of(BASE_DATE.minusDays(6), BASE_DATE,
                            List.of(new DailyScore(BASE_DATE.minusDays(6), 62),
                                    new DailyScore(BASE_DATE.minusDays(5), null)),
                            new WeeklyReportResponse.Summary(70, 86, 7)));

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
                    .andExpect(jsonPath("$.data.summary.hitRate").value(86))
                    .andExpect(jsonPath("$.data.summary.verifiedDays").value(7));
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
        @DisplayName("FULL이면 주별 평균과 최고 주를 반환한다")
        void 주별_평균과_최고_주를_반환한다() throws Exception {
            given(monthlyReportService.getMonthlyReport(USER_ID, BASE_DATE)).willReturn(
                    MonthlyReportResponse.of(BASE_DATE.minusDays(27), BASE_DATE,
                            List.of(new WeekScore("W1", 65, false), new WeekScore("W2", 58, false),
                                    new WeekScore("W3", 52, false), new WeekScore("W4", 70, true)),
                            new MonthlyReportResponse.Summary(61, 82, 26)));

            mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                            .param("baseDate", "2026-08-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FULL"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-07-18"))
                    .andExpect(jsonPath("$.data.weeks[0].weekLabel").value("W1"))
                    .andExpect(jsonPath("$.data.weeks[3].weekLabel").value("W4"))
                    .andExpect(jsonPath("$.data.weeks[3].avgSleepScore").value(70))
                    .andExpect(jsonPath("$.data.weeks[3].isHighest").value(true))
                    .andExpect(jsonPath("$.data.weeks[0].isHighest").value(false))
                    .andExpect(jsonPath("$.data.summary.avgSleepScore").value(61));
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

}
