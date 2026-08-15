package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.report.dto.ReportPeriodStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 리포트. 최근 7일({@code baseDate} 포함)의 하루치 수면 점수 추이.
 *
 * <p>{@code periodStart}~{@code periodEnd}는 <b>{@code baseDate} 기준으로 역산한다</b> —
 * 가입일에 고정된 창이 아니라 호출할 때마다 최근 7일을 가리키므로 매일 창이 하루씩 밀린다.
 */
@Schema(description = "주간 리포트")
public record WeeklyReportResponse(

        @Schema(description = "조회 상태", example = "FULL")
        ReportPeriodStatus status,

        @Schema(description = "기간 시작일 (`baseDate - 6`)", example = "2026-08-08")
        LocalDate periodStart,

        @Schema(description = "기간 종료일 (`baseDate`)", example = "2026-08-14")
        LocalDate periodEnd,

        @Schema(description = "periodStart~periodEnd 7일치 일별 점수. `INSUFFICIENT_DATA`면 빈 배열")
        List<DailyScore> dailyScores,

        @Schema(description = "기간 요약. `INSUFFICIENT_DATA`면 `null`", nullable = true)
        Summary summary,

        @Schema(description = "수면 피처-피부 지표 상관 강도 7종. `INSUFFICIENT_DATA`면 빈 배열")
        List<FeatureCorrelation> correlations
) {

    public static WeeklyReportResponse insufficientData(LocalDate periodStart, LocalDate periodEnd) {
        return new WeeklyReportResponse(ReportPeriodStatus.INSUFFICIENT_DATA, periodStart, periodEnd,
                List.of(), null, List.of());
    }

    public static WeeklyReportResponse of(LocalDate periodStart, LocalDate periodEnd,
                                          List<DailyScore> dailyScores, Summary summary,
                                          List<FeatureCorrelation> correlations) {
        return new WeeklyReportResponse(ReportPeriodStatus.FULL, periodStart, periodEnd,
                dailyScores, summary, correlations);
    }

    /**
     * @param sleepScore 그날의 수면 점수(§10.8). 세션이 없는 날은 {@code null}이다 — 기록하지
     *                   않은 날을 0점으로 채우면 "안 잔 날"이 "최악으로 잔 날"처럼 보인다
     */
    @Schema(description = "하루치 수면 점수")
    public record DailyScore(

            @Schema(description = "날짜", example = "2026-08-08")
            LocalDate date,

            @Schema(description = "수면 점수. 그날 세션이 없으면 `null`", nullable = true, example = "62")
            Integer sleepScore
    ) {
    }

    /**
     * @param avgSleepScore       기간 내 {@code sleepScore}가 있는 날짜만의 평균, 반올림. 전부
     *                            결측이면 {@code null}
     * @param avgDeepSleepMinutes 기간 내 세션이 있는 날짜만의 {@code deepSleepMinutes} 평균,
     *                            반올림 — {@code avgSleepScore}와 <b>같은 결측 처리</b>다.
     *                            세션이 없는 날은 분모에서 빠지고, 기간 전체에 세션이 없으면
     *                            {@code null}
     */
    @Schema(description = "기간 요약")
    public record Summary(

            @Schema(description = "기간 평균 수면 점수. 전부 결측이면 `null`", nullable = true,
                    example = "70")
            Integer avgSleepScore,

            @Schema(description = "기간 평균 깊은 수면(분). 세션이 없는 날은 제외하고 평균낸다. "
                    + "전부 결측이면 `null`", nullable = true, example = "126")
            Integer avgDeepSleepMinutes
    ) {
    }

}
