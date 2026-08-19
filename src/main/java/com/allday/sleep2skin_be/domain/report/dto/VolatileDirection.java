package com.allday.sleep2skin_be.domain.report.dto;

/**
 * {@link MetricTrend#VOLATILE}일 때의 방향. {@code VOLATILE}이 아닌 다른 추세에서는
 * 항상 {@code null}이다.
 */
public enum VolatileDirection {

    /** W1→W2는 상승, W2→W3는 하락. */
    RISE_THEN_FALL,

    /** W1→W2는 하락, W2→W3는 상승. */
    FALL_THEN_RISE

}
