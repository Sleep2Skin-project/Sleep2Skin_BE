package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.ReportPeriodStatus;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.DailyScore;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
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
 * 여기서는 {@code DailySleepScoreCalculator}를 스텁으로 두고 <b>기간 조립·평균·적중률
 * 필터링</b>만 본다.
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
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private DailySleepScoreCalculator dailySleepScoreCalculator;

    private WeeklyReportService service() {
        return new WeeklyReportService(userRepository, sleepSessionRepository,
                skinMeasurementRepository, dailySleepScoreCalculator);
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
    }

    @Test
    @DisplayName("가입 당일을 포함해 정확히 7일차면 FULL이다")
    void 가입_7일차부터_FULL이다() {
        joinedOn(BASE_DATE.minusDays(6)); // 가입 당일 포함 7일차
        noSessions();
        noVerifications();

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
        noVerifications();

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
        noVerifications();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        // (60+80)/2 = 70 — 나머지 5일은 세션이 없어 null이라 분모에서 빠진다
        assertThat(response.summary().avgSleepScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("전부 결측이면 avgSleepScore도 null이다")
    void 전부_결측이면_평균도_null이다() {
        joinedLongAgo();
        noSessions();
        noVerifications();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().avgSleepScore()).isNull();
    }

    /** <b>분모는 검증 일수 × 3이 아니라 실제 판정한 지표 수다.</b> */
    @Test
    @DisplayName("hitRate는 지표 기준이고 기간 밖 검증일은 제외한다")
    void 적중률은_지표_기준이고_기간을_넘지_않는다() {
        joinedLongAgo();
        noSessions();
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(List.of(
                // 기간 안 — 3개 중 2개 적중(±5)
                new VerifiedDay(BASE_DATE, 67, 62, 81, 65, 60, 60),
                // 기간 밖(periodStart 하루 전) — 제외돼야 한다
                new VerifiedDay(PERIOD_START.minusDays(1), 67, 62, 81, 40, 30, 40)));

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().hitRate()).isEqualTo(67); // 3개 중 2개 = 67%
        assertThat(response.summary().verifiedDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("혈색 예보가 없던 날은 분모에서 그 지표가 빠진다")
    void 빈_지표는_분모에서_빠진다() {
        joinedLongAgo();
        noSessions();
        // 혈색(forecastComplexion) 예보가 없다 — 판정은 다크서클·장벽 2개뿐, 둘 다 적중
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE))
                .willReturn(List.of(new VerifiedDay(BASE_DATE, 67, null, 81, 65, 55, 80)));

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().hitRate()).isEqualTo(100); // 3이 분모였다면 67이다
    }

    @Test
    @DisplayName("검증이 없으면 hitRate는 null이고 verifiedDays는 0이다")
    void 검증이_없으면_적중률은_null이다() {
        joinedLongAgo();
        noSessions();
        noVerifications();

        WeeklyReportResponse response = service().getWeeklyReport(USER_ID, BASE_DATE);

        assertThat(response.summary().hitRate()).isNull();
        assertThat(response.summary().verifiedDays()).isEqualTo(0);
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

    private void noVerifications() {
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE)).willReturn(List.of());
    }

    private static SleepSession session(LocalDate sleepDate) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(sleepDate)
                .sleepOnsetTime(sleepDate.atTime(23, 40).atOffset(ZoneOffset.UTC))
                .wakeTime(sleepDate.plusDays(1).atTime(7, 10).atOffset(ZoneOffset.UTC))
                .totalSleepMinutes(432).deepSleepMinutes(126)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(2).awakeMinutes(7)
                .hrv(new BigDecimal("42.00")).restingHeartRate(55)
                .payloadHash("a".repeat(64))
                .build();
    }

}
