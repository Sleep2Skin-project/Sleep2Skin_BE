package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.OverallReportStatus;
import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 종합 리포트.
 *
 * <p>{@code sleepScore} 계산 자체({@link DailySleepScoreCalculator})와 추세·정체 판정 로직
 * ({@link TriagePolicy})은 각자의 단위 테스트가 검증하므로, 여기서는 <b>기간 조립·baseDate 상한
 * 조회·상태 판정 배선</b>만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverallReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate PERIOD_START = BASE_DATE.minusDays(20);      // 2026-07-25
    private static final LocalDate FIRST_HALF_END = PERIOD_START.plusDays(9);   // 2026-08-03
    private static final LocalDate SECOND_HALF_START = PERIOD_START.plusDays(11); // 2026-08-05

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private DailySleepScoreCalculator dailySleepScoreCalculator;

    private OverallReportService service() {
        return new OverallReportService(userRepository, sleepSessionRepository, skinForecastRepository,
                skinMeasurementRepository, dailySleepScoreCalculator);
    }

    @BeforeEach
    void 기본_픽스처() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(sleepSessionRepository.findByUserIdAndSleepDateBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(skinForecastRepository.findByUserIdAndBaseDateBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(skinMeasurementRepository
                .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(anyLong(), any()))
                .willReturn(Optional.empty());
        // 세션이 없는 날은 null — 기본값은 표본 부족(INSUFFICIENT_DATA)을 만든다
        given(dailySleepScoreCalculator.calculate(anyLong(), any(), any())).willReturn(null);
    }

    @Nested
    @DisplayName("baseDate 상한 조회")
    class BaseDateUpperBound {

        /**
         * <b>"오늘"이나 "전체 최신"을 쓰는 곳이 하나도 없어야 한다.</b> 세 조회 전부
         * {@code baseDate}를 상한으로 건다 — 서버는 오늘을 모른다(conventions.md §8).
         */
        @Test
        @DisplayName("세션·예보·실측 조회가 전부 baseDate를 상한으로 건다")
        void 세_조회_모두_baseDate가_상한이다() {
            service().getOverallReport(USER_ID, BASE_DATE);

            verify(sleepSessionRepository)
                    .findByUserIdAndSleepDateBetween(USER_ID, PERIOD_START, BASE_DATE);
            verify(skinForecastRepository)
                    .findByUserIdAndBaseDateBetween(USER_ID, PERIOD_START, BASE_DATE);
            verify(skinMeasurementRepository)
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE);
        }

        @Test
        @DisplayName("periodStart는 baseDate - 20이고 periodEnd는 baseDate다")
        void 관찰_기간이_baseDate_기준으로_역산된다() {
            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.periodStart()).isEqualTo(PERIOD_START);
            assertThat(response.periodEnd()).isEqualTo(BASE_DATE);
        }

        /** 가운데 11일차(2026-08-04)는 전반부·후반부 어느 쪽 점수 계산에도 쓰이지 않는다. */
        @Test
        @DisplayName("가운데 11일차는 수면 점수 계산에서 제외된다")
        void 가운데_하루는_제외된다() {
            LocalDate middleDay = PERIOD_START.plusDays(10); // 2026-08-04

            service().getOverallReport(USER_ID, BASE_DATE);

            verify(dailySleepScoreCalculator, never())
                    .calculate(eq(USER_ID), eq(middleDay), any());
        }
    }

    @Nested
    @DisplayName("OverallReportStatus 판정")
    class StatusJudgement {

        @Test
        @DisplayName("유효 표본이 부족하면 status가 INSUFFICIENT_DATA다")
        void 표본이_부족하면_INSUFFICIENT_DATA다() {
            // 기본 픽스처가 이미 전부 null이라 표본 0개 — 별도 설정 없이도 재현된다
            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.INSUFFICIENT_DATA);
            assertThat(response.triage().sleepTrend()).isEqualTo(SleepTrend.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("유효 표본이 충분하면 status가 FULL이다")
        void 표본이_충분하면_FULL이다() {
            allDaysScored(60);

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.FULL);
            assertThat(response.triage().sleepTrend()).isEqualTo(SleepTrend.STABLE);
        }

        /**
         * 정체 지표의 표본 부족은 status에 영향을 주지 않는다 — 수면 추세만이 전체 상태를
         * 결정하는 단일 기준이다.
         */
        @Test
        @DisplayName("정체 지표 판정이 표본 부족이어도 status는 여전히 FULL이다")
        void 정체_판정_표본_부족은_status에_영향_없다() {
            allDaysScored(60);
            given(skinForecastRepository.findByUserIdAndBaseDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                    .willReturn(List.of()); // 예보가 하나도 없어 세 지표 전부 판정 불가

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.FULL);
            assertThat(response.triage().stagnantMetrics()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() {
            given(userRepository.existsById(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> service().getOverallReport(USER_ID, BASE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("트리아지 발동")
    class TriageTrigger {

        @Test
        @DisplayName("추세가 STABLE이고 정체 지표가 있으면 발동한다")
        void STABLE_이고_정체가_있으면_발동한다() {
            allDaysScored(60);
            stagnantForecastsFor(SkinMetric.COMPLEXION);

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.triage().triggered()).isTrue();
            assertThat(response.triage().stagnantMetrics()).containsExactly(SkinMetric.COMPLEXION);
        }

        @Test
        @DisplayName("추세가 FALLING이면 정체 지표가 있어도 발동하지 않는다")
        void FALLING_이면_정체가_있어도_발동하지_않는다() {
            fallingTrend();
            stagnantForecastsFor(SkinMetric.COMPLEXION);

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.triage().sleepTrend()).isEqualTo(SleepTrend.FALLING);
            assertThat(response.triage().triggered()).isFalse();
        }

        @Test
        @DisplayName("추세가 STABLE이어도 정체 지표가 없으면 발동하지 않는다")
        void 정체가_없으면_발동하지_않는다() {
            allDaysScored(60);

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.triage().triggered()).isFalse();
            assertThat(response.triage().stagnantMetrics()).isEmpty();
        }
    }

    @Nested
    @DisplayName("appManaged · clinicNeeded")
    class FixedSections {

        @Test
        @DisplayName("appManaged는 예보 지표 3종 고정 목록이다")
        void appManaged는_고정된다() {
            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.appManaged())
                    .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER);
        }

        @Test
        @DisplayName("실측 이력이 없으면 clinicNeeded는 null이다")
        void 실측_이력이_없으면_null이다() {
            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded()).isNull();
        }

        @Test
        @DisplayName("가장 최근 실측의 감지 플래그를 그대로 옮긴다")
        void 최근_실측의_플래그를_옮긴다() {
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(true).acneScarDetected(false).agingDetected(true)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded().pigmentationDetected()).isTrue();
            assertThat(response.clinicNeeded().acneScarDetected()).isFalse();
            assertThat(response.clinicNeeded().agingDetected()).isTrue();
        }

        /**
         * 이 컬럼 도입 이전에 만들어진 실측 행을 흉내낸다 — 행은 있지만 특정 필드만
         * {@code null}이다. {@code false}(실제 미검출)로 채워지면 안 된다.
         */
        @Test
        @DisplayName("행은 있지만 특정 필드만 null이면 그 필드만 null로, 나머지는 값 그대로 응답한다")
        void 특정_필드만_null이면_그_필드만_null이다() {
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(null).acneScarDetected(false).agingDetected(true)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded().pigmentationDetected()).isNull();
            assertThat(response.clinicNeeded().acneScarDetected()).isFalse();
            assertThat(response.clinicNeeded().agingDetected()).isTrue();
        }

        @Test
        @DisplayName("clinicLink는 고정 URL이다")
        void clinicLink는_고정이다() {
            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicLink()).isEqualTo("https://amredclinic.com/ko");
        }
    }

    // ===== 픽스처 =====

    /** 21일 관찰 창(가운데 제외 20일) 전부 같은 점수를 준다 — 추세는 STABLE이 된다. */
    private void allDaysScored(int score) {
        given(dailySleepScoreCalculator.calculate(eq(USER_ID), any(LocalDate.class), any()))
                .willReturn(score);
    }

    /** 전반부는 낮게, 후반부는 그보다 더 낮게 줘서 FALLING을 만든다. */
    private void fallingTrend() {
        given(dailySleepScoreCalculator.calculate(eq(USER_ID),
                argThatBetween(PERIOD_START, FIRST_HALF_END), any())).willReturn(70);
        given(dailySleepScoreCalculator.calculate(eq(USER_ID),
                argThatBetween(SECOND_HALF_START, BASE_DATE), any())).willReturn(50);
    }

    private LocalDate argThatBetween(LocalDate from, LocalDate to) {
        return argThat(date -> date != null && !date.isBefore(from) && !date.isAfter(to));
    }

    /** 지정한 지표만 정체 조건(평균<50, 변동폭<=5, 표본>=5)을 만족하는 예보 21일치를 채운다. */
    private void stagnantForecastsFor(SkinMetric target) {
        List<SkinForecast> forecasts = PERIOD_START.datesUntil(BASE_DATE.plusDays(1))
                .map(date -> forecastOn(date, target))
                .toList();
        given(skinForecastRepository.findByUserIdAndBaseDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                .willReturn(forecasts);
    }

    private SkinForecast forecastOn(LocalDate date, SkinMetric target) {
        int stagnantScore = 30;   // 평균<50, 변동폭 0 — 항상 정체 조건을 만족한다
        int healthyScore = 80;    // 평균>=50 — 정체가 아니다
        return SkinForecast.builder()
                .userId(USER_ID).baseDate(date)
                .darkCircle(target == SkinMetric.DARK_CIRCLE ? stagnantScore : healthyScore)
                .complexion(target == SkinMetric.COMPLEXION ? stagnantScore : healthyScore)
                .barrier(target == SkinMetric.BARRIER ? stagnantScore : healthyScore)
                .build();
    }

}
