package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.report.dto.response.FeatureCorrelation;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.BedtimeRegularityCalculator;
import com.allday.sleep2skin_be.domain.sleep.SleepInterpretationPolicy;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수면 피처 7종과 피부 지표 사이의 상관 강도 (REP-06·07 "상관 강도" 섹션).
 *
 * <p><b>예보값이 아니라 실측값(셀피 검증)과 비교한다.</b> "이 피처가 실제 피부에 얼마나
 * 영향을 주는지"를 보여주려는 섹션인데, 예보값은 그 자체가 이 피처들로 계산된 값이라
 * 예보와 비교하면 항상 강한 상관이 나오는 순환 논증이 된다. 그래서 {@code skin_measurement}만
 * 쓴다 — 수면 세션과 실측이 <b>둘 다 있는 날짜만</b> 짝(pair)으로 삼는다.
 *
 * <p><b>"원본값"을 쓴다 — {@code DailySleepScoreCalculator}가 내는 0~100 부분점수가
 * 아니다.</b> 부분점수는 이미 구간선형으로 정규화된 값이라 그것끼리 상관을 내면 정규화
 * 곡선의 모양이 상관계수에 섞여 들어간다. 여기서는 {@code awakeCount}(횟수)·
 * {@code totalSleepMinutes}(분)처럼 저장된 그대로, 비율 피처는 백분율로만 환산해서 쓴다.
 *
 * <p>수면 피처 → 피부 지표 매핑은 예보 산출(§10.3)과 같다. {@code skin} 도메인 파일은
 * 건드리지 않는다 — 여기서는 그 매핑을 상수로 다시 적었을 뿐, {@code ScoringPolicy}를
 * 참조하거나 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CorrelationCalculator {

    /** §10.3의 7쌍. 항상 이 순서·이 7개를 기준으로 결과를 만든다 — 정렬 전 원본 순서다. */
    private static final List<Pair> FEATURE_METRIC_PAIRS = List.of(
            new Pair(SleepFeature.AWAKE_COUNT, SkinMetric.DARK_CIRCLE),
            new Pair(SleepFeature.TOTAL_SLEEP, SkinMetric.DARK_CIRCLE),
            new Pair(SleepFeature.DEEP_SLEEP, SkinMetric.BARRIER),
            new Pair(SleepFeature.REM_SLEEP, SkinMetric.BARRIER),
            new Pair(SleepFeature.BEDTIME_REGULARITY, SkinMetric.COMPLEXION),
            new Pair(SleepFeature.HRV, SkinMetric.COMPLEXION),
            new Pair(SleepFeature.RESTING_HEART_RATE, SkinMetric.COMPLEXION)
    );

    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SkinMeasurementRepository skinMeasurementRepository;

    /**
     * @param sessions 기간 안의 세션. 호출부(주간·월간 서비스)가 {@code dailyScores}·
     *                 {@code weeks} 계산에 쓴 것과 <b>같은 맵을 그대로 넘긴다</b> — 세션을
     *                 다시 조회하지 않는다
     * @return {@code FEATURE_METRIC_PAIRS} 7개 전부. 표본이 부족해도 빠지지 않고
     *         {@code strength: null}로 포함된다. 상관계수 절댓값 내림차순이며, 표본 부족은
     *         전부 배열 맨 뒤로 간다. <b>동률이면 {@code SleepFeature} 선언 순서(§10.3)로
     *         고정한다</b> — {@link CorrelationGroup#groupBySkinMetric}이 그룹당 대표 1개만
     *         뽑으므로, 동률에서 순서가 흔들리면 같은 데이터로도 호출마다 대표가 바뀔 수 있다
     */
    public List<FeatureCorrelation> calculate(Long userId, LocalDate periodStart, LocalDate baseDate,
                                              Map<LocalDate, SleepSession> sessions) {
        Map<LocalDate, VerifiedDay> measuredByDate = skinMeasurementRepository
                .findVerifiedDays(userId, baseDate).stream()
                .filter(day -> !day.baseDate().isBefore(periodStart))
                .collect(Collectors.toMap(VerifiedDay::baseDate, Function.identity()));

        // 세션과 실측이 둘 다 있는 날짜만 — 예보만 있고 검증하지 않은 날은 여기 들어오지 않는다
        List<LocalDate> pairedDates = sessions.keySet().stream()
                .filter(measuredByDate::containsKey)
                .toList();

        return FEATURE_METRIC_PAIRS.stream()
                .map(pair -> correlationFor(pair, userId, pairedDates, sessions, measuredByDate))
                // 1차: sortKey 내림차순. 2차(동률): SleepFeature 선언 순서 오름차순(§10.3) —
                // enum 선언 순서가 이미 그 표와 일치해 ordinal() 자연 순서로 충분하다
                .sorted(Comparator.comparingDouble(Scored::sortKey).reversed()
                        .thenComparing(scored -> scored.response().sleepFeature()))
                .map(Scored::response)
                .toList();
    }

    private Scored correlationFor(Pair pair, Long userId, List<LocalDate> pairedDates,
                                  Map<LocalDate, SleepSession> sessions,
                                  Map<LocalDate, VerifiedDay> measuredByDate) {
        List<Double> featureValues = new ArrayList<>();
        List<Double> metricValues = new ArrayList<>();

        for (LocalDate date : pairedDates) {
            Double featureValue = extractFeatureValue(pair.feature(), userId, date, sessions.get(date));
            // 그 피처만 결측(워치 미착용의 HRV·안정시 심박, 이력 3일 미만의 취침 규칙성,
            // 단계 미상의 비율)인 날은 이 피처-지표 쌍의 계산에서만 뺀다 — 다른 쌍은 그대로 쓴다
            if (featureValue == null) {
                continue;
            }
            featureValues.add(featureValue);
            metricValues.add((double) metricValue(pair.metric(), measuredByDate.get(date)));
        }

        int sampleSize = featureValues.size();
        boolean insufficientSample = sampleSize < CorrelationPolicy.MIN_SAMPLE_SIZE;

        CorrelationStrength strength = null;
        double sortKey = Double.NEGATIVE_INFINITY; // 표본 부족은 정렬 맨 뒤로 가라앉는다

        if (!insufficientSample) {
            double absCorrelation = Math.abs(pearson(featureValues, metricValues));
            strength = CorrelationPolicy.strengthOf(absCorrelation);
            sortKey = absCorrelation;
        }

        FeatureCorrelation response = new FeatureCorrelation(pair.feature(),
                SleepInterpretationPolicy.label(pair.feature()), pair.metric(), metricLabel(pair.metric()),
                strength, sampleSize, insufficientSample);

        return new Scored(response, sortKey);
    }

    /**
     * 피처의 "원본값" — 예보 산출용 0~100 부분점수가 아니라 저장된 그대로거나 그에 가장 가까운
     * 단위다. {@code null}은 그날 이 피처만 결측이라는 뜻이다.
     */
    private Double extractFeatureValue(SleepFeature feature, Long userId, LocalDate date, SleepSession session) {
        return switch (feature) {
            case AWAKE_COUNT -> (double) session.getAwakeCount();
            case TOTAL_SLEEP -> (double) session.getTotalSleepMinutes();
            case DEEP_SLEEP -> stagePercentage(session.getDeepSleepMinutes(), session);
            case REM_SLEEP -> stagePercentage(session.getRemSleepMinutes(), session);
            case BEDTIME_REGULARITY ->
                    bedtimeRegularityCalculator.calculate(userId, date, session.getSleepOnsetTime());
            case HRV -> session.getHrv() == null ? null : session.getHrv().doubleValue();
            case RESTING_HEART_RATE -> session.getRestingHeartRate() == null
                    ? null : session.getRestingHeartRate().doubleValue();
        };
    }

    /**
     * <b>분모는 총 수면이 아니라 단계 합이다</b>(§10.5와 같은 이유). 단계 합이 0인 밤은 비율
     * 자체가 성립하지 않아 {@code null}이다 — 예보에서 {@code BARRIER}가 빈 상태가 되는 것과
     * 같은 조건이다.
     */
    private Double stagePercentage(int stageMinutes, SleepSession session) {
        int staged = session.stagedSleepMinutes();
        return staged == 0 ? null : stageMinutes * 100.0 / staged;
    }

    /** 실측 지표값. {@code skin_measurement}의 세 컬럼 모두 {@code NOT NULL}이라 결측이 없다. */
    private int metricValue(SkinMetric metric, VerifiedDay measured) {
        return switch (metric) {
            case DARK_CIRCLE -> measured.measuredDarkCircle();
            case COMPLEXION -> measured.measuredComplexion();
            case BARRIER -> measured.measuredBarrier();
        };
    }

    /**
     * 예보·검증 API의 지표명과 같은 한국어 표기다({@code SkinControllerSpec} 응답 예시 참고).
     *
     * <p><b>{@code DARK_CIRCLE}은 "다크서클"이 아니라 "다크서클 회복"이다</b>(prd.md §1).
     * 점수가 "심한 정도"가 아니라 <b>"회복된 정도"</b>라 높을수록 좋은데, "다크서클"이라고만
     * 쓰면 높은 점수가 "다크서클이 심하다"로 읽힌다 — 값 범위는 정상이라 아무 제약에도 걸리지
     * 않고 화면에서 뜻만 뒤집힌다.
     */
    private String metricLabel(SkinMetric metric) {
        return switch (metric) {
            case DARK_CIRCLE -> "다크서클 회복";
            case COMPLEXION -> "혈색";
            case BARRIER -> "장벽";
        };
    }

    /**
     * 피어슨 상관계수. <b>두 변수 중 하나라도 분산이 0이면(표본 안에서 값이 전부 같으면)
     * 수학적으로 정의되지 않는다</b> — 0(무관)으로 취급한다. {@code NaN}을 그대로 내보내면
     * JSON 직렬화가 깨지고, 응답에 "상관 없음"과 "계산 불능"을 구분할 필드도 없다.
     */
    private double pearson(List<Double> xs, List<Double> ys) {
        int n = xs.size();
        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        double numerator = 0;
        double sumSquaredX = 0;
        double sumSquaredY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            numerator += dx * dy;
            sumSquaredX += dx * dx;
            sumSquaredY += dy * dy;
        }

        if (sumSquaredX == 0 || sumSquaredY == 0) {
            return 0;
        }
        return numerator / Math.sqrt(sumSquaredX * sumSquaredY);
    }

    private record Pair(SleepFeature feature, SkinMetric metric) {
    }

    private record Scored(FeatureCorrelation response, double sortKey) {
    }

}
