package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse.FeatureWeight;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse.MetricWeights;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse.Model;
import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.SleepInterpretationPolicy;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 내 모델 — 일반 vs 개인화 (REP-12).
 *
 * <h2>비율은 같은 지표 안에서만 의미를 갖는다</h2>
 *
 * <p>{@code weight}는 일반 가중치에 곱하는 배수이고 곱한 뒤 <b>지표 내 합이 1로 재정규화</b>되므로
 * 절댓값 자체에는 의미가 없다(erd.md §3.7). 그래서 지표별로 묶어 내보내고, 헤드라인도
 * <b>지표 안에서 최대/최소 비가 가장 큰 지표</b>로 만든다.
 *
 * <p><b>재정규화를 여기서 다시 하는 것이 요점이다.</b> 저장된 배수를 그대로 보여주면 예보가 실제로
 * 쓰는 비중과 다른 숫자를 화면에 띄우게 된다 — 같은 값을 두 번 계산하는 것이 아니라, 예보와
 * 같은 정의를 쓰는 것이다.
 *
 * <p><b>{@code personal_weight}가 유일한 출처다</b>(§10.7 L1). REP-07은 관측된 상관을, MY-01은
 * 검증 횟수를 말한다 — <b>셋이 서로 다른 질문에 답한다.</b>
 *
 * <p><b>신뢰도 등급을 만들지 않는다</b>(§4.5, L8 해결). 검증 횟수를 그대로 주고 해석은 클라이언트가
 * 한다 — 컷오프를 서버에 두면 바꿀 때마다 배포해야 한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SkinModelQueryService {

    private final UserRepository userRepository;
    private final PersonalWeightRepository personalWeightRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;

    public PersonalModelResponse getModel(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "모델을 조회할 사용자가 없다 userId=" + userId);
        }

        List<PersonalWeight> weights = personalWeightRepository.findByUserId(userId);
        if (weights.isEmpty()) {
            // 행의 존재 자체가 개인화 시작 여부다 (erd.md §3.7). 신규 사용자에게 정상이다
            log.info("개인 가중치 없음 — 아직 검증 전 userId={}", userId);
            return PersonalModelResponse.empty();
        }

        List<MetricWeights> metrics = metricWeights(byFeature(weights));
        return PersonalModelResponse.of(new Model(
                skinMeasurementRepository.countByUserId(userId), headline(metrics), metrics));
    }

    private Map<SleepFeature, BigDecimal> byFeature(List<PersonalWeight> weights) {
        Map<SleepFeature, BigDecimal> byFeature = new EnumMap<>(SleepFeature.class);
        weights.forEach(weight -> byFeature.put(weight.getSleepFeature(), weight.getWeight()));
        return byFeature;
    }

    /**
     * 지표마다 배수를 <b>합이 1이 되도록 재정규화</b>한다 — 예보가 쓰는 것과 같은 정의다.
     *
     * <p>일반 가중치가 지표 내 균등이라 {@code generalShare}는 {@code 1/n}이고,
     * {@code ratio = personalShare / generalShare}가 곧 "일반 대비 몇 배"가 된다.
     */
    private List<MetricWeights> metricWeights(Map<SleepFeature, BigDecimal> byFeature) {
        List<MetricWeights> metrics = new ArrayList<>();

        for (SkinMetric metric : SkinMetric.values()) {
            List<SleepFeature> features = ScoringPolicy.featuresOf(metric);
            double generalShare = ScoringPolicy.generalWeight(metric);   // 확정값 (PRD §10.4)

            double total = features.stream().mapToDouble(feature -> multiplier(byFeature, feature)).sum();

            List<FeatureWeight> weights = features.stream()
                    .map(feature -> {
                        double personalShare = multiplier(byFeature, feature) / total;
                        return new FeatureWeight(feature,
                                SleepInterpretationPolicy.label(feature),
                                round(generalShare), round(personalShare),
                                round(personalShare / generalShare));
                    })
                    .toList();

            metrics.add(new MetricWeights(metric, weights));
        }
        return List.copyOf(metrics);
    }

    /** 행이 빠져 있으면 학습 전으로 본다 — 7행을 한꺼번에 만들지만 방어해 둔다. */
    private double multiplier(Map<SleepFeature, BigDecimal> byFeature, SleepFeature feature) {
        return byFeature.getOrDefault(feature, ScoringPolicy.DEFAULT_PERSONAL_WEIGHT).doubleValue();
    }

    /**
     * 지표 안에서 최대/최소 비가 가장 큰 지표로 한 문장을 만든다.
     *
     * <p><b>지표를 넘어 비교하지 않는다.</b> 재정규화 때문에 다른 지표의 배수와는 스케일이 달라
     * 비교 자체가 성립하지 않는다(erd.md §3.7).
     *
     * <p>전부 {@code 1.0}이면 아직 배운 것이 없다 — 첫 검증 직후나 부분점수가 늘 같았던 경우이며
     * 오류가 아니다.
     */
    private String headline(List<MetricWeights> metrics) {
        return metrics.stream()
                .map(this::spreadOf)
                .filter(spread -> spread.ratio() > 1.0)
                .max(Comparator.comparingDouble(Spread::ratio))
                .map(spread -> "%s에 %.1f배 민감해요".formatted(spread.label(), spread.ratio()))
                .orElse("아직은 일반 모델과 같아요. 검증이 쌓이면 나에게 맞게 조정돼요.");
    }

    /** 지표 하나의 최대/최소 비와, 그 최댓값을 가진 피처의 이름. */
    private Spread spreadOf(MetricWeights metric) {
        double max = metric.features().stream().mapToDouble(FeatureWeight::personalShare).max().orElse(0);
        double min = metric.features().stream().mapToDouble(FeatureWeight::personalShare).min().orElse(0);

        String label = metric.features().stream()
                .max(Comparator.comparingDouble(FeatureWeight::personalShare))
                .map(FeatureWeight::label)
                .orElse("");

        return new Spread(label, min == 0 ? 1.0 : round(max / min));
    }

    /** 소수 둘째 자리. 화면에 "1.6배"로 나가는 숫자라 더 정밀할 이유가 없다. */
    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private record Spread(String label, double ratio) {
    }

}
