package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;

/**
 * 상관 강도를 {@code skinMetric} 기준으로 묶은 그룹 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>{@link com.allday.sleep2skin_be.domain.report.CorrelationCalculator}가 내는 7개 flat
 * 배열을 응답 DTO 레벨에서만 재배열한다 — 상관계수 산출·정렬 로직은 건드리지 않는다. 그룹
 * 안의 순서와 개수는 그 계산 결과를 필터링한 것뿐이라, 원래의 "상관계수 절댓값 내림차순,
 * 표본 부족은 뒤로" 정렬이 그룹 안에서도 그대로 유지된다.
 */
@Schema(description = "피부 지표별 상관 강도 그룹")
public record CorrelationGroup(

        @Schema(description = "피부 지표", example = "DARK_CIRCLE")
        SkinMetric skinMetric,

        @Schema(description = "이 지표에 매핑된 수면 피처의 상관 강도")
        List<FeatureCorrelation> correlations
) {

    /**
     * {@code SkinMetric.values()} 순서(`DARK_CIRCLE`·`COMPLEXION`·`BARRIER`)로 3그룹을 만든다.
     * 항상 3그룹 전부 반환한다 — 매핑되는 피처가 없어도 빈 배열로 포함된다.
     */
    public static List<CorrelationGroup> groupBySkinMetric(List<FeatureCorrelation> correlations) {
        return Arrays.stream(SkinMetric.values())
                .map(metric -> new CorrelationGroup(metric,
                        correlations.stream().filter(c -> c.skinMetric() == metric).toList()))
                .toList();
    }

}
