package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import com.allday.sleep2skin_be.domain.report.dto.ReportPeriodStatus;
import com.allday.sleep2skin_be.domain.report.dto.response.CorrelationGroup;
import com.allday.sleep2skin_be.domain.report.dto.response.FeatureCorrelation;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse.WeekScore;
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
import java.util.ArrayList;
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
 * 월간 리포트.
 *
 * <p>{@code sleepScore} 계산 자체는 {@link DailySleepScoreCalculatorTest}가 검증했으므로
 * 여기서는 {@code DailySleepScoreCalculator}를 스텁으로 두고 <b>4주 분할·최고 주 판정·28일
 * 전체 평균이 주 평균의 평균과 다르다는 것</b>을 본다.
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
class MonthlyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate PERIOD_START = BASE_DATE.minusDays(27);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private DailySleepScoreCalculator dailySleepScoreCalculator;
    @Mock
    private CorrelationCalculator correlationCalculator;

    private MonthlyReportService service() {
        return new MonthlyReportService(userRepository, sleepSessionRepository,
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
                        SkinMetric.DARK_CIRCLE, "다크서클 회복", CorrelationStrength.WEAK, 22, false),
                new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                        SkinMetric.COMPLEXION, "혈색", CorrelationStrength.WEAK, 22, false),
                new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                        SkinMetric.BARRIER, "장벽", CorrelationStrength.WEAK, 22, false)));
    }

    @Test
    @DisplayName("가입한 지 28일 미만이면 INSUFFICIENT_DATA다")
    void 가입_28일_미만이면_빈_상태다() {
        joinedOn(BASE_DATE.minusDays(26)); // 가입 당일 포함 27일차

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(ReportPeriodStatus.INSUFFICIENT_DATA);
        assertThat(response.periodStart()).isEqualTo(PERIOD_START);
        assertThat(response.periodEnd()).isEqualTo(BASE_DATE);
        assertThat(response.weeks()).isEmpty();
        assertThat(response.summary()).isNull();
        assertThat(response.correlations()).isEmpty();
    }

    @Test
    @DisplayName("가입 당일을 포함해 정확히 28일차면 FULL이다")
    void 가입_28일차부터_FULL이다() {
        joinedOn(BASE_DATE.minusDays(27)); // 가입 당일 포함 28일차
        noSessions();

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(ReportPeriodStatus.FULL);
    }

    /**
     * 명세 예시(65·58·52·70)를 그대로 재현한다. 각 주 7일을 같은 값으로 채우면 그 값이 곧
     * 주 평균이라 W1~W4 라벨과 최고 주 판정을 직접 검증할 수 있다.
     */
    @Test
    @DisplayName("W1이 가장 과거이고 W4가 baseDate를 포함한 최근 7일이며, 최고 주만 isHighest다")
    void 주별_라벨과_최고_주를_판정한다() {
        joinedLongAgo();
        stubUniformWeeks(65, 58, 52, 70);

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        // stubUniformWeeks가 쓰는 session() 픽스처는 deepSleepMinutes를 항상 126으로 고정한다
        assertThat(response.weeks()).containsExactly(
                new WeekScore("W1", 65, 126, false),
                new WeekScore("W2", 58, 126, false),
                new WeekScore("W3", 52, 126, false),
                new WeekScore("W4", 70, 126, true));
    }

    @Test
    @DisplayName("최고 평균이 동점이면 동점인 주 모두 isHighest다")
    void 동점이면_모두_true다() {
        joinedLongAgo();
        stubUniformWeeks(70, 50, 70, 40);

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.weeks()).extracting(WeekScore::isHighest)
                .containsExactly(true, false, true, false);
    }

    @Test
    @DisplayName("모든 주가 null이면 모든 isHighest가 false다")
    void 전부_null이면_최고_주도_없다() {
        joinedLongAgo();
        noSessions();

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.weeks()).extracting(WeekScore::avgSleepScore)
                .containsExactly(null, null, null, null);
        assertThat(response.weeks()).extracting(WeekScore::isHighest)
                .containsExactly(false, false, false, false);
    }

    /**
     * <b>28일 전체 평균은 주 평균 4개의 평균이 아니다.</b> 이 테스트가 그 차이를 직접 보여준다 —
     * 주마다 결측 일수가 다르면 두 계산이 다른 숫자를 낸다.
     *
     * <p>W1은 하루만 100점, 나머지 6일 결측 → 주 평균 100. W2는 7일 전부 0점 → 주 평균 0.
     * W3·W4는 전부 결측 → 주 평균 {@code null}.
     *
     * <p>주 평균의 평균(결측 주 제외)이면 {@code (100+0)/2 = 50}이지만, 28일 전체를 한 번에
     * 평균내면 <b>결측이 아닌 값 8개({@code 100} 하나 + {@code 0} 일곱)의 평균인
     * {@code 100/8 = 12.5 → 반올림 13}</b>이다. 명세가 "28일 전부 결측이면 null"이라고 28일
     * 단위로 조건을 걸었으므로 후자가 맞다.
     */
    @Test
    @DisplayName("summary.avgSleepScore는 28일 전체 평균이지 주 평균의 평균이 아니다")
    void 전체_평균은_28일_단위로_낸다() {
        joinedLongAgo();

        LocalDate week1FirstDay = PERIOD_START;
        List<SleepSession> sessions = new ArrayList<>();
        sessions.add(session(week1FirstDay));
        given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(week1FirstDay), any()))
                .willReturn(100);

        LocalDate week2Start = PERIOD_START.plusDays(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = week2Start.plusDays(i);
            sessions.add(session(day));
            given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(day), any())).willReturn(0);
        }

        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(sessions);

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.weeks().get(0).avgSleepScore()).isEqualTo(100); // W1
        assertThat(response.weeks().get(1).avgSleepScore()).isEqualTo(0);   // W2
        assertThat(response.weeks().get(2).avgSleepScore()).isNull();       // W3
        assertThat(response.weeks().get(3).avgSleepScore()).isNull();       // W4

        assertThat(response.summary().avgSleepScore()).isEqualTo(13); // 28일 단위, 50이 아니다
    }

    /**
     * {@code avgSleepScore}와 별개로 계산되지만 같은 방식(그 주 7일 평균, 결측 제외)을 쓴다.
     * 주마다 다른 값을 줘서 주별로 올바르게 갈리는지 확인한다.
     */
    @Test
    @DisplayName("각 주의 avgDeepSleepMinutes는 그 주 7일의 평균이다")
    void 주별_깊은수면_평균을_계산한다() {
        joinedLongAgo();
        List<SleepSession> sessions = new ArrayList<>();
        int[] deepSleepPerWeek = {110, 105, 98, 132};
        for (int week = 0; week < 4; week++) {
            LocalDate weekStart = PERIOD_START.plusDays((long) week * 7);
            for (int i = 0; i < 7; i++) {
                LocalDate day = weekStart.plusDays(i);
                sessions.add(session(day, deepSleepPerWeek[week]));
                given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(day), any())).willReturn(60);
            }
        }
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(sessions);

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.weeks()).extracting(WeekScore::avgDeepSleepMinutes)
                .containsExactly(110, 105, 98, 132);
        // (110+105+98+132)/4 = 111.25 → 반올림 111 — 28일 단위 평균이며 4주 다 같은 표본 수라
        // 주 평균의 평균과 같은 값이 나온다(달라지는 경우는 sleepScore 테스트가 이미 검증함)
        assertThat(response.summary().avgDeepSleepMinutes()).isEqualTo(111);
    }

    @Test
    @DisplayName("월간도 전부 결측이면 avgDeepSleepMinutes가 null이다")
    void 월간_깊은수면_전부_결측이면_평균도_null이다() {
        joinedLongAgo();
        noSessions();

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().avgDeepSleepMinutes()).isNull();
        assertThat(response.weeks()).extracting(WeekScore::avgDeepSleepMinutes)
                .containsExactly(null, null, null, null);
    }

    /**
     * <b>계산 자체가 아니라 배선(wiring)을 확인한다.</b> 상관계수 계산 로직은
     * {@code CorrelationCalculatorTest}가 검증하고, 여기서는 서비스가 그 결과를
     * {@code skinMetric} 기준 대표 1개씩으로만 묶어 응답에 싣는지 본다.
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
                SkinMetric.DARK_CIRCLE, "다크서클 회복", CorrelationStrength.STRONG, 22, false);
        FeatureCorrelation complexionCorrelation = new FeatureCorrelation(SleepFeature.HRV, "심박변이도",
                SkinMetric.COMPLEXION, "혈색", CorrelationStrength.WEAK, 22, false);
        FeatureCorrelation barrierCorrelation = new FeatureCorrelation(SleepFeature.DEEP_SLEEP, "깊은 수면",
                SkinMetric.BARRIER, "장벽", CorrelationStrength.MODERATE, 22, false);
        given(correlationCalculator.calculate(eq(USER_ID), eq(PERIOD_START), eq(BASE_DATE), any()))
                .willReturn(List.of(darkCircleCorrelation, complexionCorrelation, barrierCorrelation));

        MonthlyReportResponse response = service().getMonthlyReport(USER_ID, BASE_DATE);

        assertThat(response.correlations()).hasSize(3);
        assertThat(response.correlations()).extracting(CorrelationGroup::skinMetric)
                .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER);
        CorrelationGroup darkCircleGroup = response.correlations().stream()
                .filter(group -> group.skinMetric() == SkinMetric.DARK_CIRCLE).findFirst().orElseThrow();
        assertThat(darkCircleGroup.topCorrelation()).isEqualTo(darkCircleCorrelation);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMonthlyReport(USER_ID, BASE_DATE))
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

    /** 4주 각 7일을 같은 값으로 채운다 — 그 값이 곧 그 주의 평균이 된다. */
    private void stubUniformWeeks(int w1, int w2, int w3, int w4) {
        List<SleepSession> sessions = new ArrayList<>();
        int[] weekValues = {w1, w2, w3, w4};
        for (int week = 0; week < 4; week++) {
            LocalDate weekStart = PERIOD_START.plusDays((long) week * 7);
            for (int i = 0; i < 7; i++) {
                LocalDate day = weekStart.plusDays(i);
                sessions.add(session(day));
                given(dailySleepScoreCalculator.calculate(eq(USER_ID), eq(day), any()))
                        .willReturn(weekValues[week]);
            }
        }
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(sessions);
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
