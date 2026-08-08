package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore.UnavailableMetric;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkinScoringEngineTest {

    private final SkinScoringEngine engine = new SkinScoringEngine();

    /**
     * 기준 픽스처 — 부분점수가 전부 딱 떨어지게 잡았다.
     *
     * <pre>
     * AWAKE_COUNT 3회      → 50      TOTAL_SLEEP 480분 → 100   ⇒ DARK_CIRCLE 75
     * DEEP 9%              → 50      REM 15%           →  70   ⇒ BARRIER     60
     * REGULARITY 75분      → 50      HRV 37.5ms        →  50
     * RESTING_HEART_RATE 70bpm → 50                          ⇒ COMPLEXION 50
     * </pre>
     */
    private static Fixture baseline() {
        return new Fixture();
    }

    @Nested
    @DisplayName("가중합과 재정규화 (§10.4)")
    class WeightedSum {

        @Test
        @DisplayName("개인 가중치가 없으면 지표점수는 부분점수의 평균이다")
        void 개인_가중치가_없으면_평균이다() {
            SkinForecastScore score = engine.score(baseline().build());

            assertThat(score.scoreOf(SkinMetric.DARK_CIRCLE)).isEqualTo(75);
            assertThat(score.scoreOf(SkinMetric.BARRIER)).isEqualTo(60);
            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isEqualTo(50);
            assertThat(score.unavailable()).isEmpty();
        }

        @Test
        @DisplayName("개인 가중치를 모두 같은 배수로 올리면 점수가 그대로다 — 재정규화가 있다는 증거")
        void 같은_배수는_점수를_바꾸지_않는다() {
            SkinForecastScore doubled = engine.score(baseline()
                    .weight(SleepFeature.AWAKE_COUNT, "2.0")
                    .weight(SleepFeature.TOTAL_SLEEP, "2.0")
                    .build());

            assertThat(doubled.scoreOf(SkinMetric.DARK_CIRCLE)).isEqualTo(75);
        }

        @Test
        @DisplayName("한 피처의 가중치를 올리면 그 피처 쪽으로 점수가 끌린다 — 상대 비중을 학습한다")
        void 가중치가_높은_피처_쪽으로_끌린다() {
            // 각성(50점) 가중치를 2배로 → w' = 2/3 : 1/3 → 2/3×50 + 1/3×100 = 66.7
            SkinForecastScore score = engine.score(baseline()
                    .weight(SleepFeature.AWAKE_COUNT, "2.0")
                    .build());

            assertThat(score.scoreOf(SkinMetric.DARK_CIRCLE)).isEqualTo(67);
        }

        @Test
        @DisplayName("가중치를 클램프 양 끝으로 밀어도 점수가 0~100 밖으로 나가지 않는다")
        void 점수는_항상_0에서_100_사이다() {
            SkinForecastScore score = engine.score(baseline()
                    .weight(SleepFeature.AWAKE_COUNT, "2.0")
                    .weight(SleepFeature.TOTAL_SLEEP, "0.5")
                    .build());

            assertThat(score.scoreOf(SkinMetric.DARK_CIRCLE)).isBetween(0, 100);
        }
    }

    @Nested
    @DisplayName("비율 분모 (§10.5)")
    class StageRatio {

        @Test
        @DisplayName("깊은수면·REM의 분모는 총 수면이 아니라 단계 합이다")
        void 분모는_단계_합이다() {
            // 420분을 잤지만 단계가 잡힌 건 100분 — 나머지는 UNSPECIFIED다
            SkinForecastScore score = engine.score(baseline()
                    .totalSleep(420)
                    .stages(18, 22, 60)   // deep 18% · rem 22% → 둘 다 정상 범위(100점)
                    .build());

            // 총 수면을 분모로 썼다면 deep 4.3% · rem 5.2% → 둘 다 0점 → 장벽 0점("위험")이 나간다
            assertThat(score.scoreOf(SkinMetric.BARRIER)).isEqualTo(100);
        }

        @Test
        @DisplayName("단계 합이 같으면 총 수면이 달라져도 장벽 점수가 변하지 않는다")
        void 총_수면은_장벽에_영향을_주지_않는다() {
            // 분모가 총 수면이라면 이 둘의 장벽 점수가 달라야 한다
            int exact = engine.score(baseline().totalSleep(100).stages(18, 22, 60).build())
                    .scoreOf(SkinMetric.BARRIER);
            int withUnspecified = engine.score(baseline().totalSleep(420).stages(18, 22, 60).build())
                    .scoreOf(SkinMetric.BARRIER);

            assertThat(withUnspecified).isEqualTo(exact);
        }
    }

    @Nested
    @DisplayName("결측 처리 (§10.6)")
    class MissingFeatures {

        @Test
        @DisplayName("워치를 안 찬 밤은 혈색이 취침 규칙성 하나로 재정규화된다")
        void 워치_결측은_규칙성으로_재정규화된다() {
            SkinForecastScore score = engine.score(baseline()
                    .bedtimeRegularity(30.0)   // 100점
                    .noWatch()
                    .build());

            // 대입했다면 (100 + 기본값 + 기본값)/3이 됐을 값이 100이 된다
            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isEqualTo(100);
            assertThat(score.unavailable()).isEmpty();
        }

        @Test
        @DisplayName("취침 이력이 3일 미만이면 혈색이 워치 피처 둘로 재정규화된다")
        void 이력_부족은_워치_피처로_재정규화된다() {
            SkinForecastScore score = engine.score(baseline()
                    .bedtimeRegularity(null)
                    .hrv("60")               // 100점
                    .restingHeartRate(55)    // 100점
                    .build());

            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isEqualTo(100);
            assertThat(score.unavailable()).isEmpty();
        }

        @Test
        @DisplayName("결측 피처는 참여 피처 목록에서 빠진다 — 그 밤의 오차를 HRV 탓으로 돌리지 않는다")
        void 결측_피처는_참여_목록에서_빠진다() {
            SkinForecastScore score = engine.score(baseline().noWatch().build());

            assertThat(score.scoredFeatures())
                    .doesNotContain(SleepFeature.HRV, SleepFeature.RESTING_HEART_RATE)
                    .contains(SleepFeature.BEDTIME_REGULARITY, SleepFeature.AWAKE_COUNT,
                            SleepFeature.TOTAL_SLEEP, SleepFeature.DEEP_SLEEP, SleepFeature.REM_SLEEP);
        }

        @Test
        @DisplayName("피처가 하나만 남아도 그 지표는 정상 발급된다")
        void 피처가_하나만_남아도_발급된다() {
            SkinForecastScore score = engine.score(baseline()
                    .noWatch()
                    .bedtimeRegularity(75.0)   // 50점
                    .build());

            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("빈 상태 (§10.6)")
    class Unavailable {

        @Test
        @DisplayName("혈색 피처가 전부 없으면 혈색만 비고 나머지 둘은 정상 발급된다")
        void 혈색_전멸은_혈색만_비운다() {
            SkinForecastScore score = engine.score(baseline()
                    .noWatch()
                    .bedtimeRegularity(null)
                    .build());

            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isNull();
            assertThat(score.metricScores()).doesNotContainKey(SkinMetric.COMPLEXION);
            assertThat(score.unavailable())
                    .containsExactly(new UnavailableMetric(SkinMetric.COMPLEXION,
                            UnavailableReason.MISSING_FEATURES));

            assertThat(score.scoreOf(SkinMetric.DARK_CIRCLE)).isEqualTo(75);
            assertThat(score.scoreOf(SkinMetric.BARRIER)).isEqualTo(60);
        }

        @Test
        @DisplayName("워치를 찼으면 이력이 부족해도 혈색이 비지 않는다")
        void 워치가_있으면_혈색이_비지_않는다() {
            SkinForecastScore score = engine.score(baseline()
                    .bedtimeRegularity(null)
                    .hrv("40")
                    .restingHeartRate(65)
                    .build());

            assertThat(score.unavailable()).isEmpty();
            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isNotNull();
        }

        @Test
        @DisplayName("혈색이 비는 밤은 언제나 워치 미착용이 함께다 — INSUFFICIENT_HISTORY는 현재 매핑에서 나오지 않는다")
        void 혈색_빈_상태의_사유는_항상_워치_미착용이다() {
            // 혈색이 비려면 피처 3개가 전부 없어야 하는데, HRV·안정시 심박이 하나라도 있으면
            // 그 순간 혈색은 산출된다. 즉 "혈색이 빈다 ⇒ 워치가 없다"가 항상 참이다.
            // api.md는 INSUFFICIENT_HISTORY를 빈 상태 사유로 적어뒀지만 혈색에서는 도달할 수 없다
            assertThat(engine.score(baseline().noWatch().bedtimeRegularity(null).build()).unavailable())
                    .extracting(UnavailableMetric::reason)
                    .containsOnly(UnavailableReason.MISSING_FEATURES);

            assertThat(engine.score(baseline().hrv("40").restingHeartRate(null)
                    .bedtimeRegularity(null).build()).unavailable()).isEmpty();
            assertThat(engine.score(baseline().noWatch().restingHeartRate(65)
                    .bedtimeRegularity(null).build()).unavailable()).isEmpty();
        }

        @Test
        @DisplayName("단계 합이 0이면 장벽만 비운다 — 0점으로 발급하면 없는 위험을 경고하게 된다")
        void 단계_합_0은_장벽만_비운다() {
            SkinForecastScore score = engine.score(baseline()
                    .totalSleep(450)
                    .stages(0, 0, 0)
                    .build());

            assertThat(score.scoreOf(SkinMetric.BARRIER)).isNull();
            assertThat(score.unavailable())
                    .containsExactly(new UnavailableMetric(SkinMetric.BARRIER,
                            UnavailableReason.NO_SLEEP_STAGES));

            assertThat(score.scoreOf(SkinMetric.DARK_CIRCLE)).isNotNull();
            assertThat(score.scoreOf(SkinMetric.COMPLEXION)).isEqualTo(50);
        }

        @Test
        @DisplayName("빈 지표의 피처는 참여 목록에 들어오지 않는다")
        void 빈_지표의_피처는_참여하지_않는다() {
            SkinForecastScore score = engine.score(baseline().stages(0, 0, 0).build());

            assertThat(score.scoredFeatures())
                    .doesNotContain(SleepFeature.DEEP_SLEEP, SleepFeature.REM_SLEEP);
        }

        @Test
        @DisplayName("혈색과 장벽이 동시에 비어도 다크서클은 발급된다")
        void 두_지표가_동시에_비어도_다크서클은_남는다() {
            SkinForecastScore score = engine.score(baseline()
                    .noWatch()
                    .bedtimeRegularity(null)
                    .stages(0, 0, 0)
                    .build());

            assertThat(score.metricScores()).containsOnlyKeys(SkinMetric.DARK_CIRCLE);
            assertThat(score.unavailable()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("지표 방향")
    class MetricDirection {

        @Test
        @DisplayName("각성이 많은 밤일수록 다크서클 점수가 낮다 — '심한 정도'가 아니라 '회복된 정도'다")
        void 각성이_많으면_다크서클이_낮다() {
            int quiet = engine.score(baseline().awakeCount(0).build()).scoreOf(SkinMetric.DARK_CIRCLE);
            int broken = engine.score(baseline().awakeCount(5).build()).scoreOf(SkinMetric.DARK_CIRCLE);

            assertThat(broken).isLessThan(quiet);
        }

        @Test
        @DisplayName("깊은수면 비율이 정상 범위에 가까울수록 장벽 점수가 높다")
        void 깊은수면이_정상_범위면_장벽이_높다() {
            int shallow = engine.score(baseline().stages(5, 15, 80).build()).scoreOf(SkinMetric.BARRIER);
            int healthy = engine.score(baseline().stages(18, 22, 60).build()).scoreOf(SkinMetric.BARRIER);

            assertThat(healthy).isGreaterThan(shallow);
        }
    }

    // ===== 픽스처 =====

    private static final class Fixture {

        private int awakeCount = 3;
        private int totalSleepMinutes = 480;
        private int deep = 9;
        private int rem = 15;
        private int core = 76;
        private Double bedtimeRegularitySd = 75.0;
        private BigDecimal hrv = new BigDecimal("37.5");
        private Integer restingHeartRate = 70;
        private final Map<SleepFeature, BigDecimal> weights = new EnumMap<>(SleepFeature.class);

        Fixture awakeCount(int value) {
            this.awakeCount = value;
            return this;
        }

        Fixture totalSleep(int minutes) {
            this.totalSleepMinutes = minutes;
            return this;
        }

        Fixture stages(int deep, int rem, int core) {
            this.deep = deep;
            this.rem = rem;
            this.core = core;
            return this;
        }

        Fixture bedtimeRegularity(Double standardDeviation) {
            this.bedtimeRegularitySd = standardDeviation;
            return this;
        }

        Fixture hrv(String value) {
            this.hrv = new BigDecimal(value);
            return this;
        }

        Fixture restingHeartRate(Integer value) {
            this.restingHeartRate = value;
            return this;
        }

        Fixture noWatch() {
            this.hrv = null;
            this.restingHeartRate = null;
            return this;
        }

        Fixture weight(SleepFeature feature, String multiplier) {
            weights.put(feature, new BigDecimal(multiplier));
            return this;
        }

        ScoringCommand build() {
            return new ScoringCommand(awakeCount, totalSleepMinutes, deep, rem, deep + rem + core,
                    bedtimeRegularitySd, hrv, restingHeartRate, Map.copyOf(weights));
        }
    }

}
