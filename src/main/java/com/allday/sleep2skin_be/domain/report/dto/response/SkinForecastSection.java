package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Optional;

/**
 * 일간 리포트의 피부 예보(전일 대비) 섹션 (REP-05).
 *
 * <p>{@link SleepSummarySection}과 독립적으로 비어 있을 수 있어 같은
 * {@code {status, message, 페이로드}} 모양을 따로 갖는다. 빈 상태 사유는 예보 조회
 * API(HOME-03)와 같은 {@code NO_SLEEP_DATA}를 재사용한다 — 예보가 없는 것은 결국 그 예보를
 * 낳을 수면 데이터가 없어서다.
 */
@Schema(description = "피부 예보 섹션 (전일 대비)")
public record SkinForecastSection(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "다크서클 회복 — 오늘 값과 전일 대비. **항상 값이 있다**(예보가 있는 한)")
        MetricDiff darkCircle,

        @Schema(description = "혈색 — 오늘 값과 전일 대비. 그날 산출 못 했으면 `today`가 `null`")
        MetricDiff complexion,

        @Schema(description = "장벽 — 오늘 값과 전일 대비. 그날 산출 못 했으면 `today`가 `null`")
        MetricDiff barrier
) {

    private static final String NO_FORECAST_MESSAGE = "그날 피부 예보가 없습니다.";

    /**
     * @param yesterday 전날 예보. 없으면(첫 예보인 날 등) 모든 지표의 {@code diffFromYesterday}가
     *                  {@code null}이 된다
     */
    public static SkinForecastSection of(SkinForecast today, Optional<SkinForecast> yesterday) {
        return new SkinForecastSection(QueryStatus.AVAILABLE, null,
                MetricDiff.of(today.getDarkCircle(),
                        yesterday.map(SkinForecast::getDarkCircle).orElse(null)),
                MetricDiff.of(today.getComplexion(),
                        yesterday.map(SkinForecast::getComplexion).orElse(null)),
                MetricDiff.of(today.getBarrier(),
                        yesterday.map(SkinForecast::getBarrier).orElse(null)));
    }

    public static SkinForecastSection empty() {
        return new SkinForecastSection(QueryStatus.NO_SLEEP_DATA, NO_FORECAST_MESSAGE,
                MetricDiff.empty(), MetricDiff.empty(), MetricDiff.empty());
    }

    /**
     * @param today             오늘 값. 그날 이 지표를 산출하지 못했으면 {@code null}
     * @param diffFromYesterday {@code 오늘 − 어제}. 오늘 값이 없거나 전날 값이 없으면
     *                          {@code null}이다 — <b>어느 한쪽이 없는 차이는 "변화 없음"이 아니라
     *                          "비교 불가"</b>다. {@code 0}으로 채우면 존재하지 않는 비교가 생긴다
     */
    @Schema(description = "오늘 값과 전일 대비 증감")
    public record MetricDiff(

            @Schema(description = "오늘 값. 산출 못 했으면 `null`", nullable = true, example = "63")
            Integer today,

            @Schema(description = "전일 대비 증감(오늘 − 어제). 비교 불가면 `null`", nullable = true,
                    example = "7")
            Integer diffFromYesterday
    ) {
        static MetricDiff of(Integer todayValue, Integer yesterdayValue) {
            if (todayValue == null) {
                return new MetricDiff(null, null);
            }
            Integer diff = yesterdayValue == null ? null : todayValue - yesterdayValue;
            return new MetricDiff(todayValue, diff);
        }

        static MetricDiff empty() {
            return new MetricDiff(null, null);
        }
    }

}
