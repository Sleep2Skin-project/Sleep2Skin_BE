package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoringPolicyTest {

    @Nested
    @DisplayName("피처 정규화 곡선 (§10.5)")
    class FeatureCurves {

        @ParameterizedTest(name = "총 수면 {0}분 → {1}점")
        @CsvSource({
                "300, 0", "360, 50", "420, 100", "480, 100", "540, 100",
                "630, 75", "720, 50", "900, 0",
                "240, 0",   // 0점 구간 아래는 클램프
                "960, 0"    // 과다 구간 끝 아래로 내려가지 않는다
        })
        @DisplayName("총 수면은 권장 범위를 벗어나면 양방향 모두 감점된다")
        void 총_수면_곡선(int minutes, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.TOTAL_SLEEP, minutes))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "각성 {0}회 → {1}점")
        @CsvSource({"0, 100", "1, 100", "2, 75", "3, 50", "5, 0", "9, 0"})
        @DisplayName("각성이 많을수록 점수가 내려간다 — 다크서클은 '회복된 정도'다")
        void 각성_횟수_곡선(int count, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.AWAKE_COUNT, count))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "깊은수면 {0}% → {1}점")
        @CsvSource({
                "5, 0", "9, 50", "13, 100", "18, 100", "23, 100",
                "28, 79.17",   // 과다 감점 — 부족 기울기의 1/3
                "35, 50", "47, 0", "60, 0"
        })
        @DisplayName("깊은수면은 분이 아니라 비율(%)로 채점한다")
        void 깊은수면_곡선(double percentage, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.DEEP_SLEEP, percentage))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "REM {0}% → {1}점")
        @CsvSource({"8, 0", "13, 50", "18, 100", "27, 100", "33, 80", "42, 50", "57, 0"})
        @DisplayName("REM도 비율(%)로 채점한다")
        void REM_곡선(double percentage, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.REM_SLEEP, percentage))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "취침 편차 {0}분 → {1}점")
        @CsvSource({"0, 100", "30, 100", "75, 50", "120, 0", "200, 0"})
        @DisplayName("취침 규칙성은 표준편차가 작을수록 높은 점수다")
        void 취침_규칙성_곡선(double standardDeviation, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.BEDTIME_REGULARITY, standardDeviation))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "HRV {0}ms → {1}점")
        @CsvSource({"10, 0", "15, 0", "37.5, 50", "60, 100", "90, 100"})
        @DisplayName("HRV는 높을수록 높은 점수다")
        void HRV_곡선(double milliseconds, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.HRV, milliseconds))
                    .isCloseTo(expected, within(0.01));
        }

        @ParameterizedTest(name = "안정시 심박 {0}bpm → {1}점")
        @CsvSource({"45, 100", "55, 100", "70, 50", "85, 0", "100, 0"})
        @DisplayName("안정시 심박은 낮을수록 높은 점수다")
        void 안정시_심박_곡선(double bpm, double expected) {
            assertThat(ScoringPolicy.featureScore(SleepFeature.RESTING_HEART_RATE, bpm))
                    .isCloseTo(expected, within(0.01));
        }
    }

    @Nested
    @DisplayName("매핑과 일반 가중치 (§10.3·§10.4)")
    class MappingAndWeights {

        @Test
        @DisplayName("피처 7종이 지표에 2·3·2로 나뉜다")
        void 피처는_7종이다() {
            assertThat(ScoringPolicy.featuresOf(SkinMetric.DARK_CIRCLE))
                    .containsExactly(SleepFeature.AWAKE_COUNT, SleepFeature.TOTAL_SLEEP);
            assertThat(ScoringPolicy.featuresOf(SkinMetric.COMPLEXION))
                    .containsExactly(SleepFeature.BEDTIME_REGULARITY, SleepFeature.HRV,
                            SleepFeature.RESTING_HEART_RATE);
            assertThat(ScoringPolicy.featuresOf(SkinMetric.BARRIER))
                    .containsExactly(SleepFeature.DEEP_SLEEP, SleepFeature.REM_SLEEP);
        }

        @Test
        @DisplayName("모든 피처가 정확히 한 지표에 붙어 있다 — personal_weight 7행과 1:1이다")
        void 모든_피처가_정확히_한_지표에_붙는다() {
            assertThat(java.util.Arrays.stream(SkinMetric.values())
                    .flatMap(metric -> ScoringPolicy.featuresOf(metric).stream())
                    .toList())
                    .hasSize(SleepFeature.values().length)
                    .containsExactlyInAnyOrder(SleepFeature.values());
        }

        @Test
        @DisplayName("일반 가중치는 지표 내 균등이다")
        void 일반_가중치는_지표_내_균등이다() {
            assertThat(ScoringPolicy.generalWeight(SkinMetric.DARK_CIRCLE)).isCloseTo(0.5, within(1e-9));
            assertThat(ScoringPolicy.generalWeight(SkinMetric.COMPLEXION)).isCloseTo(1.0 / 3, within(1e-9));
            assertThat(ScoringPolicy.generalWeight(SkinMetric.BARRIER)).isCloseTo(0.5, within(1e-9));
        }
    }

    @Nested
    @DisplayName("등급 컷오프 (§10.1)")
    class Grades {

        @ParameterizedTest(name = "{0}점 → {1}")
        @CsvSource({
                "0, RISK", "25, RISK",
                "26, CAUTION", "50, CAUTION",
                "51, NORMAL", "75, NORMAL",
                "76, STABLE", "100, STABLE"
        })
        @DisplayName("경계값 25·50·75에서 등급이 갈린다 — 숫자가 클수록 좋다")
        void 등급_경계(int score, SkinGrade expected) {
            assertThat(ScoringPolicy.grade(score)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("판정 오차 구간 (§10.2)")
    class Verdicts {

        @ParameterizedTest(name = "예보 {0} · 실측 {1} → {2}")
        @CsvSource({
                "70, 70, HIT",
                "75, 70, HIT",           // +5
                "65, 70, HIT",           // −5
                "76, 70, CLOSE",         // +6
                "64, 70, CLOSE",         // −6
                "85, 70, CLOSE",         // +15
                "55, 70, CLOSE",         // −15
                "86, 70, OVERESTIMATED", // +16 — 점수를 높게 냈다
                "54, 70, UNDERESTIMATED" // −16 — 점수를 낮게 냈다
        })
        @DisplayName("절댓값을 먼저 걸고 남은 것만 부호를 본다")
        void 판정_경계(int forecast, int measured, VerificationVerdict expected) {
            assertThat(ScoringPolicy.verdict(forecast, measured)).isEqualTo(expected);
        }

        @Test
        @DisplayName("과소예측은 점수 축 기준이다 — 점수를 낮게 낸 것이 피부 위험을 과대평가한 것이다")
        void 과소예측은_점수_축_기준이다() {
            // 예보 30점(위험) · 실측 80점(안정) — 위험을 크게 잡았지만 점수 축에서는 과소예측이다
            assertThat(ScoringPolicy.verdict(30, 80)).isEqualTo(VerificationVerdict.UNDERESTIMATED);
        }
    }

}
