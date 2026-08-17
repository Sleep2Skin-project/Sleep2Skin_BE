package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriagePolicyTest {

    @Nested
    @DisplayName("수면 점수 추세 판정")
    class SleepTrendClassification {

        @Test
        @DisplayName("전체 유효 표본이 5개 미만이면 INSUFFICIENT_DATA다")
        void 전체_표본이_부족하면_결측이다() {
            // 전반부 2개 + 후반부 2개 = 4개 < 5
            List<Integer> first = scores(60, 60, null, null, null, null, null, null, null, null);
            List<Integer> second = scores(60, 60, null, null, null, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second))
                    .isEqualTo(SleepTrend.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("전체 표본은 충분해도 전반부가 통째로 결측이면 INSUFFICIENT_DATA다")
        void 전반부가_전부_결측이면_결측이다() {
            List<Integer> first = allNull();
            List<Integer> second = scores(60, 60, 60, 60, 60, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second))
                    .isEqualTo(SleepTrend.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("전체 표본은 충분해도 후반부가 통째로 결측이면 INSUFFICIENT_DATA다")
        void 후반부가_전부_결측이면_결측이다() {
            List<Integer> first = scores(60, 60, 60, 60, 60, null, null, null, null, null);
            List<Integer> second = allNull();

            assertThat(TriagePolicy.classifySleepTrend(first, second))
                    .isEqualTo(SleepTrend.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("표본이 충분하고 표준편차가 15 이상이면 VOLATILE이다")
        void 표준편차가_크면_VOLATILE이다() {
            // 값이 20~90 사이로 크게 흔들린다 — 전반부·후반부 평균 차이와 무관하게 VOLATILE이 우선한다
            List<Integer> first = scores(20, 90, 20, 90, 20, null, null, null, null, null);
            List<Integer> second = scores(20, 90, 20, 90, 20, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second)).isEqualTo(SleepTrend.VOLATILE);
        }

        @Test
        @DisplayName("변동은 작고 후반부가 전반부보다 5점 이상 높으면 RISING이다")
        void 상승하면_RISING이다() {
            List<Integer> first = scores(50, 50, 50, 50, 50, null, null, null, null, null);
            List<Integer> second = scores(60, 60, 60, 60, 60, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second)).isEqualTo(SleepTrend.RISING);
        }

        @Test
        @DisplayName("변동은 작고 후반부가 전반부보다 5점 이상 낮으면 FALLING이다")
        void 하락하면_FALLING이다() {
            List<Integer> first = scores(60, 60, 60, 60, 60, null, null, null, null, null);
            List<Integer> second = scores(50, 50, 50, 50, 50, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second)).isEqualTo(SleepTrend.FALLING);
        }

        @Test
        @DisplayName("변동도 작고 차이도 5점 미만이면 STABLE이다")
        void 변화가_작으면_STABLE이다() {
            List<Integer> first = scores(60, 61, 60, 61, 60, null, null, null, null, null);
            List<Integer> second = scores(62, 61, 62, 61, 62, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second)).isEqualTo(SleepTrend.STABLE);
        }

        @Test
        @DisplayName("경계값 — 차이가 정확히 5점이면 RISING이다")
        void 경계값_5점은_RISING이다() {
            List<Integer> first = scores(50, 50, 50, 50, 50, null, null, null, null, null);
            List<Integer> second = scores(55, 55, 55, 55, 55, null, null, null, null, null);

            assertThat(TriagePolicy.classifySleepTrend(first, second)).isEqualTo(SleepTrend.RISING);
        }

        private List<Integer> scores(Integer... values) {
            return Arrays.asList(values);
        }

        private List<Integer> allNull() {
            return Collections.nCopies(10, null);
        }
    }

    @Nested
    @DisplayName("피부 지표 정체 판정")
    class StagnantMetricJudgement {

        @Test
        @DisplayName("유효 표본이 5개 미만이면 정체가 아니다 — 판정 불가")
        void 표본이_부족하면_정체가_아니다() {
            assertThat(TriagePolicy.isStagnantMetric(4, 30, 28, 32)).isFalse();
        }

        @Test
        @DisplayName("표본이 5개면 판정 대상이다 — 경계값 포함")
        void 표본_5개는_판정_대상이다() {
            assertThat(TriagePolicy.isStagnantMetric(5, 30, 28, 32)).isTrue();
        }

        @Test
        @DisplayName("평균이 50점 이상이면 변동폭이 작아도 정체가 아니다")
        void 평균이_좋으면_정체가_아니다() {
            assertThat(TriagePolicy.isStagnantMetric(10, 50, 48, 52)).isFalse();
        }

        @Test
        @DisplayName("평균이 50점 미만이어도 변동폭이 5점을 넘으면 정체가 아니다")
        void 변동폭이_크면_정체가_아니다() {
            assertThat(TriagePolicy.isStagnantMetric(10, 30, 20, 40)).isFalse();
        }

        @Test
        @DisplayName("평균이 50점 미만이고 변동폭이 5점 이하면 정체다")
        void 평균도_낮고_변동폭도_작으면_정체다() {
            assertThat(TriagePolicy.isStagnantMetric(10, 30, 28, 33)).isTrue();
        }

        @Test
        @DisplayName("경계값 — 평균이 정확히 50점이면 정체가 아니다")
        void 평균_경계값_50점은_정체가_아니다() {
            assertThat(TriagePolicy.isStagnantMetric(10, 50, 47, 52)).isFalse();
        }

        @Test
        @DisplayName("경계값 — 변동폭이 정확히 5점이면 정체다")
        void 변동폭_경계값_5점은_정체다() {
            assertThat(TriagePolicy.isStagnantMetric(10, 30, 28, 33)).isTrue();
        }
    }

}
