package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 종합 리포트(REP-09~11) 트리아지 발동 판정 기준.
 *
 * <p>확정값 — sub-docs/2026-08-16-report-overall.md §5 근거. 문헌 기반은 아니고 실무 판단으로
 * 정한 값이며, 해커톤 일정상 재확정 없이 이대로 운영한다.
 *
 * <p>표본 하한은 별도로 두지 않고 {@link CorrelationPolicy#MIN_SAMPLE_SIZE}를 그대로 쓴다 — 두
 * 클래스 모두 "5개 미만 표본으로 판단하지 않는다"는 같은 기준을 공유한다.
 */
public final class TriagePolicy {

    private TriagePolicy() {
    }

    // ===== 수면 점수 추세 관찰 창 =====

    // 확정값 — 다른 리포트(REP-06/08)가 7일/28일 단위를 쓰는 것과의 절충 (sub-docs 2026-08-16 §5)
    public static final int SLEEP_TREND_WINDOW_WEEKS = 3;

    /** {@link #SLEEP_TREND_WINDOW_WEEKS}를 일수로 편 값 — 21일. */
    public static final int SLEEP_TREND_WINDOW_DAYS = SLEEP_TREND_WINDOW_WEEKS * 7;

    /**
     * 전반부·후반부 각각의 일수. {@code 10 + 1(가운데 제외) + 10 = 21}이 관찰 창과 맞는다.
     * 가운데 하루를 비교에서 빼는 것은 전반부·후반부를 완충 없이 붙이면 경계에 걸친 하루의
     * 소속이 애매해지기 때문이다.
     */
    public static final int SLEEP_TREND_HALF_DAYS = 10;

    // ===== 피부 지표 정체 판정 =====

    // 확정값 — ScoringPolicy 등급 컷오프(§10.1)의 위험/주의 경계값 재사용 (sub-docs 2026-08-16 §5)
    public static final int STAGNANT_SCORE_THRESHOLD = 50;

    // 확정값 — 판정 오차 구간(§10.2)의 ±5 적중 폭 재사용 (sub-docs 2026-08-16 §5)
    public static final int STAGNANT_RANGE_MAX = 5;

    // ===== 수면 점수 추세 판정 임계값 =====

    /** 유효 표본의 표준편차가 이 값 이상이면 {@link SleepTrend#VOLATILE}(확정값 — sub-docs 2026-08-16 §5). */
    public static final double VOLATILE_STD_DEV_THRESHOLD = 15.0;

    // 확정값 — 판정 오차 구간(§10.2)의 ±5 적중 폭 재사용 (sub-docs 2026-08-16 §5)
    public static final int TREND_DIFF_THRESHOLD = 5;

    /**
     * 최근 3주 수면 점수 추세를 판정한다.
     *
     * <p>가운데 1일을 제외한 20일(전반부 10 + 후반부 10)이 판정 대상이다 — 세션이 없는 날의
     * 값은 {@code null}이며 결측으로 처리한다.
     *
     * <p>판정 순서가 결과를 가른다 — <b>먼저 표본 부족을 걸러내고, 남은 것만 변동성·추세를
     * 본다.</b> 순서를 바꾸면 표본이 1~2개뿐인데도 우연히 표준편차가 크게 나와 VOLATILE로
     * 잘못 읽힐 수 있다.
     *
     * @param firstHalfScores  전반부 10일(가장 과거부터), 세션 없는 날은 {@code null}
     * @param secondHalfScores 후반부 10일({@code baseDate}로 끝난다), 세션 없는 날은 {@code null}
     */
    public static SleepTrend classifySleepTrend(List<Integer> firstHalfScores,
                                                List<Integer> secondHalfScores) {
        List<Integer> firstValid = valuesOf(firstHalfScores);
        List<Integer> secondValid = valuesOf(secondHalfScores);

        int totalValidCount = firstValid.size() + secondValid.size();
        if (totalValidCount < CorrelationPolicy.MIN_SAMPLE_SIZE) {
            return SleepTrend.INSUFFICIENT_DATA;
        }
        // 후반부−전반부 평균 차이 자체를 계산할 수 없다 — 한쪽이 통째로 결측이면 표본이
        // 충분해도 추세를 말할 근거가 없다
        if (firstValid.isEmpty() || secondValid.isEmpty()) {
            return SleepTrend.INSUFFICIENT_DATA;
        }

        List<Integer> combined = new ArrayList<>(firstValid);
        combined.addAll(secondValid);
        if (standardDeviation(combined) >= VOLATILE_STD_DEV_THRESHOLD) {
            return SleepTrend.VOLATILE;
        }

        double difference = average(secondValid) - average(firstValid);
        if (difference >= TREND_DIFF_THRESHOLD) {
            return SleepTrend.RISING;
        }
        if (difference <= -TREND_DIFF_THRESHOLD) {
            return SleepTrend.FALLING;
        }
        return SleepTrend.STABLE;
    }

    /**
     * 피부 지표 하나가 "정체"인가.
     *
     * <p>표본이 부족하면 판정 불가이고, <b>정체 아님으로 취급한다</b>(호출부가 stagnantMetrics에
     * 담지 않는다) — 판정 불가와 "정체 아님"은 의미가 다르지만 이 메서드의 반환 계약(boolean)에서는
     * 둘 다 같은 결과로 접힌다.
     *
     * @param sampleSize   유효 표본 수(그 기간 이 지표가 결측이 아닌 날의 수)
     * @param averageScore 유효 표본의 평균 점수
     * @param minScore     유효 표본의 최솟값
     * @param maxScore     유효 표본의 최댓값
     */
    public static boolean isStagnantMetric(int sampleSize, double averageScore, int minScore, int maxScore) {
        if (sampleSize < CorrelationPolicy.MIN_SAMPLE_SIZE) {
            return false;
        }
        if (averageScore >= STAGNANT_SCORE_THRESHOLD) {
            return false;
        }
        return (maxScore - minScore) <= STAGNANT_RANGE_MAX;
    }

    private static List<Integer> valuesOf(List<Integer> scores) {
        return scores.stream().filter(Objects::nonNull).toList();
    }

    private static double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    /** 모집단 표준편차 — 이 판정에 쓰이는 20일 표본을 그 자체로 전체 관찰 대상으로 본다. */
    private static double standardDeviation(List<Integer> values) {
        double mean = average(values);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElseThrow();
        return Math.sqrt(variance);
    }

}
