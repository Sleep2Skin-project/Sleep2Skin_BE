package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse.FeatureWeight;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse.MetricWeights;
import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinModelQueryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PersonalWeightRepository personalWeightRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;

    @InjectMocks
    private SkinModelQueryService service;

    /**
     * §10.7의 예시 그대로다 — 비슷한 밤이 20번 쌓이면 {@code 1.23} 대 {@code 0.77}이 되고
     * 그게 <b>약 1.6배 차이</b>다.
     */
    @Test
    @DisplayName("저장된 배수를 재정규화해 비중으로 바꾼다 — 예보가 쓰는 것과 같은 정의다")
    void 배수를_비중으로_바꾼다() {
        userExists();
        weights(Map.of(
                SleepFeature.AWAKE_COUNT, "1.2300",
                SleepFeature.TOTAL_SLEEP, "0.7700"));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(20L);

        PersonalModelResponse response = service.getModel(USER_ID);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        List<FeatureWeight> darkCircle = featuresOf(response, SkinMetric.DARK_CIRCLE);

        // 1.23 / (1.23 + 0.77) = 0.615
        assertThat(darkCircle.getFirst().feature()).isEqualTo(SleepFeature.AWAKE_COUNT);
        assertThat(darkCircle.getFirst().personalShare()).isCloseTo(0.62, within(0.01));
        assertThat(darkCircle.getFirst().generalShare()).isCloseTo(0.5, within(0.01));
        assertThat(darkCircle.getFirst().ratio()).isCloseTo(1.23, within(0.01));

        assertThat(darkCircle.get(1).personalShare()).isCloseTo(0.39, within(0.01));
        assertThat(darkCircle.get(1).ratio()).isCloseTo(0.77, within(0.01));
    }

    /** 지표 내 합이 1이어야 예보가 쓰는 숫자와 같다. 다르면 화면과 계산이 어긋난다. */
    @Test
    @DisplayName("지표마다 비중의 합이 1이다")
    void 지표_내_합이_1이다() {
        userExists();
        weights(Map.of(
                SleepFeature.AWAKE_COUNT, "1.8000",
                SleepFeature.TOTAL_SLEEP, "0.6000",
                SleepFeature.HRV, "1.5000"));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(9L);

        PersonalModelResponse response = service.getModel(USER_ID);

        for (MetricWeights metric : response.model().metrics()) {
            double sum = metric.features().stream().mapToDouble(FeatureWeight::personalShare).sum();
            assertThat(sum).as("%s 의 비중 합", metric.metric()).isCloseTo(1.0, within(0.02));
        }
    }

    @Test
    @DisplayName("헤드라인은 지표 안에서 최대/최소 비가 가장 큰 곳으로 만든다")
    void 헤드라인은_가장_벌어진_지표에서_나온다() {
        userExists();
        weights(Map.of(
                SleepFeature.AWAKE_COUNT, "1.2300",     // 다크서클 — 1.6배 차이
                SleepFeature.TOTAL_SLEEP, "0.7700",
                SleepFeature.DEEP_SLEEP, "1.8000",      // 장벽 — 3배 차이 (이쪽이 이긴다)
                SleepFeature.REM_SLEEP, "0.6000"));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(20L);

        PersonalModelResponse response = service.getModel(USER_ID);

        assertThat(response.model().headline()).contains("깊은 수면").contains("3.0배");
    }

    /**
     * <b>오류가 아니다.</b> 첫 검증 직후나 부분점수가 늘 같았던 경우이며 신규 사용자에게 정상이다.
     */
    @Test
    @DisplayName("전부 1.0이면 아직 배운 게 없다고 말한다")
    void 학습_전에는_같다고_말한다() {
        userExists();
        weights(Map.of());      // 7행 전부 1.0
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(1L);

        PersonalModelResponse response = service.getModel(USER_ID);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.model().headline()).contains("일반 모델과 같아요");
        assertThat(response.model().metrics())
                .flatExtracting(MetricWeights::features)
                .allSatisfy(feature -> assertThat(feature.ratio()).isCloseTo(1.0, within(0.01)));
    }

    /** 행의 존재 자체가 개인화 시작 여부다 (erd.md §3.7). */
    @Test
    @DisplayName("가중치 행이 없으면 개인화 전이라는 빈 상태다")
    void 행이_없으면_빈_상태다() {
        userExists();
        given(personalWeightRepository.findByUserId(USER_ID)).willReturn(List.of());

        PersonalModelResponse response = service.getModel(USER_ID);

        assertThat(response.status()).isEqualTo(QueryStatus.NO_VERIFICATION);
        assertThat(response.message()).isNotBlank();
        assertThat(response.model()).isNull();
    }

    /** 앱이 한국어 이름을 따로 하드코딩하면 수면 통역 카드의 문구와 어긋난다. */
    @Test
    @DisplayName("피처의 한국어 이름을 함께 내려준다")
    void 한국어_이름을_함께_준다() {
        userExists();
        weights(Map.of());
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(1L);

        PersonalModelResponse response = service.getModel(USER_ID);

        assertThat(featuresOf(response, SkinMetric.DARK_CIRCLE))
                .extracting(FeatureWeight::label)
                .containsExactly("야간 각성", "총 수면 시간");
    }

    /** 신뢰도 등급은 서버가 만들지 않는다 (L8 해결) — 숫자를 그대로 준다. */
    @Test
    @DisplayName("검증 횟수를 등급이 아니라 숫자로 준다")
    void 검증_횟수를_그대로_준다() {
        userExists();
        weights(Map.of());
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(12L);

        assertThat(service.getModel(USER_ID).model().verificationCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.getModel(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    /** 지정하지 않은 피처는 {@code 1.0}이다 — 학습은 7행을 한꺼번에 만든다. */
    private void weights(Map<SleepFeature, String> overrides) {
        Map<SleepFeature, String> values = new EnumMap<>(SleepFeature.class);
        Arrays.stream(SleepFeature.values()).forEach(feature -> values.put(feature, "1.0000"));
        values.putAll(overrides);

        given(personalWeightRepository.findByUserId(USER_ID)).willReturn(
                values.entrySet().stream()
                        .map(entry -> PersonalWeight.builder()
                                .userId(USER_ID)
                                .sleepFeature(entry.getKey())
                                .skinMetric(ScoringPolicy.metricOf(entry.getKey()))
                                .weight(new BigDecimal(entry.getValue()))
                                .build())
                        .toList());
    }

    private List<FeatureWeight> featuresOf(PersonalModelResponse response, SkinMetric metric) {
        return response.model().metrics().stream()
                .filter(weights -> weights.metric() == metric)
                .findFirst()
                .orElseThrow(() -> new AssertionError("지표가 없다: " + metric))
                .features();
    }

}
