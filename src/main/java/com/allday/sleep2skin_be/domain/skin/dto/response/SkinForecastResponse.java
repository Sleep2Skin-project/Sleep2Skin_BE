package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
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

    /**
     * <b>빈 지표와 사유를 저장된 {@code null}에서 도출한다.</b> 예보에 사유 컬럼이 없기 때문이기도
     * 하지만, 더 중요한 건 <b>업로드 경로와 조회 경로가 같은 답을 내는 것이 구조로 보장</b>된다는
     * 점이다 — 사유를 밖에서 받으면 호출부마다 판정이 갈릴 수 있다.
     *
     * @param watchDataMissing 그날 {@code HRV}와 안정시 심박이 <b>둘 다</b> 없었는가.
     *                         {@code COMPLEXION}이 빈 사유를 가르는 유일한 입력이다
     */
    public static SkinForecastResponse of(SkinForecast forecast, boolean watchDataMissing) {
        List<UnavailableMetricResponse> unavailable = new ArrayList<>();
        if (forecast.getComplexion() == null) {
            unavailable.add(UnavailableMetricResponse.of(SkinMetric.COMPLEXION, watchDataMissing));
        }
        if (forecast.getBarrier() == null) {
            unavailable.add(UnavailableMetricResponse.of(SkinMetric.BARRIER, watchDataMissing));
        }

        return new SkinForecastResponse(
                MetricScore.of(forecast.getDarkCircle()),
                MetricScore.of(forecast.getComplexion()),
                MetricScore.of(forecast.getBarrier()),
                List.copyOf(unavailable));
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
        static UnavailableMetricResponse of(SkinMetric metric, boolean watchDataMissing) {
            return new UnavailableMetricResponse(metric,
                    ScoringPolicy.reasonFor(metric, watchDataMissing));
        }
    }

}
