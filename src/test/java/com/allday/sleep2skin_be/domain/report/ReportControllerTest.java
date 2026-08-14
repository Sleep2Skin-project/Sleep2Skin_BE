package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse.SegmentResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection.MetricDiff;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection.SleepSummary;
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

}
