package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore.UnavailableMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 하룻밤의 수면 집계를 피부 예보 점수로 바꾼다. 저장도 조회도 하지 않는 순수 계산이다.
 *
 * <pre>
 * 지표점수(m) = Σ_f [ w'(m,f) × s(f) ]
 * w'(m,f)    = w일반(m,f) × w개인(m,f) / Σ_f(...)    ← 지표 내 합 = 1로 재정규화
 * s(f)       = 피처 f의 0~100 부분점수 (§10.5)
 * </pre>
 *
 * <p><b>재정규화가 이 설계의 핵심이다</b>(§10.4). 세 가지가 여기서 나온다.
 *
 * <ul>
 *   <li><b>0~100이 자동으로 보장된다</b> — {@code s}가 0~100이고 {@code w'}의 합이 1이라
 *       가중평균의 결과도 0~100이다. 스케일 상수도 클램프도 필요 없다</li>
 *   <li><b>개인 가중치가 상대 비중을 학습한다</b> — 배수로 곱한 뒤 재정규화하므로 한 피처를
 *       올리면 같은 지표의 다른 피처 비중이 내려간다. 재정규화가 없으면 점수 전체가 같은
 *       방향으로 밀릴 뿐 학습이 되지 않는다</li>
 *   <li><b>결측이 분기 없이 처리된다</b> — 없는 피처의 항을 빼고 다시 재정규화하면 끝이다</li>
 * </ul>
 *
 * <p><b>{@code DARK_CIRCLE}은 "심한 정도"가 아니라 "회복된 정도"다.</b> 각성이 많은 밤일수록
 * 점수가 내려간다 — 곡선({@code AWAKE_COUNT}: 1회 이하 100점, 5회 이상 0점)이 그 방향을 만든다.
 */
@Component
public class SkinScoringEngine {

    public SkinForecastScore score(ScoringCommand command) {
        Map<SleepFeature, Double> featureScores = featureScores(command);

        Map<SkinMetric, Integer> metricScores = new EnumMap<>(SkinMetric.class);
        List<UnavailableMetric> unavailable = new ArrayList<>();

        for (SkinMetric metric : SkinMetric.values()) {
            List<SleepFeature> scored = ScoringPolicy.featuresOf(metric).stream()
                    .filter(featureScores::containsKey)
                    .toList();

            if (scored.isEmpty()) {
                unavailable.add(new UnavailableMetric(metric,
                        ScoringPolicy.reasonFor(metric, command.isWatchDataMissing())));
                continue;
            }
            metricScores.put(metric, weightedScore(metric, scored, featureScores, command));
        }

        return new SkinForecastScore(metricScores, List.copyOf(unavailable), featureScores);
    }

    /**
     * 참여한 피처만 담는다. <b>{@code null}은 0점이 아니라 키의 부재로 표현한다</b> — 0점으로 두면
     * 존재하지 않은 값이 점수에도 학습에도 그대로 반영된다.
     *
     * <p><b>개인 가중치와 무관하다.</b> 부분점수는 가중치를 곱하기 전 단계이므로 같은 밤이면
     * 항상 같은 값이 나온다. HOME-08이 검증 시점에 이걸 다시 계산해도 예보 때와 같은 값을
     * 얻는 근거이며(§10.7), 그래서 부분점수를 저장하지 않는다.
     */
    public Map<SleepFeature, Double> featureScores(ScoringCommand command) {
        Map<SleepFeature, Double> scores = new EnumMap<>(SleepFeature.class);

        // 이 둘은 세션이 존재하는 이상 항상 있다 — 결측되지 않는다 (§10.3)
        scores.put(SleepFeature.AWAKE_COUNT,
                ScoringPolicy.featureScore(SleepFeature.AWAKE_COUNT, command.awakeCount()));
        scores.put(SleepFeature.TOTAL_SLEEP,
                ScoringPolicy.featureScore(SleepFeature.TOTAL_SLEEP, command.totalSleepMinutes()));

        if (command.stagedSleepMinutes() > 0) {
            scores.put(SleepFeature.DEEP_SLEEP, ScoringPolicy.featureScore(
                    SleepFeature.DEEP_SLEEP, stagePercentage(command.deepSleepMinutes(), command)));
            scores.put(SleepFeature.REM_SLEEP, ScoringPolicy.featureScore(
                    SleepFeature.REM_SLEEP, stagePercentage(command.remSleepMinutes(), command)));
        }
        if (command.bedtimeRegularitySd() != null) {
            scores.put(SleepFeature.BEDTIME_REGULARITY, ScoringPolicy.featureScore(
                    SleepFeature.BEDTIME_REGULARITY, command.bedtimeRegularitySd()));
        }
        if (command.hrv() != null) {
            scores.put(SleepFeature.HRV,
                    ScoringPolicy.featureScore(SleepFeature.HRV, command.hrv().doubleValue()));
        }
        if (command.restingHeartRate() != null) {
            scores.put(SleepFeature.RESTING_HEART_RATE, ScoringPolicy.featureScore(
                    SleepFeature.RESTING_HEART_RATE, command.restingHeartRate()));
        }
        return scores;
    }

    /**
     * ⚠️ <b>분모는 총 수면이 아니라 단계 합이다</b> (§10.5).
     *
     * <p>총 수면에는 단계 미상 구간이 섞일 수 있는데 그걸 분모에 넣으면 <b>측정하지 못한 시간이
     * "깊은 수면이 아니었던 시간"으로 계산된다.</b> 420분 중 45분만 잡힌 밤에서 깊은수면 20분은
     * 총 수면 분모로는 4.8%(0점)지만 단계 합 분모로는 44%로 정상 범위다. 전제(단계 누락 없음)가
     * 지켜지면 두 방식의 결과가 완전히 같고, 깨지는 순간에만 갈린다.
     */
    private double stagePercentage(int stageMinutes, ScoringCommand command) {
        return stageMinutes * 100.0 / command.stagedSleepMinutes();
    }

    /**
     * 가중합. {@code w일반}은 지금 지표 내 균등이라 재정규화 뒤 상쇄되지만, §10.4가 정의한 항이라
     * 식에 남겨둔다(균등이 아니게 되면 그대로 동작한다).
     */
    private int weightedScore(SkinMetric metric, List<SleepFeature> scored,
                              Map<SleepFeature, Double> featureScores, ScoringCommand command) {
        double generalWeight = ScoringPolicy.generalWeight(metric);   // 확정값 (PRD §10.4)

        double totalWeight = 0;
        for (SleepFeature feature : scored) {
            totalWeight += generalWeight * personalWeight(feature, command);
        }

        double weighted = 0;
        for (SleepFeature feature : scored) {
            double normalized = generalWeight * personalWeight(feature, command) / totalWeight;
            weighted += normalized * featureScores.get(feature);
        }
        return (int) Math.round(weighted);
    }

    private double personalWeight(SleepFeature feature, ScoringCommand command) {
        return command.personalWeights()
                .getOrDefault(feature, ScoringPolicy.DEFAULT_PERSONAL_WEIGHT)
                .doubleValue();
    }

}
