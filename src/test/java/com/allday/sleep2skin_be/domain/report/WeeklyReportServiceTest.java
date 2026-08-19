package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.report.dto.ReportPeriodStatus;
import com.allday.sleep2skin_be.domain.report.dto.response.CorrelationGroup;
import com.allday.sleep2skin_be.domain.report.dto.response.FeatureCorrelation;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.DailyScore;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

/**
 * 주간 리포트.
 *
 * <p>{@code sleepScore} 계산 자체는 {@link DailySleepScoreCalculatorTest}가 이미 검증했으므로
 * 여기서는 {@code DailySleepScoreCalculator}를 스텁으로 두고 <b>기간 조립·평균</b>만 본다.
 *
 * <p>적중률(hitRate·verifiedDays) 관련 테스트는 2026-08-15에 화면에서 빠지면서 함께
 * 제거했다 — {@code SkinMeasurementRepository}를 더 이상 직접 참조하지 않는다(다만
 * {@code CorrelationCalculator}를 통해 간접적으로는 다시 쓰인다).
 *
 * <p>상관 강도 계산 자체는 {@link CorrelationCalculatorTest}가 검증하므로 여기서는
 * {@code CorrelationCalculator}를 스텁으로 두고 <b>결과를 응답에 그대로 실어 보내는지</b>만
 * 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeeklyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate PERIOD_START = BASE_DATE.minusDays(6);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private DailySleepScoreCalculator dailySleepScoreCalculator;
    @Mock
    private CorrelationCalculator correlationCalculator;

    private WeeklyReportService service() {
        return new WeeklyReportService(userRepository, sleepSessionRepository,
                dailySleepScoreCalculator, correlationCalculator);
    }

    /**
     * <b>Mockito는 {@code Integer} 반환 메서드의 언스텁 호출에도 {@code null}이 아니라
     * {@code 0}을 기본값으로 준다</b>({@code ReturnsEmptyValues}가 래퍼 타입을 원시 타입처럼
     * 취급한다). 세션이 없는 날은 {@code sessions.get(date)}가 {@code null}이라 실제
     * {@code DailySleepScoreCalculator}라면 즉시 {@code null}을 반환하지만, 여기선 목이라 그
     * 분기를 타지 않는다 — 그래서 {@code session == null}일 때의 반환값을 직접 고정해둔다.
     */
    @BeforeEach
    void 세션이_없는_날은_점수도_없다() {
        given(dailySleepScoreCalculator.calculate(anyLong(), any(), isNull())).willReturn(null);
    }

    /**
     * <b>{@code CorrelationGroup.groupBySkinMetric}은 그룹당 대표가 없으면 예외를 던진다</b>
     * (§10.3 FEATURE_METRIC_PAIRS 불변식). 언스텁 상태의 목은 빈 리스트를 반환하는데, 이 값을
     * 그대로 두면 상관 강도를 직접 검증하지 않는 다른 테스트들까지 이 예외로 실패한다 — 그래서
     * 3개 지표를 전부 채운 기본값을 깔아 둔다. 상관 강도 자체를 검증하는 테스트는 이 스텁을
     * 재정의(re-stub)해서 쓴다.
     */
    @BeforeEach
    void 상관_강도는_기본적으로_3개_지표를_전부_채운다() {
        given(correlationCalculator.calculate(anyLong(), any(), any(), any())).willReturn(List.of(
                new FeatureCorrelation(SleepFeature.AWAKE_COUNT, "야간 각성",
                        SkinMetric.DARK_CIRCLE, "다크서클 회복", CorrelationStrength.WEAK, 6, false),
                new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                        SkinMetric.COMPLEXION, "혈색", CorrelationStrength.WEAK, 6, false),
                new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                        SkinMetric.BARRIER, "장벽", CorrelationStrength.WEAK, 6, false)));
    }

    @Test
    @DisplayName("가입한 지 7일 미만이면 INSUFFICIENT_DATA다")
    void 가입_7일_미만이면_빈_상태다() {
        joinedOn(BASE_DATE.minusDays(5)); // 가입 당일 포함 6일차

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(ReportPeriodStatus.INSUFFICIENT_DATA);
        assertThat(response.periodStart()).isEqualTo(PERIOD_START);
        assertThat(response.periodEnd()).isEqualTo(BASE_DATE);
        assertThat(response.dailyScores()).isEmpty();
        assertThat(response.summary()).isNull();
        assertThat(response.correlations()).isEmpty();
    }

    @Test
    @DisplayName("가입 당일을 포함해 정확히 7일차면 FULL이다")
    void 가입_7일차부터_FULL이다() {
        joinedOn(BASE_DATE.minusDays(6)); // 가입 당일 포함 7일차
        noSessions();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(ReportPeriodStatus.FULL);
    }

    @Test
    @DisplayName("7일치 dailyScores를 항상 반환하고 세션 없는 날은 null이다")
    void 일별_점수를_반환한다() {
        joinedLongAgo();
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(List.of(session(PERIOD_START)));
        given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(PERIOD_START), any()))
                .willReturn(62);

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.dailyScores()).hasSize(7);
        assertThat(response.dailyScores().get(0)).isEqualTo(new DailyScore(PERIOD_START, 62));
        assertThat(response.dailyScores().get(1).sleepScore()).isNull(); // 그날은 세션이 없다
        assertThat(response.dailyScores().get(6).date()).isEqualTo(BASE_DATE);
    }

    @Test
    @DisplayName("avgSleepScore는 null인 날짜를 제외한 평균이다")
    void 평균은_결측을_제외한다() {
        joinedLongAgo();
        LocalDate day1 = PERIOD_START;
        LocalDate day2 = PERIOD_START.plusDays(1);
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(List.of(session(day1), session(day2)));
        given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(day1), any())).willReturn(60);
        given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(day2), any())).willReturn(80);

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        // (60+80)/2 = 70 — 나머지 5일은 세션이 없어 null이라 분모에서 빠진다
        assertThat(response.summary().avgSleepScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("전부 결측이면 avgSleepScore도 null이다")
    void 전부_결측이면_평균도_null이다() {
        joinedLongAgo();
        noSessions();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().avgSleepScore()).isNull();
    }

    /**
     * <b>avgSleepScore와 별개로 계산되지만 같은 결측 처리를 쓴다.</b> 세션이 있는 날짜의
     * {@code deepSleepMinutes}만 평균낸다 — 세션이 없는 5일은 분모에서 빠진다.
     */
    @Test
    @DisplayName("avgDeepSleepMinutes는 세션이 있는 날짜만의 평균이다")
    void 깊은수면_평균은_세션이_있는_날짜만_쓴다() {
        joinedLongAgo();
        LocalDate day1 = PERIOD_START;
        LocalDate day2 = PERIOD_START.plusDays(1);
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(List.of(session(day1, 100), session(day2, 150)));

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        // (100+150)/2 = 125 — 나머지 5일은 세션이 없어 분모에서 빠진다
        assertThat(response.summary().avgDeepSleepMinutes()).isEqualTo(125);
    }

    @Test
    @DisplayName("전부 결측이면 avgDeepSleepMinutes도 null이다")
    void 깊은수면_전부_결측이면_평균도_null이다() {
        joinedLongAgo();
        noSessions();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().avgDeepSleepMinutes()).isNull();
    }

    /**
     * <b>계산 자체가 아니라 배선(wiring)을 확인한다.</b> 상관계수 계산 로직은
     * {@code CorrelationCalculatorTest}가 검증하고, 여기서는 서비스가 그 결과를 가공하지
     * 않고 {@code skinMetric} 기준 3그룹 대표 1개씩으로만 묶어 응답에 싣는지 본다 — 기간
     * (periodStart~baseDate)과 세션 맵을 계산기에 그대로 넘기는지도 함께 확인한다.
     *
     * <p>{@code CorrelationGroup.groupBySkinMetric}이 그룹당 대표가 없으면 예외를 던지므로
     * (§10.3 FEATURE_METRIC_PAIRS 불변식), 스텁도 3개 지표 전부에 최소 1개씩 채워 넣는다.
     */
    @Test
    @DisplayName("CorrelationCalculator의 결과를 skinMetric 기준 대표 1개씩으로 묶어 싣는다")
    void 상관_강도를_그룹으로_묶어_싣는다() {
        joinedLongAgo();
        noSessions();
        FeatureCorrelation darkCircleCorrelation = new FeatureCorrelation(SleepFeature.AWAKE_COUNT, "야간 각성",
                SkinMetric.DARK_CIRCLE, "다크서클 회복", CorrelationStrength.VERY_STRONG, 6, false);
        FeatureCorrelation complexionCorrelation = new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                SkinMetric.COMPLEXION, "혈색", null, 2, true);
        FeatureCorrelation barrierCorrelation = new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                SkinMetric.BARRIER, "장벽", CorrelationStrength.MODERATE, 6, false);
        given(correlationCalculator.calculate(eq(USER_ID), eq(PERIOD_START), eq(BASE_DATE), any()))
                .willReturn(List.of(darkCircleCorrelation, complexionCorrelation, barrierCorrelation));

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.correlations()).hasSize(3);
        assertThat(response.correlations()).extracting(CorrelationGroup::skinMetric)
                .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER);
        CorrelationGroup darkCircleGroup = response.correlations().stream()
                .filter(group -> group.skinMetric() == SkinMetric.DARK_CIRCLE).findFirst().orElseThrow();
        assertThat(darkCircleGroup.topCorrelation()).isEqualTo(darkCircleCorrelation);
        CorrelationGroup complexionGroup = response.correlations().stream()
                .filter(group -> group.skinMetric() == SkinMetric.COMPLEXION).findFirst().orElseThrow();
        assertThat(complexionGroup.topCorrelation()).isEqualTo(complexionCorrelation);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().getWeeklyReport(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===== 픽스처 =====

    private void joinedOn(LocalDate createdDate) {
        User user = User.builder().nickname("tester").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "createdAt", createdDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private void joinedLongAgo() {
        joinedOn(BASE_DATE.minusYears(1));
    }

    private void noSessions() {
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(List.of());
    }

    private static SleepSession session(LocalDate sleepDate) {
        return session(sleepDate, 126);
    }

    private static SleepSession session(LocalDate sleepDate, int deepSleepMinutes) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(sleepDate)
                .sleepOnsetTime(sleepDate.atTime(23, 40).atOffset(ZoneOffset.UTC))
                .wakeTime(sleepDate.plusDays(1).atTime(7, 10).atOffset(ZoneOffset.UTC))
                .totalSleepMinutes(432).deepSleepMinutes(deepSleepMinutes)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(2).awakeMinutes(7)
                .hrv(new BigDecimal("42.00")).restingHeartRate(55)
                .payloadHash("a".repeat(64))
                .build();
    }

}
