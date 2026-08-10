package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelUpdateResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelUpdateResponse.WeightChangeResponse;
import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.sleep.BedtimeRegularityCalculator;
import com.allday.sleep2skin_be.domain.sleep.SleepInterpretationPolicy;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 개인 가중치 학습 (HOME-08 — §10.7).
 *
 * <pre>
 * e      = 실측 − 예보
 * Δw(f)  = η × (e / 100) × ((s(f) − 지표점수) / 100)        η = 0.5
 * w(f)   = clamp(w(f) + Δw(f), 0.5, 2.0)
 * </pre>
 *
 * <h2>오차를 누구 탓으로 돌릴 것인가</h2>
 *
 * <p>지표 하나당 오차는 <b>하나</b>인데 기여한 피처는 2~3개다. 배분 기준은 공식에서 그대로
 * 나온다 — 지표점수가 부분점수의 가중평균이라 {@code ∂지표점수/∂w(f) ∝ s(f) − 지표점수}다.
 * 실측이 예보보다 나빴으면 부분점수가 평균보다 낮았던 피처의 비중이 올라간다.
 *
 * <h2>재계산하는 것은 부분점수뿐이다</h2>
 *
 * <p>{@code s(f)}는 저장하지 않으므로 세션에서 다시 계산한다. <b>부분점수는 개인 가중치와
 * 무관해</b>(가중치를 곱하기 전 단계라) 예보 때와 같은 값이 나온다.
 *
 * <p>⚠️ <b>지표점수는 다시 계산하지 않고 저장된 예보값을 쓴다 — §10.7의 문구와 다르다.</b>
 * 명세는 "{@code s(f)}와 지표점수를 검증 시점에 다시 계산한다"고 적었고 근거는 "검증을 마친 날의
 * 세션과 예보는 갱신되지 않으므로 같은 값이 나온다"였다. 그런데 <b>지표점수만은 개인 가중치에
 * 의존</b>하고, 가중치는 <b>다른 날짜의 검증으로</b> 바뀔 수 있다.
 *
 * <pre>
 * 8/7 예보 산출 (가중치 A)  →  8/8 검증 (가중치 A → B)  →  8/7 검증
 *                                                         재계산하면 가중치 B 로 계산된다
 * </pre>
 *
 * <p>과거 날짜의 셀피를 나중에 올리면 성립하고, api.md가 <b>"과거 날짜도 받는다"</b>고 명시해
 * 두었으므로 드문 경로도 아니다. 저장된 값을 쓰면 이 갈래가 통째로 사라지고, {@code e}의 예보와
 * 지표점수가 <b>정의상 같은 숫자</b>가 된다.
 *
 * <p>대가는 반올림뿐이다 — 저장된 값이 정수라 §10.7 예시의 {@code 67.5} 대신 {@code 68}을 쓴다.
 * 보정량이 {@code 0.0117} 대신 {@code 0.0110}이 되는 정도이며, 학습률 자체가 도달 속도에서
 * 역산한 값이라 이 차이는 의미를 갖지 않는다.
 *
 * <p>⚠️ 남는 예외가 하나 있다. <b>취침 규칙성만 최근 7일 창을 쓰므로</b> 그 사이 과거 날짜의
 * 세션이 갱신되면 {@code s(f)}가 어긋날 수 있다. 이건 §10.7이 이미 적어둔 것이고, 발생 조건이
 * 좁고 오차도 작아 지금은 감수한다.
 *
 * <h2>갱신하지 않는 것</h2>
 *
 * <ul>
 *   <li><b>그날 스코어링에 참여하지 않은 피처</b> — 워치를 안 찬 밤의 오차를 {@code HRV} 탓으로
 *       돌리면 존재하지 않은 값이 학습에 반영된다(§10.6)</li>
 *   <li><b>대조하지 못한 지표의 피처</b> — 예보가 없으면 오차 자체가 없다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkinModelService {

    private final PersonalWeightRepository personalWeightRepository;
    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SkinScoringEngine scoringEngine;

    /**
     * 검증 결과로 개인 가중치를 보정한다. <b>검증과 같은 트랜잭션에서 호출된다</b> —
     * 호출부({@code SkinVerificationService})가 트랜잭션 경계를 들고 있다.
     *
     * @param verifications 예보와 <b>대조한</b> 지표만. {@code skipped}는 넘어오지 않는다
     */
    public PersonalModelUpdateResponse learn(Long userId, SleepSession session,
                                             List<MetricVerificationResponse> verifications) {
        if (verifications.isEmpty()) {
            return PersonalModelUpdateResponse.notUpdated();
        }

        Map<SleepFeature, Double> featureScores = featureScores(userId, session);
        Map<SleepFeature, PersonalWeight> weights = loadOrCreate(userId);

        List<WeightChangeResponse> changes = new ArrayList<>();
        for (MetricVerificationResponse verification : verifications) {
            changes.addAll(apply(userId, session, verification, featureScores, weights));
        }

        return new PersonalModelUpdateResponse(true, message(changes), List.copyOf(changes));
    }

    /**
     * 지표 하나의 오차를 그 지표의 참여 피처에 배분한다.
     *
     * <p><b>{@code forecast.score()}가 곧 지표점수다.</b> 응답 DTO를 거쳐 오지만 값은
     * {@code skin_forecast}에 저장된 그 숫자이며, 같은 값이 {@code e}의 예보이기도 하다.
     */
    private List<WeightChangeResponse> apply(Long userId, SleepSession session,
                                             MetricVerificationResponse verification,
                                             Map<SleepFeature, Double> featureScores,
                                             Map<SleepFeature, PersonalWeight> weights) {

        SkinMetric metric = verification.metric();
        int forecast = verification.forecast().score();
        int measured = verification.measured().score();

        List<WeightChangeResponse> changes = new ArrayList<>();
        for (SleepFeature feature : ScoringPolicy.featuresOf(metric)) {
            Double featureScore = featureScores.get(feature);
            if (featureScore == null) {
                continue;                    // 그날 결측이었던 피처 — 오차를 이쪽 탓으로 돌리지 않는다
            }

            PersonalWeight weight = weights.get(feature);
            BigDecimal before = weight.getWeight();
            BigDecimal delta = ScoringPolicy.weightDelta(measured, forecast, featureScore);
            BigDecimal after = ScoringPolicy.clampWeight(before.add(delta));

            if (after.compareTo(before) == 0) {
                continue;                    // 부분점수가 지표점수와 같은 날 — 배분할 근거가 없다
            }
            weight.updateWeight(after);

            // 이력 테이블을 두지 않기로 한 대신 로그로 남긴다 (erd.md §4)
            log.info("가중치 보정 user={} date={} feature={} metric={} {} -> {} (오차 {})",
                    userId, session.getSleepDate(), feature, metric, before, after, measured - forecast);

            changes.add(new WeightChangeResponse(feature, metric,
                    SleepInterpretationPolicy.label(feature), before, after));
        }
        return changes;
    }

    /**
     * 그날 부분점수 {@code s(f)}. 저장하지 않으므로 세션에서 다시 계산한다.
     *
     * <p>개인 가중치를 넘기지 않는 것은 <b>부분점수가 가중치 이전 단계</b>라서다. 넘겨도 결과가
     * 같지만, 넘기지 않는 쪽이 "여기서 가중치는 의미가 없다"를 코드로 말한다.
     */
    private Map<SleepFeature, Double> featureScores(Long userId, SleepSession session) {
        Double bedtimeRegularitySd = bedtimeRegularityCalculator.calculate(
                userId, session.getSleepDate(), session.getSleepOnsetTime());

        return scoringEngine.featureScores(
                ScoringCommand.forFeatureScores(session, bedtimeRegularitySd));
    }

    /**
     * 7행을 통째로 읽거나, 없으면 통째로 만든다 (§10.7).
     *
     * <p><b>첫 검증 때 7행을 전부 {@code 1.0}으로 만든다</b> — 그날 참여한 피처에만 보정값을
     * 적용하더라도. 행 수가 항상 7로 고정되어야 <b>"행이 있다 = 개인화가 시작됐다"</b>가 그대로
     * 성립하고(erd.md §3.7), REP-12의 판단이 단순해진다.
     */
    private Map<SleepFeature, PersonalWeight> loadOrCreate(Long userId) {
        List<PersonalWeight> existing = personalWeightRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            existing = personalWeightRepository.saveAll(initialWeights(userId));
            log.info("개인 가중치 7행 생성 user={}", userId);
        }

        Map<SleepFeature, PersonalWeight> byFeature = new EnumMap<>(SleepFeature.class);
        existing.forEach(weight -> byFeature.put(weight.getSleepFeature(), weight));
        return byFeature;
    }

    /** 피처 하나가 지표 하나에 붙는 §10.3의 7쌍이 그대로 7행이 된다. */
    private List<PersonalWeight> initialWeights(Long userId) {
        return java.util.Arrays.stream(SleepFeature.values())
                .map(feature -> PersonalWeight.builder()
                        .userId(userId)
                        .sleepFeature(feature)
                        .skinMetric(ScoringPolicy.metricOf(feature))
                        .weight(ScoringPolicy.clampWeight(ScoringPolicy.DEFAULT_PERSONAL_WEIGHT))
                        .build())
                .toList();
    }

    /**
     * 가장 크게 올라간 피처 하나로 문장을 만든다.
     *
     * <p><b>올라간 쪽을 고르는 것은 그게 사용자가 읽을 이야기이기 때문이다</b> — "이 사람에겐
     * 각성이 더 치명적이더라". 보정식의 성질상 한 피처가 올라가면 같은 지표의 다른 피처는
     * 반드시 내려가므로(§10.7 "합이 0이다"), 내려간 쪽까지 말하면 같은 사실을 두 번 말하게 된다.
     */
    private String message(List<WeightChangeResponse> changes) {
        return changes.stream()
                .filter(change -> change.after().compareTo(change.before()) > 0)
                .max(Comparator.comparing(change -> change.after().subtract(change.before())))
                .map(change -> "%s을(를) 조금 더 중요하게 보도록 학습했어요.".formatted(change.label()))
                .orElse("오늘은 예보와 실측이 거의 같아 모델을 그대로 두었어요.");
    }

}
