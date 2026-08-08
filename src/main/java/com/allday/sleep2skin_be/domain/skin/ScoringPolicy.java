package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 스코어링 파라미터 전부가 모이는 한 곳. <b>서비스 로직에 하드코딩하지 않는다.</b>
 *
 * <p>확정값이지만 조정될 수 있고, 흩어지면 조정할 때 전부 찾아다녀야 하는 것은 임시값과 같다.
 * 참조하는 코드에는 출처를 남긴다 — {@code // 확정값 (PRD §10.4)}.
 *
 * <p><b>DB가 아니라 코드에 두는 이유는 테스트다.</b> 기준값이 DB에 있으면 스코어링 단위 테스트가
 * DB를 띄워야 하고, 그러면 아무도 돌리지 않는다. 숫자가 틀리면 제품 전체가 틀리는 로직인데
 * 테스트가 무거워지는 건 나쁜 거래다(erd.md §4). {@code action_master.threshold}를 DB에 둔 것과
 * 반대인데, 그쪽은 <b>콘텐츠</b>(문구와 조건이 한 몸)이고 이쪽은 알고리즘 파라미터라 성격이 다르다.
 *
 * <p><b>수집 정규화 규칙은 여기 없다.</b> 각성 5분·기상 60분 임계값은
 * {@code domain/sleep}의 {@code SleepNormalizationPolicy}에 있다 — 값이 바뀌면 예보 점수가 아니라
 * 저장되는 집계값 자체가 바뀌는 다른 층이다.
 */
public final class ScoringPolicy {

    // ===== §10.1 등급 컷오프 (2026-08-06 확정) =====

    /** 25점씩 4등분이라 경계값이 셋뿐이다. <b>세 지표가 같은 구간을 쓴다.</b> */
    public static final int GRADE_RISK_MAX = 25;
    public static final int GRADE_CAUTION_MAX = 50;
    public static final int GRADE_NORMAL_MAX = 75;

    // ===== §10.2 판정 오차 구간 (2026-08-06 확정) =====

    /** {@code |예보 − 실측| ≤ 5} → 적중. */
    public static final int VERDICT_HIT_TOLERANCE = 5;

    /** {@code |예보 − 실측| ≤ 15} → 근접. 넘어가면 부호로 과소·과대를 가른다. */
    public static final int VERDICT_CLOSE_TOLERANCE = 15;

    // ===== §10.3 피처 → 지표 매핑 (2026-08-07 확정) =====

    /**
     * <b>prd.md §10.3이 유일한 출처이고 이 표는 사본이다.</b> 매핑을 바꿀 땐 §10.3을 고치고
     * erd.md §3.7과 §4.4 REP-07 표를 맞춘다. 세 곳이 어긋나면 예보·리포트·학습이 서로 다른
     * 근거를 쓰게 된다.
     *
     * <p>각 행이 곧 {@code personal_weight}의 한 행이다 — 사용자당 7행이다.
     *
     * <p><b>{@code AWAKE_MINUTES}는 여기 없다.</b> 각성 횟수와 같은 5분 임계값·같은 구간 집합에서
     * 나와 중복 상관이 강해 <b>표시 전용으로 확정</b>됐다. 배정을 미룬 게 아니니 추가하지 말 것.
     *
     * <p>{@code REM_SLEEP → BARRIER}는 직접 근거가 약하다는 것이 문서에 명시돼 있다. 그럼에도
     * 넣은 이유는 생리학이 아니라 <b>학습 가능성</b>이다 — 피처가 1개뿐인 지표는 개인 가중치가
     * 점수 전체를 같은 비율로 밀 뿐 상대 비중을 학습하지 못한다. 리포트 문구에서 이 한계를
     * 과장해 설명하지 않는다.
     */
    private static final Map<SkinMetric, List<SleepFeature>> FEATURES_BY_METRIC = new EnumMap<>(Map.of(
            SkinMetric.DARK_CIRCLE, List.of(SleepFeature.AWAKE_COUNT, SleepFeature.TOTAL_SLEEP),
            SkinMetric.COMPLEXION, List.of(SleepFeature.BEDTIME_REGULARITY, SleepFeature.HRV,
                    SleepFeature.RESTING_HEART_RATE),
            SkinMetric.BARRIER, List.of(SleepFeature.DEEP_SLEEP, SleepFeature.REM_SLEEP)
    ));

    // ===== §10.5 피처 정규화 기준 (2026-08-07 확정) =====

    /**
     * 구간선형 곡선. 꺾인점 사이를 선형 보간하고 <b>양 끝은 클램프한다.</b>
     *
     * <p>모든 곡선이 0점 지점에서 끝나므로 범위 밖 입력도 자동으로 0~100에 갇힌다 — 별도의
     * 클램프 분기가 필요 없다.
     */
    private static final Map<SleepFeature, Curve> CURVES = new EnumMap<>(Map.of(

            // 성인 권장 수면 7~9시간 (NSF 2015, AASM). 540분을 넘으면 완만히 내려간다 —
            // 권장 범위를 벗어난 것은 양방향 모두 이탈이다
            SleepFeature.TOTAL_SLEEP, Curve.of(300, 0, 420, 100, 540, 100, 720, 50, 900, 0),

            // WASO 정상 20분 미만(50세 미만), 5분 초과 각성이 임상적 분기점
            SleepFeature.AWAKE_COUNT, Curve.of(1, 100, 5, 0),

            // 건강 성인 N3 비율 정상 범위 (Boulos 2019 메타분석). 입력은 분이 아니라 %다
            SleepFeature.DEEP_SLEEP, Curve.of(5, 0, 13, 100, 23, 100, 35, 50, 47, 0),

            // 같은 메타분석의 REM 비율 정상 범위. 입력은 분이 아니라 %다
            SleepFeature.REM_SLEEP, Curve.of(8, 0, 18, 100, 27, 100, 42, 50, 57, 0),

            // 수면 타이밍 규칙성 합의문(NSF 2023)·UK Biobank SRI. 낮을수록 좋다
            SleepFeature.BEDTIME_REGULARITY, Curve.of(30, 100, 120, 0),

            // 웨어러블 야간 RMSSD 참조값 (성인 단일 밴드)
            SleepFeature.HRV, Curve.of(15, 0, 60, 100),

            // 야간 안정시 심박 참조값 (성인 단일 밴드). 낮을수록 좋다
            SleepFeature.RESTING_HEART_RATE, Curve.of(55, 100, 85, 0)
    ));

    private ScoringPolicy() {
    }

    /**
     * 지표에 붙은 피처들. 반환 순서는 §10.3 표 순서이며 계산 결과에 영향을 주지 않는다.
     */
    public static List<SleepFeature> featuresOf(SkinMetric metric) {
        return FEATURES_BY_METRIC.get(metric);
    }

    /**
     * 초기(일반) 가중치 — <b>지표 내 균등</b> (§10.4).
     * {@code DARK_CIRCLE}·{@code BARRIER} 각 {@code 0.5}, {@code COMPLEXION} 각 {@code 1/3}.
     *
     * <p><b>균등에서 출발하는 것이 근거 없는 차등보다 방어하기 쉽다.</b> 피처별 상대 기여도를
     * 특정할 수 있는 문헌이 피부 지표 3종에 대해 존재하지 않는다. 여기서 {@code 0.4 / 0.35 / 0.25}
     * 같은 숫자를 만들면 그 숫자 자체가 새로운 임시값이 된다. 근거는 가중치가 아니라 정규화
     * 기준(§10.5)에 싣고, 차등은 개인 가중치 학습이 데이터로 만들게 한다.
     *
     * <p>⚠️ <b>지금은 지표 내 균등이라 재정규화 뒤 완전히 상쇄된다</b> — 값만 보면 식에서 빼도
     * 결과가 같다. 그래도 남겨두는 것은 §10.4가 정의한 항이고 균등이 아니게 될 수 있기 때문이다.
     * 빼두면 그때 곱하는 자리를 다시 찾아야 한다.
     */
    public static double generalWeight(SkinMetric metric) {
        return 1.0 / featuresOf(metric).size();
    }

    /**
     * 피처의 0~100 부분점수 {@code s(f)}.
     *
     * <p>⚠️ <b>{@code DEEP_SLEEP}·{@code REM_SLEEP}의 입력은 분이 아니라 비율(%)이다.</b>
     * 분모는 총 수면이 아니라 {@code deep + rem + core}다 — 여기서 단위나 분모를 헷갈리면
     * 장벽 점수만 조용히 틀린다(§10.5).
     */
    public static double featureScore(SleepFeature feature, double value) {
        return CURVES.get(feature).scoreOf(value);
    }

    /** 점수 → 등급 (§10.1). */
    public static SkinGrade grade(int score) {
        if (score <= GRADE_RISK_MAX) {
            return SkinGrade.RISK;
        }
        if (score <= GRADE_CAUTION_MAX) {
            return SkinGrade.CAUTION;
        }
        if (score <= GRADE_NORMAL_MAX) {
            return SkinGrade.NORMAL;
        }
        return SkinGrade.STABLE;
    }

    /**
     * 예보와 실측을 대조해 판정 (§10.2).
     *
     * <p><b>절댓값을 먼저 걸고 남은 것만 부호를 본다.</b> 순서를 바꾸면 −5점이 과소예측으로 샌다.
     */
    public static VerificationVerdict verdict(int forecast, int measured) {
        int difference = forecast - measured;
        int magnitude = Math.abs(difference);

        if (magnitude <= VERDICT_HIT_TOLERANCE) {
            return VerificationVerdict.HIT;
        }
        if (magnitude <= VERDICT_CLOSE_TOLERANCE) {
            return VerificationVerdict.CLOSE;
        }
        return difference < 0 ? VerificationVerdict.UNDERESTIMATED : VerificationVerdict.OVERESTIMATED;
    }

    /** 개인 가중치가 없는 사용자(첫 검증 전)의 배수. 이때 {@code w'}는 일반 가중치와 같다. */
    public static final BigDecimal DEFAULT_PERSONAL_WEIGHT = BigDecimal.ONE;

    /**
     * 꺾인점 목록으로 정의한 구간선형 곡선.
     *
     * @param breakpoints 입력값 오름차순, 각 원소는 {@code {입력, 부분점수}}
     */
    private record Curve(double[][] breakpoints) {

        /**
         * @param pairs {@code 입력1, 점수1, 입력2, 점수2, ...}
         */
        static Curve of(double... pairs) {
            double[][] points = new double[pairs.length / 2][2];
            for (int i = 0; i < points.length; i++) {
                points[i][0] = pairs[i * 2];
                points[i][1] = pairs[i * 2 + 1];
            }
            return new Curve(points);
        }

        double scoreOf(double value) {
            if (value <= breakpoints[0][0]) {
                return breakpoints[0][1];
            }
            for (int i = 1; i < breakpoints.length; i++) {
                if (value <= breakpoints[i][0]) {
                    double[] from = breakpoints[i - 1];
                    double[] to = breakpoints[i];
                    double ratio = (value - from[0]) / (to[0] - from[0]);
                    return from[1] + ratio * (to[1] - from[1]);
                }
            }
            return breakpoints[breakpoints.length - 1][1];
        }
    }

}
