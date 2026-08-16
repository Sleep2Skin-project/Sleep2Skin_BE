package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 종합 리포트(REP-09~11) 트리아지 발동 판정 기준.
 *
 * <p>⚠️ <b>임시값 — 팀 확정 필요.</b> 이 서비스에 수면 목표값(B6)이 없어(2026-08-14 MVP 제외)
 * "수면 목표 달성"을 대신할 신호로 <b>최근 3주 수면 점수 추세</b>를 채택했고, 정체 판정의 점수
 * 기준(50점)은 §10.1 등급 컷오프의 "주의" 등급 상한({@code ScoringPolicy.GRADE_CAUTION_MAX})을
 * 재사용한 것이다. 표준편차·변동폭·추세 임계값(5점, 15점)은 이 서비스의 데이터로 검증된 적이
 * 없다 — {@code CorrelationPolicy}와 같은 자리이며, 팀이 재확인하면 이 파일의 상수만 바꾸면 된다.
 *
 * <p>표본 하한은 별도로 두지 않고 {@link CorrelationPolicy#MIN_SAMPLE_SIZE}를 그대로 쓴다 — 두
 * 클래스 모두 "5개 미만 표본으로 판단하지 않는다"는 같은 기준을 공유한다.
 */
public final class TriagePolicy {

    private TriagePolicy() {
    }

    // ===== 수면 점수 추세 관찰 창 =====

    /** 수면 점수 추세를 보는 관찰 창(임시값). */
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

    /**
     * 정체 판정 대상이 되려면 그 기간 평균 점수가 이 값 <b>미만</b>이어야 한다(임시값).
     * §10.1 등급 컷오프의 "주의" 등급 상한을 재사용한다 — 점수가 좋은데 변동폭만 작은 경우를
     * 정체로 오판하지 않기 위해서다. {@code ScoringPolicy.GRADE_CAUTION_MAX}와 값이 같지만,
     * 이쪽은 등급이 아니라 트리아지 판정 기준이라 별도 상수로 둔다 — {@code ScoringPolicy}에
     * 종합 리포트 개념을 섞지 않는다.
     */
    public static final int STAGNANT_SCORE_THRESHOLD = 50;

    /** 정체로 보려면 {@code (최댓값 − 최솟값)}이 이 값 이하여야 한다(임시값). */
    public static final int STAGNANT_RANGE_MAX = 5;

    // ===== 수면 점수 추세 판정 임계값 =====

    /** 유효 표본의 표준편차가 이 값 이상이면 {@link SleepTrend#VOLATILE}(임시값). */
    public static final double VOLATILE_STD_DEV_THRESHOLD = 15.0;

    /** {@code |후반부 평균 − 전반부 평균|}이 이 값 이상이면 상승·하락으로 판정한다(임시값). */
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
