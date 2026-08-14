package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;

/**
 * 상관 강도 판정 기준 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>⚠️ <b>임시값 — 팀 확정 필요.</b> 통계학에서 흔히 쓰는 상관계수 절댓값 구간(문헌마다
 * 0.1/0.3/0.5 또는 0.3/0.5/0.7 등 여러 변형이 있다)을 참고해 0.7/0.4/0.2로 임시 채택했다.
 * 수면-피부 상관관계라는 이 도메인 고유의 데이터에 그대로 적용해도 되는 구간인지는 검증된
 * 적이 없다 — prd.md §9.2의 다른 임시값(L7 상관 강도 라벨 구간)과 같은 자리이며, 팀이
 * 재확인하면 이 파일의 상수만 바꾸면 된다.
 *
 * <p>표본 하한({@link #MIN_SAMPLE_SIZE})도 같은 이유로 임시값이다 — "5개"는 상관계수가
 * 우연히 극단값을 내지 않을 최소한이라는 통념을 따른 것이지, 이 서비스의 데이터로 검증한
 * 값이 아니다.
 */
public final class CorrelationPolicy {

    private CorrelationPolicy() {
    }

    /**
     * 유효 표본 수가 이 값 미만이면 상관계수를 계산하지 않는다(임시값). 5개 미만의 점으로 낸
     * 상관계수는 극단값 하나에도 크게 흔들려 "강한 상관"이라는 문구가 우연을 가리킬 수 있다.
     */
    public static final int MIN_SAMPLE_SIZE = 5;

    /** 상관계수 절댓값이 이 값 이상이면 {@link CorrelationStrength#VERY_STRONG}(임시값). */
    public static final double VERY_STRONG_THRESHOLD = 0.7;

    /** 상관계수 절댓값이 이 값 이상이면 {@link CorrelationStrength#STRONG}(임시값). */
    public static final double STRONG_THRESHOLD = 0.4;

    /**
     * 상관계수 절댓값이 이 값 이상이면 {@link CorrelationStrength#MODERATE}, 미만이면
     * {@link CorrelationStrength#WEAK}(임시값).
     */
    public static final double MODERATE_THRESHOLD = 0.2;

    /**
     * @param absCorrelation 상관계수의 절댓값(0~1). 부호는 방향이지 강도가 아니라서 여기서는
     *                       다루지 않는다 — 호출부가 부호까지 필요하면 원본 상관계수를 따로 본다
     */
    public static CorrelationStrength strengthOf(double absCorrelation) {
        if (absCorrelation >= VERY_STRONG_THRESHOLD) {
            return CorrelationStrength.VERY_STRONG;
        }
        if (absCorrelation >= STRONG_THRESHOLD) {
            return CorrelationStrength.STRONG;
        }
        if (absCorrelation >= MODERATE_THRESHOLD) {
            return CorrelationStrength.MODERATE;
        }
        return CorrelationStrength.WEAK;
    }

}
