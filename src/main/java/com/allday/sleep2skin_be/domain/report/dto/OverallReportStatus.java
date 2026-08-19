package com.allday.sleep2skin_be.domain.report.dto;

/**
 * 종합 리포트(REP-09~11)의 조회 상태.
 *
 * <p>2026-08-19부터 REP-06·REP-08(주간·월간)과 <b>같은 가입일 기준 게이트</b>를 쓴다 — 가입
 * 당일을 1일차로 세어 {@code MetricTrendPolicy.WINDOW_DAYS}(21일) 미만이면
 * {@code INSUFFICIENT_DATA}다. 이전에는 수면 점수 추세({@code SleepTrend})가
 * {@code INSUFFICIENT_DATA}인가로 결정됐지만, 발동 조건과 함께 그 축 자체가 제거됐다.
 *
 * <p>가입일 기준을 넘겼는데 특정 지표의 표본만 부족한 경우는 이 상태에 영향을 주지 않는다 —
 * {@code trends}에서 그 지표의 {@code trend}만 {@code MetricTrend.INSUFFICIENT_SAMPLE}로
 * 표시될 뿐이다.
 */
public enum OverallReportStatus {

    /** 가입일 기준 21일(관찰 창) 이상 지나 지표별 추세를 판정할 수 있다. */
    FULL,

    /** 가입한 지 21일(관찰 창) 미만이다. */
    INSUFFICIENT_DATA

}
