package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 일간 리포트 (REP-02·04·05).
 *
 * <p>{@code sleepScore} 계산 자체({@code s(f)} 평균)는 {@link DailySleepScoreCalculatorTest}가
 * 진짜 {@code SkinScoringEngine}으로 검증한다. 여기서는 <b>오케스트레이션</b>만 본다 —
 * {@code DailySleepScoreCalculator}를 스텁으로 두고 두 섹션의 독립적인 빈 상태 판정·전일 대비
 * 계산·사용자 검증에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final Integer STUBBED_SLEEP_SCORE = 70;

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private DailySleepScoreCalculator dailySleepScoreCalculator;

    private DailyReportService service() {
        return new DailyReportService(userRepository, sleepSessionRepository,
                skinForecastRepository, dailySleepScoreCalculator);
    }

    @Test
    @DisplayName("세션과 예보가 모두 있으면 두 섹션 다 AVAILABLE이고 전일 대비를 계산한다")
    void 둘_다_있으면_AVAILABLE이다() {
        userExists();
        SleepSession session = session(new BigDecimal("42.00"), 55);
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session));
        given(dailySleepScoreCalculator.calculate(USER_ID, BASE_DATE, session))
                .willReturn(STUBBED_SLEEP_SCORE);
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(forecast(BASE_DATE, 44, 63, 79)));
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE.minusDays(1)))
                .willReturn(Optional.of(forecast(BASE_DATE.minusDays(1), 43, 56, null)));

        DailyReportResponse response = service().getDailyReport(USER_ID, BASE_DATE);

        assertThat(response.baseDate()).isEqualTo(BASE_DATE);

        assertThat(response.sleepSummary().status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.sleepSummary().summary().totalSleepMinutes()).isEqualTo(432);
        assertThat(response.sleepSummary().summary().deepSleepMinutes()).isEqualTo(126);
        assertThat(response.sleepSummary().summary().lightSleepMinutes()).isEqualTo(270); // coreSleepMinutes
        assertThat(response.sleepSummary().summary().awakeCount()).isEqualTo(2);
        assertThat(response.sleepSummary().summary().awakeMinutes()).isEqualTo(7);
        assertThat(response.sleepSummary().summary().sleepScore()).isEqualTo(STUBBED_SLEEP_SCORE);

        assertThat(response.skinForecast().status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.skinForecast().darkCircle().today()).isEqualTo(44);
        assertThat(response.skinForecast().darkCircle().diffFromYesterday()).isEqualTo(1);
        assertThat(response.skinForecast().complexion().today()).isEqualTo(63);
        assertThat(response.skinForecast().complexion().diffFromYesterday()).isEqualTo(7);
        assertThat(response.skinForecast().barrier().today()).isEqualTo(79);
        // 전날 barrier가 없어 "변화 없음"이 아니라 "비교 불가" — null이어야 한다
        assertThat(response.skinForecast().barrier().diffFromYesterday()).isNull();
    }

    @Test
    @DisplayName("수면 세션이 없으면 sleepSummary만 빈 상태다 — skinForecast는 별도로 산다")
    void 세션이_없으면_수면_섹션만_빈_상태다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(forecast(BASE_DATE, 44, 63, 79)));
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE.minusDays(1)))
                .willReturn(Optional.empty());

        DailyReportResponse response = service().getDailyReport(USER_ID, BASE_DATE);

        assertThat(response.sleepSummary().status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.sleepSummary().message()).isNotBlank();
        assertThat(response.sleepSummary().summary()).isNull();

        assertThat(response.skinForecast().status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.skinForecast().darkCircle().today()).isEqualTo(44);
        // 전날 예보 자체가 없어 diff는 null이다
        assertThat(response.skinForecast().darkCircle().diffFromYesterday()).isNull();

        verify(dailySleepScoreCalculator, never()).calculate(anyLong(), any(), any());
    }

    @Test
    @DisplayName("예보가 없으면 skinForecast만 빈 상태다 — sleepSummary는 별도로 산다")
    void 예보가_없으면_예보_섹션만_빈_상태다() {
        userExists();
        SleepSession session = session(new BigDecimal("42.00"), 55);
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session));
        given(dailySleepScoreCalculator.calculate(USER_ID, BASE_DATE, session))
                .willReturn(STUBBED_SLEEP_SCORE);
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        DailyReportResponse response = service().getDailyReport(USER_ID, BASE_DATE);

        assertThat(response.sleepSummary().status()).isEqualTo(QueryStatus.AVAILABLE);

        assertThat(response.skinForecast().status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.skinForecast().message()).isNotBlank();
        assertThat(response.skinForecast().darkCircle().today()).isNull();
        assertThat(response.skinForecast().darkCircle().diffFromYesterday()).isNull();

        // 오늘 예보 자체가 없으면 전날 예보를 조회할 이유가 없다
        verify(skinForecastRepository, never())
                .findByUserIdAndBaseDate(USER_ID, BASE_DATE.minusDays(1));
    }

    @Test
    @DisplayName("둘 다 없으면 두 섹션 다 빈 상태다")
    void 둘_다_없으면_둘_다_빈_상태다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        DailyReportResponse response = service().getDailyReport(USER_ID, BASE_DATE);

        assertThat(response.sleepSummary().status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.skinForecast().status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다 — 빈 상태가 아니라 진짜 에러다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service().getDailyReport(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
        verify(skinForecastRepository, never()).findByUserIdAndBaseDate(anyLong(), any());
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private static SleepSession session(BigDecimal hrv, Integer restingHeartRate) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(BASE_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-13T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-13T22:10:00Z"))
                .totalSleepMinutes(432).deepSleepMinutes(126)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(2).awakeMinutes(7)
                .hrv(hrv).restingHeartRate(restingHeartRate)
                .payloadHash("a".repeat(64))
                .build();
    }

    private static SkinForecast forecast(LocalDate baseDate, int darkCircle, Integer complexion,
                                         Integer barrier) {
        return SkinForecast.builder()
                .userId(USER_ID).baseDate(baseDate)
                .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                .build();
    }

}
