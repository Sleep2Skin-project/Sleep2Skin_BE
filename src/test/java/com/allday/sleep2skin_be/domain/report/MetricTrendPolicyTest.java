package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.MetricTrend;
import com.allday.sleep2skin_be.domain.report.dto.VolatileDirection;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.MetricTrendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종합 리포트(REP-09~11) 지표별 추세 판정. {@code MetricTrendPolicy.classify}가 W1/W2/W3
 * 평균 세 값만으로 순수 함수로 동작하므로, 주간 평균 집계(결측 제외)는 여기서 다시 검증하지
 * 않는다 — {@link OverallReportServiceTest}가 그 배선을 본다.
 */
class MetricTrendPolicyTest {

    @Nested
    @DisplayName("W1 또는 W3 결측")
    class InsufficientSample {

        @Test
        @DisplayName("W1이 결측이면 INSUFFICIENT_SAMPLE이다")
        void W1이_결측이면_INSUFFICIENT_SAMPLE이다() {
            MetricTrendResult result = MetricTrendPolicy.classify(null, 60, 70);

            assertThat(result.trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
            assertThat(result.volatileDirection()).isNull();
            assertThat(result.w1Average()).isNull();
            assertThat(result.w3Average()).isEqualTo(70);
        }

        @Test
        @DisplayName("W3이 결측이면 INSUFFICIENT_SAMPLE이다")
        void W3이_결측이면_INSUFFICIENT_SAMPLE이다() {
            MetricTrendResult result = MetricTrendPolicy.classify(60, 60, null);

            assertThat(result.trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
            assertThat(result.volatileDirection()).isNull();
            assertThat(result.w1Average()).isEqualTo(60);
            assertThat(result.w3Average()).isNull();
        }

        @Test
        @DisplayName("W1·W3이 둘 다 결측이어도 INSUFFICIENT_SAMPLE이다")
        void 둘_다_결측이어도_INSUFFICIENT_SAMPLE이다() {
            MetricTrendResult result = MetricTrendPolicy.classify(null, null, null);

            assertThat(result.trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
        }
    }

    @Nested
    @DisplayName("VOLATILE — 방향이 정확히 반대일 때만")
    class VolatileJudgement {

        @Test
        @DisplayName("leg1>0·leg2<0이면 VOLATILE(RISE_THEN_FALL)이다")
        void 오르다_내리면_RISE_THEN_FALL이다() {
            // W1=50, W2=70(leg1=+20), W3=60(leg2=-10)
            MetricTrendResult result = MetricTrendPolicy.classify(50, 70, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.VOLATILE);
            assertThat(result.volatileDirection()).isEqualTo(VolatileDirection.RISE_THEN_FALL);
            assertThat(result.w1Average()).isEqualTo(50);
            assertThat(result.w3Average()).isEqualTo(60);
        }

        @Test
        @DisplayName("leg1<0·leg2>0이면 VOLATILE(FALL_THEN_RISE)이다")
        void 내리다_오르면_FALL_THEN_RISE이다() {
            // W1=70, W2=50(leg1=-20), W3=60(leg2=+10)
            MetricTrendResult result = MetricTrendPolicy.classify(70, 50, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.VOLATILE);
            assertThat(result.volatileDirection()).isEqualTo(VolatileDirection.FALL_THEN_RISE);
        }

        @Test
        @DisplayName("leg1이 정확히 0이면 VOLATILE로 새지 않는다 — 방향 일관으로 처리한다")
        void leg1이_0이면_VOLATILE이_아니다() {
            // W1=50, W2=50(leg1=0), W3=60(leg2=+10) → 반대 부호가 아니므로 total(=10>0)로 판정
            MetricTrendResult result = MetricTrendPolicy.classify(50, 50, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.IMPROVED);
            assertThat(result.volatileDirection()).isNull();
        }

        @Test
        @DisplayName("leg2가 정확히 0이면 VOLATILE로 새지 않는다 — 방향 일관으로 처리한다")
        void leg2가_0이면_VOLATILE이_아니다() {
            // W1=50, W2=70(leg1=+20), W3=70(leg2=0) → 반대 부호가 아니므로 total(=20>0)로 판정
            MetricTrendResult result = MetricTrendPolicy.classify(50, 70, 70);

            assertThat(result.trend()).isEqualTo(MetricTrend.IMPROVED);
            assertThat(result.volatileDirection()).isNull();
        }

        @Test
        @DisplayName("같은 방향으로 두 구간 다 오르면 VOLATILE이 아니라 IMPROVED다")
        void 같은_방향이면_VOLATILE이_아니다() {
            // W1=40, W2=50(leg1=+10), W3=60(leg2=+10) → 같은 부호라 방향 일관
            MetricTrendResult result = MetricTrendPolicy.classify(40, 50, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.IMPROVED);
            assertThat(result.volatileDirection()).isNull();
        }
    }

    @Nested
    @DisplayName("총 변화(IMPROVED/WORSENED/MAINTAINED)")
    class TotalChangeJudgement {

        @Test
        @DisplayName("W3-W1이 양수면 IMPROVED다")
        void 총_변화가_양수면_IMPROVED다() {
            MetricTrendResult result = MetricTrendPolicy.classify(48, 60, 79);

            assertThat(result.trend()).isEqualTo(MetricTrend.IMPROVED);
        }

        @Test
        @DisplayName("W3-W1이 음수면 WORSENED다")
        void 총_변화가_음수면_WORSENED다() {
            MetricTrendResult result = MetricTrendPolicy.classify(79, 60, 48);

            assertThat(result.trend()).isEqualTo(MetricTrend.WORSENED);
        }

        @Test
        @DisplayName("W3-W1이 정확히 0이면 MAINTAINED다")
        void 총_변화가_0이면_MAINTAINED다() {
            // W1=W2=W3=60 — leg1=leg2=0이라 VOLATILE 조건(반대 부호)에도 안 걸린다
            MetricTrendResult result = MetricTrendPolicy.classify(60, 60, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.MAINTAINED);
            assertThat(result.volatileDirection()).isNull();
        }
    }

    @Nested
    @DisplayName("W2만 결측")
    class MiddleWeekMissing {

        @Test
        @DisplayName("W2가 결측이면 VOLATILE 판정을 생략하고 total만으로 판정한다 — 상승")
        void W2가_없으면_total로만_IMPROVED를_판정한다() {
            MetricTrendResult result = MetricTrendPolicy.classify(48, null, 79);

            assertThat(result.trend()).isEqualTo(MetricTrend.IMPROVED);
            assertThat(result.volatileDirection()).isNull();
            assertThat(result.w1Average()).isEqualTo(48);
            assertThat(result.w3Average()).isEqualTo(79);
        }

        @Test
        @DisplayName("W2가 결측이면 VOLATILE 판정을 생략하고 total만으로 판정한다 — 하락")
        void W2가_없으면_total로만_WORSENED를_판정한다() {
            MetricTrendResult result = MetricTrendPolicy.classify(79, null, 48);

            assertThat(result.trend()).isEqualTo(MetricTrend.WORSENED);
        }

        @Test
        @DisplayName("W2가 결측이고 total이 0이면 MAINTAINED다")
        void W2가_없고_total이_0이면_MAINTAINED다() {
            MetricTrendResult result = MetricTrendPolicy.classify(60, null, 60);

            assertThat(result.trend()).isEqualTo(MetricTrend.MAINTAINED);
        }
    }

}
