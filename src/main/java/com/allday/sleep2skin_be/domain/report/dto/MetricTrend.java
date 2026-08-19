package com.allday.sleep2skin_be.domain.report.dto;

/**
 * 종합 리포트(REP-09~11)의 지표별(다크서클·혈색·장벽) 추세 판정.
 * {@code MetricTrendPolicy.classify}가 이 값을 매긴다.
 *
 * <p>관찰 창(21일)을 W1(가장 과거 7일)·W2(중간 7일)·W3(baseDate로 끝나는 최근 7일)로 나누고,
 * W1·W3 평균의 비교(총 변화)와 W1→W2→W3의 방향 일관성(변동성)을 함께 본다.
 */
public enum MetricTrend {

    /** W3 평균이 W1 평균보다 높다(W3−W1 &gt; 0). */
    IMPROVED,

    /** W3 평균이 W1 평균보다 낮다(W3−W1 &lt; 0). */
    WORSENED,

    /**
     * W1→W2, W2→W3의 방향이 정확히 반대다(오르다 내렸거나 내리다 올랐다) — {@code VolatileDirection}이
     * 어느 쪽인지 함께 나간다. W2 평균이 결측이면 판정하지 않는다(W1·W3만으로는 "오르다 내렸다"를
     * 말할 수 없다).
     */
    VOLATILE,

    /** W3 평균과 W1 평균이 같다(W3−W1 = 0). */
    MAINTAINED,

    /** W1 또는 W3 구간(7일) 전부가 결측이라 비교 자체가 성립하지 않는다. */
    INSUFFICIENT_SAMPLE

}
