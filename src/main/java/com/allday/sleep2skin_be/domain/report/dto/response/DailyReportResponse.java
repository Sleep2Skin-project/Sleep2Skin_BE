package com.allday.sleep2skin_be.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 일간 리포트 (REP-02·04·05).
 *
 * <p><b>{@code sleepSummary}와 {@code skinForecast}는 서로 독립적으로 빈 상태가 될 수 있는
 * 합성 응답이다.</b> 검증을 마친 날의 예보는 세션이 갱신돼도 재산출되지 않는다는 정책 때문에,
 * 두 섹션의 존재 여부가 항상 같이 가지 않는다. 그래서 응답 전체를 하나의 {@code status}로
 * 감싸지 않고, 각 섹션이 자기 자신의 {@code {status, message, 페이로드}}를 갖는다.
 */
@Schema(description = "일간 리포트")
public record DailyReportResponse(

        @Schema(description = "조회 기준일", example = "2026-08-14")
        LocalDate baseDate,

        @Schema(description = "수면 요약 섹션. 독립적으로 빈 상태를 가질 수 있다")
        SleepSummarySection sleepSummary,

        @Schema(description = "피부 예보(전일 대비) 섹션. 독립적으로 빈 상태를 가질 수 있다")
        SkinForecastSection skinForecast
) {

    public static DailyReportResponse of(LocalDate baseDate, SleepSummarySection sleepSummary,
                                         SkinForecastSection skinForecast) {
        return new DailyReportResponse(baseDate, sleepSummary, skinForecast);
    }

}
