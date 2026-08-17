package com.allday.sleep2skin_be.domain.report.dto;

/**
 * 종합 리포트(REP-09~11)의 조회 상태.
 *
 * <p>기존 {@code QueryStatus}·{@code ReportPeriodStatus}에 값을 끼워넣지 않는다 — 이 API의
 * {@code INSUFFICIENT_DATA}는 <b>가입일 기준이 아니라 수면 점수 추세({@code SleepTrend})가
 * {@code INSUFFICIENT_DATA}인가 하나로만 결정된다</b>. 피부 지표별 정체 판정의 표본 부족은
 * {@code stagnantMetrics}에서 그 지표만 빠질 뿐 이 상태에 영향을 주지 않는다 — 값 집합의 의미가
 * 두 기존 enum 어느 쪽과도 다르다.
 */
public enum OverallReportStatus {

    /** 수면 점수 추세를 판정할 수 있었다. */
    FULL,

    /** 최근 3주 수면 점수 표본이 부족해 추세를 판정할 수 없다. */
    INSUFFICIENT_DATA

}
