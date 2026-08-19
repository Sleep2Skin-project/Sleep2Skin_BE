package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 적중률 — 개수 비율이 아니라 정확도 평균이다.
 *
 * <p><b>기대값은 손으로 계산한 상수다.</b> {@code ScoringPolicy.accuracy()}로 만들면 곡선이 바뀌어도
 * 항상 통과해서 아무것도 지키지 못한다.
 */
class HitRateCalculatorTest {

    private final HitRateCalculator calculator = new HitRateCalculator();

    /** 예보 − 실측이 {@code difference}가 되도록 만든다. 정확도는 부호를 보지 않는다. */
    private MetricVerificationResponse verification(SkinMetric metric, int difference) {
        return MetricVerificationResponse.of(metric, 70 + difference, 70);
    }

    @Test
    @DisplayName("판정된 지표들의 정확도 평균이다 — 0·33·67·100의 계단이 아니다")
    void 정확도_평균이다() {
        // 오차 2 · 6 · 11 → 정확도 93.14 · 82.80 · 73.41 → 평균 83.12
        int hitRate = calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 2),
                verification(SkinMetric.COMPLEXION, -6),
                verification(SkinMetric.BARRIER, 11)));

        assertThat(hitRate).isEqualTo(83);
    }

    @Test
    @DisplayName("적중이 하나도 없어도 0이 아니다 — 오차 6점과 60점이 뭉개지지 않는다")
    void 적중이_없어도_0이_아니다() {
        // 셋 다 CLOSE 이상(적중 0건)이지만 오차 크기에 따라 갈린다
        int 근접한_날 = calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 6),
                verification(SkinMetric.COMPLEXION, 6),
                verification(SkinMetric.BARRIER, 6)));
        int 크게_빗나간_날 = calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 60),
                verification(SkinMetric.COMPLEXION, 60),
                verification(SkinMetric.BARRIER, 60)));

        assertThat(근접한_날).isEqualTo(83);
        assertThat(크게_빗나간_날).isEqualTo(34);
    }

    @Test
    @DisplayName("분모는 판정한 지표 수다 — 예보가 없던 지표는 애초에 들어오지 않는다")
    void 분모는_판정한_지표_수다() {
        // 오차 0 · 20 → 정확도 100 · 61.49 → 평균 80.75. 3으로 나눴다면 54가 된다
        int hitRate = calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 0),
                verification(SkinMetric.BARRIER, 20)));

        assertThat(hitRate).isEqualTo(81);
    }

    @Test
    @DisplayName("100은 세 지표가 전부 정확히 맞아야 나온다")
    void 전부_정확해야_100이다() {
        assertThat(calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 0),
                verification(SkinMetric.COMPLEXION, 0),
                verification(SkinMetric.BARRIER, 0)))).isEqualTo(100);

        // 한 지표가 1점만 빗나가도 100이 아니다
        assertThat(calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 0),
                verification(SkinMetric.COMPLEXION, 0),
                verification(SkinMetric.BARRIER, 1)))).isEqualTo(99);
    }

    @Test
    @DisplayName("반올림은 평균을 낸 뒤 한 번만 한다")
    void 반올림은_마지막에_한_번이다() {
        // 정확도 100 · 96.38 · 96.38 → 평균 97.59 → 98.
        // 지표마다 먼저 반올림하면 (100 + 96 + 96) / 3 = 97 이 되어 갈린다
        int hitRate = calculator.calculate(List.of(
                verification(SkinMetric.DARK_CIRCLE, 0),
                verification(SkinMetric.COMPLEXION, 1),
                verification(SkinMetric.BARRIER, -1)));

        assertThat(hitRate).isEqualTo(98);
    }

}
