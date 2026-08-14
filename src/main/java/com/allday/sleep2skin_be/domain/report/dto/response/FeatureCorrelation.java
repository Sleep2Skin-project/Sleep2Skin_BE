package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수면 피처 하나와 피부 지표 하나 사이의 상관 강도 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>주간·월간 리포트가 같은 모양을 공유한다 — 계산은 {@code CorrelationCalculator} 하나가
 * 하고 두 서비스가 그 결과를 그대로 응답에 싣는다.
 *
 * @param sleepFeature       수면 피처 (§10.3의 7종과 동일)
 * @param featureLabel       표시용 한국어 이름
 * @param skinMetric         이 피처가 매핑되는 피부 지표. 매핑은 예보 산출과 같다(§10.3)
 * @param metricLabel        표시용 한국어 이름
 * @param strength           상관 강도. {@code insufficientSample}이 {@code true}면 {@code null}
 * @param sampleSize         상관계수 계산에 실제로 쓰인 유효 표본 수(그 피처 값이 결측이 아닌
 *                            검증일 수)
 * @param insufficientSample 표본이 부족해(§{@code CorrelationPolicy.MIN_SAMPLE_SIZE} 미만)
 *                            상관계수를 계산하지 않았는가
 */
@Schema(description = "수면 피처-피부 지표 상관 강도")
public record FeatureCorrelation(

        @Schema(description = "수면 피처", example = "AWAKE_COUNT")
        SleepFeature sleepFeature,

        @Schema(description = "수면 피처 표시명", example = "야간 각성")
        String featureLabel,

        @Schema(description = "매핑되는 피부 지표", example = "DARK_CIRCLE")
        SkinMetric skinMetric,

        @Schema(description = "피부 지표 표시명", example = "다크서클")
        String metricLabel,

        @Schema(description = "상관 강도. 표본 부족이면 `null`", nullable = true, example = "VERY_STRONG")
        CorrelationStrength strength,

        @Schema(description = "상관계수 계산에 쓰인 유효 표본 수", example = "6")
        int sampleSize,

        @Schema(description = "표본 부족으로 상관계수를 계산하지 않았는가", example = "false")
        boolean insufficientSample
) {
}
