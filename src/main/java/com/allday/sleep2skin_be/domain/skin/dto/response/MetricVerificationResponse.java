package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지표 하나의 검증 결과 (HOME-07). <b>지표별로 따로 판정하고 적중률은 그 결과를 센다</b>(§10.2).
 *
 * @param difference {@code 예보 − 실측}. <b>부호가 뒤집히면 과소·과대가 서로 바뀐다</b> —
 *                   판정 구간이 이 방향으로 정의돼 있다
 */
@Schema(description = "지표 하나의 검증 결과")
public record MetricVerificationResponse(

        @Schema(description = "지표", example = "DARK_CIRCLE")
        SkinMetric metric,

        @Schema(description = "예보값")
        MetricScore forecast,

        @Schema(description = "셀피 실측값")
        MetricScore measured,

        @Schema(description = "`예보 − 실측`. 양수면 예보가 더 높았다", example = "6")
        int difference,

        @Schema(description = """
                판정 (§10.2)

                - `HIT` — ±5점 이내
                - `CLOSE` — ±6~15점
                - `UNDERESTIMATED` — −16점 이하. 점수를 낮게 예측 = **피부 위험을 과대평가**
                - `OVERESTIMATED` — +16점 이상. 점수를 높게 예측 = **피부 위험을 과소평가**

                **점수 축과 위험 축이 반대다.** 문구를 쓸 때 어느 축인지 밝히지 않으면 뜻이 뒤집힌다.
                """, example = "CLOSE")
        VerificationVerdict verdict
) {

    /**
     * <b>판정과 차이를 밖에서 받지 않고 여기서 계산한다.</b> 호출부가 둘을 따로 넘기면 부호와
     * 판정이 서로 어긋난 응답을 만들 수 있는데, 값 범위는 정상이라 아무 데도 안 걸린다.
     */
    public static MetricVerificationResponse of(SkinMetric metric, int forecast, int measured) {
        return new MetricVerificationResponse(
                metric,
                MetricScore.of(forecast),
                MetricScore.of(measured),
                forecast - measured,
                ScoringPolicy.verdict(forecast, measured));   // 확정값 (PRD §10.2)
    }

    public boolean isHit() {
        return verdict == VerificationVerdict.HIT;
    }

}
