package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore.UnavailableMetric;
import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 피부 예보 응답. 수면 업로드(HOME-03)와 예보 조회가 같은 모양을 쓴다.
 *
 * <p><b>등급은 저장하지 않고 여기서 매번 계산한다.</b> 컷오프를 조정하면 과거 데이터까지 일관되게
 * 반영된다 — 저장해뒀으면 같은 78점이 어제는 "보통" 오늘은 "안정"으로 보인다.
 *
 * <p><b>빈 지표는 {@code null}로 나가고 사유가 {@code unavailable}에 함께 실린다.</b>
 * {@code null}만 주면 앱이 어떤 문구를 띄울지 고를 수 없다. 에러가 아니라 정상 응답이다.
 */
@Schema(description = "피부 예보 (지표 3종)")
public record SkinForecastResponse(

        @Schema(description = "다크서클 회복 — 높을수록 맑음. **항상 값이 있다**")
        MetricScore darkCircle,

        @Schema(description = "혈색 — 높을수록 생기 있음. 산출하지 못했으면 `null`", nullable = true)
        MetricScore complexion,

        @Schema(description = "장벽 — 높을수록 튼튼함. 산출하지 못했으면 `null`", nullable = true)
        MetricScore barrier,

        @Schema(description = "산출하지 못한 지표와 사유. 전부 산출됐으면 빈 배열")
        List<UnavailableMetricResponse> unavailable
) {

    public static SkinForecastResponse of(SkinForecast forecast, List<UnavailableMetric> unavailable) {
        return new SkinForecastResponse(
                MetricScore.of(forecast.getDarkCircle()),
                MetricScore.of(forecast.getComplexion()),
                MetricScore.of(forecast.getBarrier()),
                unavailable.stream().map(UnavailableMetricResponse::from).toList());
    }

    @Schema(description = "지표 점수와 등급")
    public record MetricScore(

            @Schema(description = "0~100 점수. 높을수록 좋은 상태", example = "68")
            int score,

            @Schema(description = "등급 (0~25 위험 · 26~50 주의 · 51~75 보통 · 76~100 안정)",
                    example = "NORMAL")
            SkinGrade grade
    ) {
        /** 빈 지표는 {@code null}이다 — {@code 0}점으로 채우면 없는 위험을 경고하게 된다. */
        static MetricScore of(Integer score) {
            return score == null ? null : new MetricScore(score, ScoringPolicy.grade(score));
        }
    }

    @Schema(description = "산출하지 못한 지표")
    public record UnavailableMetricResponse(

            @Schema(description = "지표", example = "COMPLEXION")
            SkinMetric metric,

            @Schema(description = """
                    사유

                    - `MISSING_FEATURES` — 워치 미착용 (HRV·안정시 심박 없음)
                    - `INSUFFICIENT_HISTORY` — 취침 규칙성 이력 3일 미만
                    - `NO_SLEEP_STAGES` — 단계 합이 0이라 장벽 산출 불가
                    """, example = "MISSING_FEATURES")
            UnavailableReason reason
    ) {
        static UnavailableMetricResponse from(UnavailableMetric unavailable) {
            return new UnavailableMetricResponse(unavailable.metric(), unavailable.reason());
        }
    }

}
