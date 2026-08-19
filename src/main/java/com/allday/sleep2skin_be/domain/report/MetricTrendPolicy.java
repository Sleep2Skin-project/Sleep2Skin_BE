package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.MetricTrend;
import com.allday.sleep2skin_be.domain.report.dto.VolatileDirection;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.MetricTrendResult;

/**
 * 종합 리포트(REP-09~11) 지표별 추세 판정 기준.
 *
 * <p>2026-08-19 이전에는 "발동 조건"(수면 점수 추세 STABLE/RISING + 정체 지표 존재)을 판정하는
 * {@code TriagePolicy}였다. 발동 개념 자체를 없애면서 예보 지표 3종(다크서클·혈색·장벽) 각각의
 * 최근 3주 추세를 독립적으로 보여주는 판정으로 바뀌었다 — 클래스명도 그에 맞춰 바꿨다.
 *
 * <p>관찰 창은 21일(baseDate로 끝난다)이며 겹치지 않는 세 구간(W1·W2·W3, 각 7일)으로 나눈다.
 * W1이 가장 과거, W3이 가장 최근이다. <b>W2는 방향 일관성(변동성) 체크에만 쓰고 그 평균 자체는
 * 응답에 싣지 않는다.</b>
 */
public final class MetricTrendPolicy {

    private MetricTrendPolicy() {
    }

    /** 관찰 창 — 3주(W1·W2·W3 각 7일). */
    public static final int WINDOW_WEEKS = 3;

    /** 한 구간(W1/W2/W3)의 일수. */
    public static final int WEEK_DAYS = 7;

    /** {@link #WINDOW_WEEKS} × {@link #WEEK_DAYS} — 21일. */
    public static final int WINDOW_DAYS = WINDOW_WEEKS * WEEK_DAYS;

    /**
     * 지표 하나의 W1/W2/W3 평균으로 추세를 판정한다.
     *
     * <p>W1 또는 W3 평균이 없으면(그 구간 7일 전부 결측) 비교 자체가 성립하지 않아
     * {@link MetricTrend#INSUFFICIENT_SAMPLE}이다.
     *
     * <p><b>W1→W2, W2→W3의 방향이 정확히 반대(한쪽은 양수, 한쪽은 음수)일 때만
     * {@link MetricTrend#VOLATILE}이다.</b> 둘 중 하나라도 0이면 반대 방향으로 보지 않고
     * 방향 일관(총 변화만으로 판정)으로 처리한다.
     *
     * <p><b>W2가 결측이어도 W1·W3만으로 총 변화(IMPROVED/WORSENED/MAINTAINED)는 판정한다</b> —
     * 다만 {@code VOLATILE} 판정은 중간 구간이 있어야 "오르다 내렸다"를 말할 수 있어 W2가
     * 없으면 건너뛴다.
     *
     * @param w1Average W1(가장 과거 7일) 평균. 그 7일 전부 결측이면 {@code null}
     * @param w2Average W2(중간 7일) 평균. 응답에는 싣지 않고 방향 일관성 체크에만 쓴다.
     *                  결측일 수 있다
     * @param w3Average W3(baseDate로 끝나는 최근 7일) 평균. 그 7일 전부 결측이면 {@code null}
     */
    public static MetricTrendResult classify(Integer w1Average, Integer w2Average, Integer w3Average) {
        if (w1Average == null || w3Average == null) {
            return new MetricTrendResult(MetricTrend.INSUFFICIENT_SAMPLE, null, w1Average, w3Average);
        }

        if (w2Average != null) {
            int leg1 = w2Average - w1Average;
            int leg2 = w3Average - w2Average;
            // 정확히 반대 부호일 때만 VOLATILE — 둘 중 하나라도 0이면 방향 일관으로 취급한다
            if (leg1 > 0 && leg2 < 0) {
                return new MetricTrendResult(MetricTrend.VOLATILE, VolatileDirection.RISE_THEN_FALL,
                        w1Average, w3Average);
            }
            if (leg1 < 0 && leg2 > 0) {
                return new MetricTrendResult(MetricTrend.VOLATILE, VolatileDirection.FALL_THEN_RISE,
                        w1Average, w3Average);
            }
        }

        int total = w3Average - w1Average;
        if (total > 0) {
            return new MetricTrendResult(MetricTrend.IMPROVED, null, w1Average, w3Average);
        }
        if (total < 0) {
            return new MetricTrendResult(MetricTrend.WORSENED, null, w1Average, w3Average);
        }
        return new MetricTrendResult(MetricTrend.MAINTAINED, null, w1Average, w3Average);
    }

}
