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
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 개인 가중치 학습 (§10.7).
 *
 * <p><b>스코어링 엔진은 진짜를 쓴다.</b> 부분점수를 스텁으로 만들면 "어느 피처가 평균보다
 * 낮았는가"를 테스트가 직접 정하게 되어, 검증하려는 배분 규칙과 순환한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinModelServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private PersonalWeightRepository personalWeightRepository;
    @Mock
    private BedtimeRegularityCalculator bedtimeRegularityCalculator;

    private SkinModelService skinModelService;
    private List<PersonalWeight> saved;

    @BeforeEach
    void setUp() {
        saved = new ArrayList<>();
        skinModelService = new SkinModelService(personalWeightRepository,
                bedtimeRegularityCalculator, new SkinScoringEngine());

        given(personalWeightRepository.findByUserId(USER_ID)).willAnswer(invocation -> saved);
        given(personalWeightRepository.saveAll(any())).willAnswer(invocation -> {
            List<PersonalWeight> rows = invocation.getArgument(0);
            saved.addAll(rows);
            return rows;
        });
        // 이력 3일 미만 — 신규 사용자는 반드시 이 갈래를 탄다 (§10.6)
        given(bedtimeRegularityCalculator.calculate(anyLong(), any(), any())).willReturn(null);
    }

    /**
     * <b>행의 존재 자체가 "개인화가 시작됐다"는 뜻</b>이라 7행이 고정이어야 한다(erd.md §3.7).
     * 참여한 피처만 만들면 REP-12가 행 수를 세어 판단할 수 없다.
     */
    @Test
    @DisplayName("첫 검증 때 7행을 전부 1.0으로 만든다 — 그날 참여하지 않은 피처까지")
    void 첫_검증에_7행을_만든다() {
        skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(saved).hasSize(7);
        assertThat(saved).extracting(PersonalWeight::getSleepFeature)
                .containsExactlyInAnyOrder(SleepFeature.values());

        // 워치를 안 찬 밤이라 HRV 는 스코어링에 참여하지 않았지만 행은 만들어지고 1.0 그대로다
        assertThat(weightOf(SleepFeature.HRV)).isEqualByComparingTo("1.0000");
    }

    /**
     * §10.7의 예시 그대로다 — 다크서클 예보 68 / 실측 55. 각성 부분점수가 총 수면보다 낮은
     * 밤이므로 "이 사람에겐 각성이 더 치명적이더라" 쪽으로 움직여야 한다.
     */
    @Test
    @DisplayName("실측이 예보보다 나쁘면 부분점수가 낮았던 피처의 비중이 올라간다")
    void 오차를_부분점수_편차로_배분한다() {
        // 각성 3회 → 부분점수 50 · 총 수면 440분 → 100. 각성이 평균보다 낮다
        PersonalModelUpdateResponse response =
                skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(response.updated()).isTrue();
        assertThat(weightOf(SleepFeature.AWAKE_COUNT)).isGreaterThan(new BigDecimal("1.0000"));
        assertThat(weightOf(SleepFeature.TOTAL_SLEEP)).isLessThan(new BigDecimal("1.0000"));
    }

    /**
     * <b>한 피처가 올라가면 반드시 다른 피처가 내려간다</b>(§10.7 "합이 0이다"). 재정규화 설계와
     * 맞물리는 성질이라, 한쪽으로만 밀리면 개인 가중치가 점수 전체를 옮길 뿐 상대 비중을 학습하지
     * 못한다.
     */
    @Test
    @DisplayName("같은 지표 안에서 한 피처가 오르면 다른 피처는 내려간다")
    void 지표_안에서_비중이_이동한다() {
        skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        BigDecimal awake = weightOf(SleepFeature.AWAKE_COUNT).subtract(BigDecimal.ONE);
        BigDecimal total = weightOf(SleepFeature.TOTAL_SLEEP).subtract(BigDecimal.ONE);

        assertThat(awake.signum()).isNotEqualTo(total.signum());
    }

    /**
     * <b>결측 밤의 오차를 HRV 탓으로 돌리면 존재하지 않은 값이 학습에 반영된다</b>(§10.6).
     * 값 범위는 정상이라 아무 제약에도 안 걸린다.
     */
    @Test
    @DisplayName("그날 스코어링에 참여하지 않은 피처는 갱신하지 않는다")
    void 결측_피처는_학습하지_않는다() {
        // 워치 미착용 → HRV·안정시 심박 결측. 혈색은 예보가 없어 대조 목록에도 없다
        PersonalModelUpdateResponse response =
                skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(weightOf(SleepFeature.HRV)).isEqualByComparingTo("1.0000");
        assertThat(weightOf(SleepFeature.RESTING_HEART_RATE)).isEqualByComparingTo("1.0000");
        assertThat(response.changes()).extracting(WeightChangeResponse::feature)
                .doesNotContain(SleepFeature.HRV, SleepFeature.RESTING_HEART_RATE);
    }

    /** 대조하지 못한 지표의 피처는 오차 자체가 없다. */
    @Test
    @DisplayName("대조하지 않은 지표의 피처는 갱신하지 않는다")
    void 대조하지_않은_지표는_학습하지_않는다() {
        skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(weightOf(SleepFeature.DEEP_SLEEP)).isEqualByComparingTo("1.0000");
        assertThat(weightOf(SleepFeature.REM_SLEEP)).isEqualByComparingTo("1.0000");
    }

    /**
     * <b>버그가 아니다.</b> 오차가 0이면 어느 쪽으로도 배분할 것이 없다(§10.7).
     */
    @Test
    @DisplayName("예보가 정확히 맞은 날은 값이 움직이지 않는다 — 행은 만들어진다")
    void 오차가_없으면_값이_안_움직인다() {
        PersonalModelUpdateResponse response =
                skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 68)));

        assertThat(response.updated()).isTrue();          // 7행을 만들었다
        assertThat(response.changes()).isEmpty();
        assertThat(response.message()).contains("그대로 두었어요");
        assertThat(weightOf(SleepFeature.AWAKE_COUNT)).isEqualByComparingTo("1.0000");
    }

    /**
     * 클램프가 빠지면 검증 표본이 적은 초기에 우연한 오차 몇 번으로 한 피처가 지표를 지배한다.
     */
    @Test
    @DisplayName("이미 상한인 가중치는 더 올라가지 않는다")
    void 상한을_넘지_않는다() {
        seedWeights();
        weight(SleepFeature.AWAKE_COUNT).updateWeight(new BigDecimal("2.0000"));

        skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(weightOf(SleepFeature.AWAKE_COUNT)).isEqualByComparingTo("2.0000");
    }

    /** 앱이 한국어 이름을 따로 하드코딩하면 수면 통역 카드의 문구와 어긋난다. */
    @Test
    @DisplayName("변화마다 피처의 한국어 이름을 함께 내려준다")
    void 한국어_이름을_함께_준다() {
        PersonalModelUpdateResponse response =
                skinModelService.learn(USER_ID, session(null, null), List.of(darkCircle(68, 55)));

        assertThat(response.changes()).extracting(WeightChangeResponse::label)
                .contains("야간 각성", "총 수면 시간");
        assertThat(response.message()).contains("야간 각성");
    }

    @Test
    @DisplayName("대조한 지표가 하나도 없으면 학습하지 않는다")
    void 대조가_없으면_학습하지_않는다() {
        PersonalModelUpdateResponse response =
                skinModelService.learn(USER_ID, session(null, null), List.of());

        assertThat(response.updated()).isFalse();
        assertThat(saved).isEmpty();
    }

    /**
     * 워치를 찬 밤이면 혈색도 대조되므로 그 지표의 피처 3개 중 <b>참여한 것만</b> 움직여야 한다 —
     * 취침 규칙성은 이력 3일 미만이라 결측이다.
     *
     * <p>예보 {@code 62}는 임의의 숫자가 아니라 <b>그 밤의 실제 지표점수</b>다 —
     * HRV 60점과 안정시 심박 63.3점의 균등 가중평균(61.67)을 반올림한 값이다. 임의값을 넣으면
     * 두 피처가 같은 방향으로 밀려 §10.7의 "합이 0" 성질이 재현되지 않는다.
     */
    @Test
    @DisplayName("혈색은 참여한 두 피처만 움직이고 취침 규칙성은 그대로다")
    void 이력이_부족한_피처는_빠진다() {
        SleepSession worn = session(new BigDecimal("42.00"), 66);

        skinModelService.learn(USER_ID, worn,
                List.of(MetricVerificationResponse.of(SkinMetric.COMPLEXION, 62, 45)));

        assertThat(weightOf(SleepFeature.BEDTIME_REGULARITY)).isEqualByComparingTo("1.0000");

        // 실측이 나빴으므로 부분점수가 낮았던 HRV(60)의 비중이 오르고 안정시 심박(63.3)은 내려간다
        assertThat(weightOf(SleepFeature.HRV)).isGreaterThan(new BigDecimal("1.0000"));
        assertThat(weightOf(SleepFeature.RESTING_HEART_RATE)).isLessThan(new BigDecimal("1.0000"));
    }

    /**
     * 부분점수는 가중치를 곱하기 전 단계라 <b>가중치가 이미 움직인 뒤에도 같은 값이 나와야
     * 한다.</b> 이게 깨지면 §10.7이 부분점수를 저장하지 않기로 한 근거가 무너진다.
     */
    @Test
    @DisplayName("부분점수는 개인 가중치와 무관하다")
    void 부분점수는_가중치에_흔들리지_않는다() {
        SleepSession session = session(null, null);
        SkinScoringEngine engine = new SkinScoringEngine();

        Map<SleepFeature, Double> neutral =
                engine.featureScores(ScoringCommand.forFeatureScores(session, null));
        Map<SleepFeature, Double> weighted = engine.featureScores(new ScoringCommand(
                session.getAwakeCount(), session.getTotalSleepMinutes(),
                session.getDeepSleepMinutes(), session.getRemSleepMinutes(),
                session.stagedSleepMinutes(), null, session.getHrv(),
                session.getRestingHeartRate(),
                Map.of(SleepFeature.AWAKE_COUNT, new BigDecimal("2.0000"))));

        assertThat(weighted).isEqualTo(neutral);
    }

    // ===== 픽스처 =====

    private MetricVerificationResponse darkCircle(int forecast, int measured) {
        return MetricVerificationResponse.of(SkinMetric.DARK_CIRCLE, forecast, measured);
    }

    private void seedWeights() {
        saved.addAll(Arrays.stream(SleepFeature.values())
                .map(feature -> PersonalWeight.builder()
                        .userId(USER_ID).sleepFeature(feature)
                        .skinMetric(ScoringPolicy.metricOf(feature))
                        .weight(new BigDecimal("1.0000"))
                        .build())
                .toList());
    }

    private PersonalWeight weight(SleepFeature feature) {
        return saved.stream()
                .filter(row -> row.getSleepFeature() == feature)
                .findFirst()
                .orElseThrow(() -> new AssertionError("행이 없다: " + feature));
    }

    private BigDecimal weightOf(SleepFeature feature) {
        return weight(feature).getWeight();
    }

    /** 각성 3회(부분점수 50) · 총 수면 440분(100) · 단계 합 440분인 밤. */
    private SleepSession session(BigDecimal hrv, Integer restingHeartRate) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(BASE_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-06T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-06T22:00:00Z"))
                .totalSleepMinutes(440).deepSleepMinutes(40)
                .remSleepMinutes(90).coreSleepMinutes(310)
                .awakeCount(3).awakeMinutes(21)
                .hrv(hrv).restingHeartRate(restingHeartRate)
                .payloadHash("a".repeat(64))
                .build();
    }

}
