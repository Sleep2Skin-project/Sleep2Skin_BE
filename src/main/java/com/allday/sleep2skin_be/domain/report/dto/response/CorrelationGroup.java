package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;

/**
 * 상관 강도를 {@code skinMetric} 기준으로 묶은 그룹 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>{@link com.allday.sleep2skin_be.domain.report.CorrelationCalculator}가 내는 7개 flat
 * 배열에서 그룹당 대표 1개만 뽑는다(2026-08-19, 프론트 요청) — 상관계수 산출·정렬 로직은
 * 건드리지 않는다. 대표는 그 그룹에서 정렬 순서상 맨 앞(상관계수 절댓값이 가장 크고, 동률이면
 * {@code SleepFeature} 선언 순서가 가장 앞선) 항목이다.
 *
 * <p><b>{@code insufficientSample: true}뿐인 그룹도 그대로 대표를 낸다</b> — 표본이 부족해도
 * "그 지표에서 가장 유력한 피처가 무엇인지"는 여전히 의미가 있고, 숨기면 프론트가 그 지표를
 * 아예 응답에 없는 것으로 다뤄야 한다.
 */
@Schema(description = "피부 지표별 상관 강도 대표값")
public record CorrelationGroup(

        @Schema(description = "피부 지표", example = "DARK_CIRCLE")
        SkinMetric skinMetric,

        @Schema(description = "이 지표에 매핑된 수면 피처 중 상관 강도 대표 1개 "
                + "(절댓값 내림차순 1위, 동률이면 SleepFeature 선언 순서가 가장 앞선 것)")
        FeatureCorrelation topCorrelation
) {

    /**
     * {@code SkinMetric.values()} 순서(`DARK_CIRCLE`·`COMPLEXION`·`BARRIER`)로 3그룹을 만들고,
     * 그룹마다 대표 1개만 담는다. 항상 3그룹 전부 반환한다.
     *
     * <p>{@code FEATURE_METRIC_PAIRS}가 §10.3의 7쌍 고정이라 지표 하나당 최소 2개 피처가
     * 매핑돼 있다 — {@code correlations}가 {@code CorrelationCalculator.calculate}의 결과
     * 그대로라면 {@code findFirst()}가 비는 상황은 정상 흐름에서 일어나지 않는다.
     */
    public static List<CorrelationGroup> groupBySkinMetric(List<FeatureCorrelation> correlations) {
        return Arrays.stream(SkinMetric.values())
                .map(metric -> new CorrelationGroup(metric,
                        correlations.stream()
                                .filter(c -> c.skinMetric() == metric)
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                        "지표에 매핑된 상관 강도가 없다 — §10.3 FEATURE_METRIC_PAIRS와 "
                                                + "어긋났다 skinMetric=" + metric))))
                .toList();
    }

}
