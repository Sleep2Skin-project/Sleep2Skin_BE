package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.report.dto.ReportPeriodStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 월간 리포트. 최근 28일을 7일씩 4주(W1~W4)로 나눈 주별 평균과 최고 주.
 *
 * <p>4주 구간은 <b>{@code baseDate} 기준으로 역산한다 — 가입일에 고정된 앵커가 아니다.</b>
 * {@code W4}가 항상 {@code baseDate}를 포함한 최근 7일이고 {@code W1}이 가장 과거다. 가입일을
 * 앵커로 삼으면 요일마다 주 경계가 달라져 "이번 주"라는 감각과 어긋난다.
 */
@Schema(description = "월간 리포트")
public record MonthlyReportResponse(

        @Schema(description = "조회 상태", example = "FULL")
        ReportPeriodStatus status,

        @Schema(description = "기간 시작일 (`baseDate - 27`)", example = "2026-07-18")
        LocalDate periodStart,

        @Schema(description = "기간 종료일 (`baseDate`)", example = "2026-08-14")
        LocalDate periodEnd,

        @Schema(description = "W1(가장 과거)~W4(`baseDate`를 포함한 최근 7일) 4주. "
                + "`INSUFFICIENT_DATA`면 빈 배열")
        List<WeekScore> weeks,

        @Schema(description = "28일 전체 요약. `INSUFFICIENT_DATA`면 `null`", nullable = true)
        Summary summary,

        @Schema(description = "수면 피처-피부 지표 상관 강도 7종. `INSUFFICIENT_DATA`면 빈 배열")
        List<FeatureCorrelation> correlations
) {

    public static MonthlyReportResponse insufficientData(LocalDate periodStart, LocalDate periodEnd) {
        return new MonthlyReportResponse(ReportPeriodStatus.INSUFFICIENT_DATA, periodStart, periodEnd,
                List.of(), null, List.of());
    }

    public static MonthlyReportResponse of(LocalDate periodStart, LocalDate periodEnd,
                                           List<WeekScore> weeks, Summary summary,
                                           List<FeatureCorrelation> correlations) {
        return new MonthlyReportResponse(ReportPeriodStatus.FULL, periodStart, periodEnd, weeks, summary,
                correlations);
    }

    /**
     * @param avgSleepScore       그 주 7일 중 수면 점수가 있는 날짜만의 평균, 반올림. 7일 모두
     *                            결측이면 {@code null}
     * @param avgDeepSleepMinutes 그 주 7일 중 세션이 있는 날짜만의 {@code deepSleepMinutes}
     *                            평균, 반올림 — {@code avgSleepScore}와 같은 결측 처리다.
     *                            7일 모두 결측이면 {@code null}
     * @param isHighest           4주 중 {@code avgSleepScore}가 가장 높은 주(들)인가.
     *                            {@code null}인 주는 비교 대상에서 빠지고, 최고값이 동점이면
     *                            해당하는 주 전부 {@code true}이며, 4주 모두 {@code null}이면
     *                            전부 {@code false}다(비교할 값 자체가 없다). <b>이 판정은
     *                            {@code avgSleepScore} 기준이지 {@code avgDeepSleepMinutes}
     *                            기준이 아니다</b>
     */
    @Schema(description = "주별 평균 점수")
    public record WeekScore(

            @Schema(description = "주 라벨 (`W1`이 가장 과거, `W4`가 `baseDate`를 포함한 최근 7일)",
                    example = "W4")
            String weekLabel,

            @Schema(description = "그 주 평균 수면 점수. 7일 모두 결측이면 `null`", nullable = true,
                    example = "70")
            Integer avgSleepScore,

            @Schema(description = "그 주 평균 깊은 수면(분). 세션이 없는 날은 제외하고 평균낸다. "
                    + "7일 모두 결측이면 `null`", nullable = true, example = "110")
            Integer avgDeepSleepMinutes,

            @Schema(description = "4주 중 최고 평균 점수인가. 동점이면 여럿이 `true`일 수 있다")
            boolean isHighest
    ) {
    }

    /**
     * @param avgSleepScore       <b>28일 전체를 한 번에 평균낸 값이다 — 주 평균 4개의 평균이
     *                            아니다.</b> 주마다 결측 일수가 다르면 두 계산이 갈리는데(가중치가
     *                            달라진다), "28일 전부 결측이면 null"이라는 조건이 28일 단위로
     *                            걸려 있어 28일 단위로 계산한다. 전부 결측이면 {@code null}
     * @param avgDeepSleepMinutes 같은 이유로 <b>28일 전체를 한 번에 평균낸 값</b>이다 —
     *                            {@code avgSleepScore}와 같은 28일 단위 규칙. 세션이 없는
     *                            날은 제외하고, 28일 전부 결측이면 {@code null}
     */
    @Schema(description = "28일 전체 요약")
    public record Summary(

            @Schema(description = "28일 전체 평균 수면 점수. 전부 결측이면 `null`", nullable = true,
                    example = "61")
            Integer avgSleepScore,

            @Schema(description = "28일 전체 평균 깊은 수면(분). 전부 결측이면 `null`",
                    nullable = true, example = "118")
            Integer avgDeepSleepMinutes
    ) {
    }

}
