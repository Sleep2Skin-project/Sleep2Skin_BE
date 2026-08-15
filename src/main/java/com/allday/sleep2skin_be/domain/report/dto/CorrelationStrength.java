package com.allday.sleep2skin_be.domain.report.dto;

/**
 * 상관 강도 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>구간 경계는 {@code CorrelationPolicy}에 있다. <b>⚠️ 임시값 — 팀 확정 필요</b>(그 클래스
 * Javadoc 참고). 표본이 5개 미만이면 이 값 자체가 없다({@code null}) — 강도를 매길 근거가
 * 없는 것이지 {@code WEAK}가 아니다.
 */
public enum CorrelationStrength {

    /** 상관계수 절댓값 0.7 이상. */
    VERY_STRONG,

    /** 상관계수 절댓값 0.4 이상 0.7 미만. */
    STRONG,

    /** 상관계수 절댓값 0.2 이상 0.4 미만. */
    MODERATE,

    /** 상관계수 절댓값 0.2 미만. */
    WEAK

}
