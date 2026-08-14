package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.report.dto.response.FeatureCorrelation;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 상관 강도 계산 (REP-06·07 "상관 강도" 섹션).
 *
 * <p>피어슨 상관계수는 부호 실수 하나로 값이 완전히 틀려지는 계산이라, 알려진 값으로 낸
 * 결과가 실제로 맞는지를 직접 확인한다 — 완전한 선형 관계에서 절댓값이 1에 가까운지,
 * 표본 5개 미만이면 계산 자체를 건너뛰는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorrelationCalculatorTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate PERIOD_START = BASE_DATE.minusDays(6);

    @Mock
    private BedtimeRegularityCalculator bedtimeRegularityCalculator;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;

    private CorrelationCalculator calculator() {
        return new CorrelationCalculator(bedtimeRegularityCalculator, skinMeasurementRepository);
    }

    /** 취침 규칙성 이력 부족(3일 미만)과 같은 조건 — 그 피처만 결측이 되게 한다. */
    @BeforeEach
    void 취침_규칙성은_기본적으로_결측이다() {
        given(bedtimeRegularityCalculator.calculate(anyLong(), any(), any())).willReturn(null);
    }

    /**
     * 각성 0~5회에 다크서클 실측이 정확히 100-10*count로 나오는 완전한 음의 선형 관계다.
     * 표본 6개 — 절댓값이 1에 아주 가까워야 하고(부동소수점 오차만 허용), VERY_STRONG이어야 한다.
     */
    @Test
    @DisplayName("완전한 선형 관계면 상관계수 절댓값이 1에 가깝고 VERY_STRONG이다")
    void 완전상관이면_VERY_STRONG이고_절댓값이_1에_가깝다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.insufficientSample()).isFalse();
        assertThat(result.sampleSize()).isEqualTo(6);
        assertThat(result.strength()).isEqualTo(CorrelationStrength.VERY_STRONG);
    }

    /**
     * 완전한 양의 선형 관계 — 방향(부호)이 반대여도 강도는 절댓값 기준이라 똑같이
     * VERY_STRONG이어야 한다. 부호를 버린다는 설계를 직접 확인한다.
     */
    @Test
    @DisplayName("완전한 양의 상관관계도 VERY_STRONG이다 — 강도는 부호와 무관하다")
    void 완전한_양의_상관도_VERY_STRONG이다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            verifiedDays.add(verifiedDay(date, 50 + i * 10, 60, 80)); // 각성이 늘수록 다크서클도 증가(가상)
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.strength()).isEqualTo(CorrelationStrength.VERY_STRONG);
    }

    @Test
    @DisplayName("유효 표본이 5 미만이면 insufficientSample이고 strength는 null이다")
    void 표본이_5_미만이면_insufficientSample이다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // MIN_SAMPLE_SIZE(5) 미만
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.insufficientSample()).isTrue();
        assertThat(result.strength()).isNull();
        assertThat(result.sampleSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("정확히 5개면 insufficientSample이 아니다 — 경계값")
    void 정확히_5개면_충분하다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.insufficientSample()).isFalse();
        assertThat(result.strength()).isNotNull();
    }

    /** 항상 7쌍 전부 반환한다 — 계산할 데이터가 아예 없어도 마찬가지다. */
    @Test
    @DisplayName("데이터가 없어도 7개 전부 insufficientSample로 반환한다")
    void 데이터가_없어도_7개를_반환한다() {
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(List.of());

        List<FeatureCorrelation> results = calculator().calculate(
                USER_ID, PERIOD_START, BASE_DATE, Map.of());

        assertThat(results).hasSize(7);
        assertThat(results).allMatch(FeatureCorrelation::insufficientSample);
        assertThat(results).extracting(FeatureCorrelation::sleepFeature)
                .containsExactlyInAnyOrder(SleepFeature.values());
    }

    /**
     * <b>정렬은 상관계수 절댓값 내림차순이고, 표본 부족은 배열 맨 뒤로 간다.</b> 각성 횟수는
     * 완전 상관(표본 6개)을, 총 수면 시간은 표본 4개(부족)를 주고 나머지는 전부 데이터가
     * 없게 만들어 정렬 결과를 직접 확인한다.
     */
    @Test
    @DisplayName("상관계수 절댓값 내림차순이고 표본 부족은 배열 맨 뒤다")
    void 정렬_규칙을_지킨다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        List<FeatureCorrelation> results = calculator().calculate(USER_ID, PERIOD_START, BASE_DATE, sessions);

        // 완전 상관을 준 AWAKE_COUNT가 맨 앞이어야 한다
        assertThat(results.getFirst().sleepFeature()).isEqualTo(SleepFeature.AWAKE_COUNT);
        assertThat(results.getFirst().insufficientSample()).isFalse();

        // 표본 부족(계산 불가) 항목들은 뒤로 몰려야 하고, sufficient 항목보다 항상 뒤에 있어야 한다
        int firstInsufficientIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).insufficientSample()) {
                firstInsufficientIndex = i;
                break;
            }
        }
        assertThat(firstInsufficientIndex).isGreaterThan(0);
        for (int i = firstInsufficientIndex; i < results.size(); i++) {
            assertThat(results.get(i).insufficientSample()).isTrue();
        }
    }

    /**
     * 워치를 착용하지 않아 HRV·안정시 심박이 결측인 날은 <b>그 피처의 계산에서만</b> 빠진다 —
     * 각성 횟수처럼 항상 있는 피처는 영향을 받지 않는다.
     */
    @Test
    @DisplayName("워치 미착용으로 HRV가 없는 날은 그 피처 계산에서만 제외된다")
    void HRV가_없는_날은_그_피처만_제외된다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            // 짝수 날만 워치 착용(hrv 존재) — HRV 표본은 3개뿐이라 부족, AWAKE_COUNT 표본은 6개
            sessions.put(date, session(date, i, i % 2 == 0));
            verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        List<FeatureCorrelation> results = calculator().calculate(USER_ID, PERIOD_START, BASE_DATE, sessions);

        FeatureCorrelation hrv = byFeature(results, SleepFeature.HRV);
        FeatureCorrelation awakeCount = byFeature(results, SleepFeature.AWAKE_COUNT);

        assertThat(hrv.sampleSize()).isEqualTo(3);
        assertThat(hrv.insufficientSample()).isTrue();
        assertThat(awakeCount.sampleSize()).isEqualTo(6);
        assertThat(awakeCount.insufficientSample()).isFalse();
    }

    /**
     * 세션이 있어도 그날 셀피 검증(실측)이 없으면 짝을 만들 수 없다 — 예보만 있고 검증하지
     * 않은 날은 계산에서 빠져야 한다.
     */
    @Test
    @DisplayName("세션은 있지만 검증하지 않은 날은 표본에서 빠진다")
    void 검증하지_않은_날은_빠진다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, i));
            if (i < 5) { // 처음 5일만 검증했다 — 나머지 3일은 세션만 있고 실측이 없다
                verifiedDays.add(verifiedDay(date, 100 - i * 10, 60, 80));
            }
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.sampleSize()).isEqualTo(5);
    }

    /**
     * 두 변수 중 하나라도 분산이 0이면(표본 안에서 값이 전부 같으면) 피어슨 상관계수가
     * 수학적으로 정의되지 않는다. 예외 없이 0(무관, WEAK)으로 처리하는지 확인한다.
     */
    @Test
    @DisplayName("분산이 0이면 예외 없이 WEAK로 처리한다")
    void 분산이_0이면_WEAK다() {
        Map<LocalDate, SleepSession> sessions = new LinkedHashMap<>();
        List<VerifiedDay> verifiedDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate date = PERIOD_START.plusDays(i);
            sessions.put(date, session(date, 2)); // 각성 횟수가 항상 2회로 고정 — 분산 0
            verifiedDays.add(verifiedDay(date, 70, 60, 80)); // 다크서클도 항상 70 — 분산 0
        }
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(verifiedDays);

        FeatureCorrelation result = awakeCountCorrelation(sessions);

        assertThat(result.insufficientSample()).isFalse();
        assertThat(result.strength()).isEqualTo(CorrelationStrength.WEAK);
    }

    /** 라벨이 채워지는지 — 프론트가 한국어 이름을 하드코딩하지 않게 하는 계약이다. */
    @Test
    @DisplayName("피처·지표 라벨이 함께 내려간다")
    void 라벨이_채워진다() {
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(List.of());

        List<FeatureCorrelation> results = calculator().calculate(
                USER_ID, PERIOD_START, BASE_DATE, Map.of());

        FeatureCorrelation awakeCount = byFeature(results, SleepFeature.AWAKE_COUNT);
        assertThat(awakeCount.featureLabel()).isEqualTo("야간 각성");
        assertThat(awakeCount.metricLabel()).isEqualTo("다크서클");
        assertThat(awakeCount.skinMetric()).isEqualTo(SkinMetric.DARK_CIRCLE);
    }

    // ===== 픽스처 =====

    private FeatureCorrelation awakeCountCorrelation(Map<LocalDate, SleepSession> sessions) {
        List<FeatureCorrelation> results = calculator().calculate(USER_ID, PERIOD_START, BASE_DATE, sessions);
        return byFeature(results, SleepFeature.AWAKE_COUNT);
    }

    private FeatureCorrelation byFeature(List<FeatureCorrelation> results, SleepFeature feature) {
        return results.stream().filter(r -> r.sleepFeature() == feature).findFirst().orElseThrow();
    }

    private static VerifiedDay verifiedDay(LocalDate date, int measuredDarkCircle,
                                           int measuredComplexion, int measuredBarrier) {
        return new VerifiedDay(date, 70, 60, 80, measuredDarkCircle, measuredComplexion, measuredBarrier);
    }

    private static SleepSession session(LocalDate sleepDate, int awakeCount) {
        return session(sleepDate, awakeCount, true);
    }

    private static SleepSession session(LocalDate sleepDate, int awakeCount, boolean watchWorn) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(sleepDate)
                .sleepOnsetTime(sleepDate.atTime(23, 40).atOffset(ZoneOffset.UTC))
                .wakeTime(sleepDate.plusDays(1).atTime(7, 10).atOffset(ZoneOffset.UTC))
                .totalSleepMinutes(432).deepSleepMinutes(126)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(awakeCount).awakeMinutes(7)
                .hrv(watchWorn ? new BigDecimal("42.00") : null)
                .restingHeartRate(watchWorn ? 55 : null)
                .payloadHash("a".repeat(64))
                .build();
    }

}
