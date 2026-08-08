package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 오늘의 피부 예보 조회 응답 (HOME-03).
 *
 * <p><b>모든 조회 API가 공유하는 {@code {status, message, 페이로드}} 형태다</b>(conventions.md §2).
 * 화면마다 다른 스키마가 생기지 않게 하는 것이 이 규칙의 핵심이며, 리포트·배너도 같은 모양을 쓴다.
 *
 * <p><b>빈 상태는 에러가 아니다.</b> 신규 사용자·앱을 아직 켜지 않은 사용자에게 일상적으로
 * 발생하므로 {@code 200}으로 내보낸다. 4xx로 내리면 경로 오타나 잘못된 {@code userId}와 섞여
 * 모니터링에서 신규 유입이 에러 급증으로 보인다.
 *
 * @param status   앱은 <b>문구가 아니라 이 값으로 분기한다</b>
 * @param message  사용자에게 그대로 보여줄 수 있는 한국어 문장. 정상이면 {@code null}
 * @param forecast 빈 상태면 {@code null}. <b>이 {@code null}은 그대로 직렬화된다</b> —
 *                 사라지는 것은 래퍼의 {@code error}뿐이다
 */
@Schema(description = "오늘의 피부 예보 조회 응답")
public record SkinForecastQueryResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "조회 기준일", example = "2026-08-07")
        LocalDate baseDate,

        @Schema(description = "예보. 그날 수면 데이터가 없으면 `null`", nullable = true)
        SkinForecastResponse forecast
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "수면 데이터가 없어 오늘은 예보가 없습니다.";

    public static SkinForecastQueryResponse of(SkinForecast forecast, boolean watchDataMissing) {
        return new SkinForecastQueryResponse(QueryStatus.AVAILABLE, null, forecast.getBaseDate(),
                SkinForecastResponse.of(forecast, watchDataMissing));
    }

    /**
     * 그날 수면 데이터가 없다. <b>지표가 비는 것과는 다른 층위다</b> — 이건 예보 자체가 없는 것이고,
     * {@code forecast.unavailable}은 예보는 있는데 일부 지표만 못 낸 것이다.
     */
    public static SkinForecastQueryResponse empty(LocalDate baseDate) {
        return new SkinForecastQueryResponse(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE,
                baseDate, null);
    }

}
