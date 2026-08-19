package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예보가 없어 대조하지 못한 지표.
 *
 * <p><b>실측값은 여기에도 있다.</b> LLM 은 예보와 무관하게 셋을 모두 산출하고
 * {@code skin_measurement} 도 셋 다 {@code NOT NULL} 이다 — 갈리는 것은 실측이 아니라 대조
 * 가능 여부다. 사진을 못 읽은 것이 아니라 비교 대상이 없는 것이라, 앱은 실측값을 그대로 보여줄
 * 수 있다.
 *
 * <p>{@code reason} 은 예보 조회 API 의 {@code unavailable[].reason} 과 <b>같은 집합이고 같은
 * 코드에서 나온다</b>({@link ScoringPolicy#reasonFor}). 두 화면이 같은 상황에 다른 문구를 띄우면
 * 사용자는 서버가 헷갈린다고 읽는다.
 */
@Schema(description = "예보가 없어 대조하지 못한 지표")
public record SkippedMetricResponse(

        @Schema(description = "지표", example = "COMPLEXION")
        SkinMetric metric,

        @Schema(description = "셀피 실측값. **대조만 못 했을 뿐 실측은 있다**")
        MetricScore measured,

        @Schema(description = """
                예보가 없었던 사유

                - `MISSING_FEATURES` — 워치 미착용 (HRV·안정시 심박 없음)
                - `INSUFFICIENT_HISTORY` — 취침 규칙성 이력 3일 미만
                - `NO_SLEEP_STAGES` — 단계 합이 0이라 피부 장벽 산출 불가
                """, example = "MISSING_FEATURES")
        UnavailableReason reason
) {

    public static SkippedMetricResponse of(SkinMetric metric, int measured, boolean watchDataMissing) {
        return new SkippedMetricResponse(metric, MetricScore.of(measured),
                ScoringPolicy.reasonFor(metric, watchDataMissing));
    }

}
