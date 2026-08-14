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
        Summary summary
) {

    public static MonthlyReportResponse insufficientData(LocalDate periodStart, LocalDate periodEnd) {
        return new MonthlyReportResponse(ReportPeriodStatus.INSUFFICIENT_DATA, periodStart, periodEnd,
                List.of(), null);
    }

    public static MonthlyReportResponse of(LocalDate periodStart, LocalDate periodEnd,
                                           List<WeekScore> weeks, Summary summary) {
        return new MonthlyReportResponse(ReportPeriodStatus.FULL, periodStart, periodEnd, weeks, summary);
    }

    /**
     * @param avgSleepScore 그 주 7일 중 수면 점수가 있는 날짜만의 평균, 반올림. 7일 모두
     *                      결측이면 {@code null}
     * @param isHighest     4주 중 {@code avgSleepScore}가 가장 높은 주(들)인가. {@code null}인
     *                      주는 비교 대상에서 빠지고, 최고값이 동점이면 해당하는 주 전부
     *                      {@code true}이며, 4주 모두 {@code null}이면 전부 {@code false}다
     *                      (비교할 값 자체가 없다)
     */
    @Schema(description = "주별 평균 점수")
    public record WeekScore(

            @Schema(description = "주 라벨 (`W1`이 가장 과거, `W4`가 `baseDate`를 포함한 최근 7일)",
                    example = "W4")
            String weekLabel,

            @Schema(description = "그 주 평균 수면 점수. 7일 모두 결측이면 `null`", nullable = true,
                    example = "70")
            Integer avgSleepScore,

            @Schema(description = "4주 중 최고 평균 점수인가. 동점이면 여럿이 `true`일 수 있다")
            boolean isHighest
    ) {
    }

    /**
     * @param avgSleepScore <b>28일 전체를 한 번에 평균낸 값이다 — 주 평균 4개의 평균이
     *                      아니다.</b> 주마다 결측 일수가 다르면 두 계산이 갈리는데(가중치가
     *                      달라진다), "28일 전부 결측이면 null"이라는 조건이 28일 단위로
     *                      걸려 있어 28일 단위로 계산한다. 전부 결측이면 {@code null}
     * @param hitRate       기간 내 검증된 지표 판정 중 {@code HIT} 비율(%), 반올림. 날짜 기준이
     *                      아니라 지표 기준이다. 판정이 없으면 {@code null}
     * @param verifiedDays  기간 내 검증한 날짜 수
     */
    @Schema(description = "28일 전체 요약")
    public record Summary(

            @Schema(description = "28일 전체 평균 수면 점수. 전부 결측이면 `null`", nullable = true,
                    example = "61")
            Integer avgSleepScore,

            @Schema(description = "기간 내 지표 판정의 적중률(%). 판정이 없으면 `null`",
                    nullable = true, example = "82")
            Integer hitRate,

            @Schema(description = "기간 내 검증한 날짜 수", example = "26")
            int verifiedDays
    ) {
    }

}
