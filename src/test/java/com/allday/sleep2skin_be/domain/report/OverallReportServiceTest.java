package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.MetricTrend;
import com.allday.sleep2skin_be.domain.report.dto.OverallReportStatus;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 종합 리포트.
 *
 * <p>지표별 추세 판정 로직 자체는 {@link MetricTrendPolicyTest}가 검증하므로, 여기서는
 * <b>기간 조립·baseDate 상한 조회·가입일 기준 status 판정·주간 평균 집계(결측 제외) 배선</b>만
 * 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverallReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate PERIOD_START = BASE_DATE.minusDays(20);   // 2026-07-25 (W1 첫날)
    private static final LocalDate W1_END = PERIOD_START.plusDays(6);        // 2026-07-31
    private static final LocalDate W2_START = PERIOD_START.plusDays(7);      // 2026-08-01
    private static final LocalDate W2_END = PERIOD_START.plusDays(13);       // 2026-08-07
    private static final LocalDate W3_START = PERIOD_START.plusDays(14);     // 2026-08-08

    @Mock
    private UserRepository userRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;

    private OverallReportService service() {
        return new OverallReportService(userRepository, skinForecastRepository, skinMeasurementRepository);
    }

    @BeforeEach
    void 기본_픽스처() {
        given(skinForecastRepository.findByUserIdAndBaseDateBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(skinMeasurementRepository
                .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(anyLong(), any()))
                .willReturn(Optional.empty());
    }

    @Nested
    @DisplayName("가입일 기준 게이트")
    class JoinDateGate {

        @Test
        @DisplayName("가입한 지 21일 미만이면 INSUFFICIENT_DATA이고 trends는 null이다")
        void 가입_21일_미만이면_빈_상태다() {
            joinedOn(BASE_DATE.minusDays(19)); // 가입 당일 포함 20일차

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.INSUFFICIENT_DATA);
            assertThat(response.periodStart()).isEqualTo(PERIOD_START);
            assertThat(response.periodEnd()).isEqualTo(BASE_DATE);
            assertThat(response.trends()).isNull();
            assertThat(response.message()).isNotBlank();
        }

        @Test
        @DisplayName("가입 당일을 포함해 정확히 21일차면 FULL이다")
        void 가입_21일차부터_FULL이다() {
            joinedOn(BASE_DATE.minusDays(20)); // 가입 당일 포함 21일차

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.FULL);
            assertThat(response.message()).isNull();
            assertThat(response.trends()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
        void 없는_사용자는_404다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service().getOverallReport(USER_ID, BASE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("baseDate 상한 조회 · 기간 조립")
    class PeriodAssembly {

        @Test
        @DisplayName("예보·실측 조회가 전부 baseDate를 상한으로 건다")
        void 조회가_baseDate를_상한으로_건다() {
            joinedLongAgo();

            service().getOverallReport(USER_ID, BASE_DATE);

            verify(skinForecastRepository)
                    .findByUserIdAndBaseDateBetween(USER_ID, PERIOD_START, BASE_DATE);
            verify(skinMeasurementRepository)
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE);
        }

        @Test
        @DisplayName("periodStart는 baseDate - 20이고 periodEnd는 baseDate다")
        void 관찰_기간이_baseDate_기준으로_역산된다() {
            joinedLongAgo();

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.periodStart()).isEqualTo(PERIOD_START);
            assertThat(response.periodEnd()).isEqualTo(BASE_DATE);
        }
    }

    @Nested
    @DisplayName("지표별 추세 (trends)")
    class TrendsWiring {

        /**
         * <b>계산 자체가 아니라 배선(wiring)을 확인한다.</b> 주간 평균 집계(결측 제외)와
         * {@code MetricTrendPolicy.classify} 호출이 지표 3종 각각에 대해 올바로 이뤄지는지만
         * 본다 — 판정 자체의 경계값은 {@link MetricTrendPolicyTest}가 이미 검증했다.
         */
        @Test
        @DisplayName("W1~W3 예보 점수 평균으로 지표마다 독립적으로 추세를 판정한다")
        void 지표마다_독립적으로_추세를_판정한다() {
            joinedLongAgo();
            List<SkinForecast> forecasts = new ArrayList<>();
            for (LocalDate date = PERIOD_START; !date.isAfter(W1_END); date = date.plusDays(1)) {
                forecasts.add(forecast(date, 40, 60, null)); // barrier는 W1 전체 결측
            }
            for (LocalDate date = W2_START; !date.isAfter(W2_END); date = date.plusDays(1)) {
                forecasts.add(forecast(date, 60, 60, 65));
            }
            for (LocalDate date = W3_START; !date.isAfter(BASE_DATE); date = date.plusDays(1)) {
                forecasts.add(forecast(date, 80, 60, 70));
            }
            given(skinForecastRepository.findByUserIdAndBaseDateBetween(USER_ID, PERIOD_START, BASE_DATE))
                    .willReturn(forecasts);

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            // darkCircle: W1=40, W2=60, W3=80 — 같은 방향으로 증가(+20,+20)라 VOLATILE이 아니라 IMPROVED
            assertThat(response.trends().darkCircle().trend()).isEqualTo(MetricTrend.IMPROVED);
            assertThat(response.trends().darkCircle().w1Average()).isEqualTo(40);
            assertThat(response.trends().darkCircle().w3Average()).isEqualTo(80);

            // complexion: W1=W2=W3=60 — 변화 없음
            assertThat(response.trends().complexion().trend()).isEqualTo(MetricTrend.MAINTAINED);

            // barrier: W1 전체 결측 — 판정 불가지만 리포트 전체 status는 영향받지 않는다
            assertThat(response.trends().barrier().trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
            assertThat(response.trends().barrier().w1Average()).isNull();
            assertThat(response.trends().barrier().w3Average()).isEqualTo(70);
            assertThat(response.status()).isEqualTo(OverallReportStatus.FULL);
        }

        @Test
        @DisplayName("예보가 하나도 없으면 세 지표 전부 INSUFFICIENT_SAMPLE이지만 status는 FULL이다")
        void 예보가_없어도_status는_FULL이다() {
            joinedLongAgo();

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.FULL);
            assertThat(response.trends().darkCircle().trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
            assertThat(response.trends().complexion().trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
            assertThat(response.trends().barrier().trend()).isEqualTo(MetricTrend.INSUFFICIENT_SAMPLE);
        }
    }

    @Nested
    @DisplayName("appManaged · clinicNeeded · clinicLink")
    class FixedSections {

        @Test
        @DisplayName("appManaged는 예보 지표 3종 고정 목록이다")
        void appManaged는_고정된다() {
            joinedLongAgo();

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.appManaged())
                    .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.COMPLEXION, SkinMetric.BARRIER);
        }

        @Test
        @DisplayName("실측 이력이 없으면 clinicNeeded는 null이다")
        void 실측_이력이_없으면_null이다() {
            joinedLongAgo();

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded()).isNull();
        }

        @Test
        @DisplayName("가장 최근 실측의 감지 플래그 4종을 그대로 옮긴다 — 전부 값이 있을 때")
        void 최근_실측의_플래그를_옮긴다() {
            joinedLongAgo();
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(true).acneScarDetected(false).agingDetected(true)
                    .blackheadDetected(true)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded().pigmentationDetected()).isTrue();
            assertThat(response.clinicNeeded().acneScarDetected()).isFalse();
            assertThat(response.clinicNeeded().agingDetected()).isTrue();
            assertThat(response.clinicNeeded().blackheadDetected()).isTrue();
        }

        /**
         * <b>{@code clinicNeeded}(REP-10)는 가입일 게이트와 무관하게 항상 계산된다.</b>
         * status가 INSUFFICIENT_DATA(가입 21일 미만)여도 실측 이력이 있으면 그대로 나간다 —
         * 21일 관찰 창은 {@code trends}에만 걸리는 조건이다.
         */
        @Test
        @DisplayName("가입 21일 미만이라 INSUFFICIENT_DATA여도 clinicNeeded는 그대로 계산된다")
        void 가입일_게이트와_무관하게_clinicNeeded는_계산된다() {
            joinedOn(BASE_DATE.minusDays(19)); // 20일차 — INSUFFICIENT_DATA
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(true).acneScarDetected(false).agingDetected(true)
                    .blackheadDetected(true)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(OverallReportStatus.INSUFFICIENT_DATA);
            assertThat(response.trends()).isNull();
            assertThat(response.clinicNeeded().pigmentationDetected()).isTrue();
        }

        /**
         * 이 컬럼 도입 이전에 만들어진 실측 행을 흉내낸다 — 행은 있지만 특정 필드만
         * {@code null}이다. {@code false}(실제 미검출)로 채워지면 안 된다.
         */
        @Test
        @DisplayName("행은 있지만 특정 필드만 null이면 그 필드만 null로, 나머지는 값 그대로 응답한다")
        void 특정_필드만_null이면_그_필드만_null이다() {
            joinedLongAgo();
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(null).acneScarDetected(false).agingDetected(true)
                    .blackheadDetected(true)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded().pigmentationDetected()).isNull();
            assertThat(response.clinicNeeded().acneScarDetected()).isFalse();
            assertThat(response.clinicNeeded().agingDetected()).isTrue();
            assertThat(response.clinicNeeded().blackheadDetected()).isTrue();
        }

        /**
         * 신규 필드({@code blackheadDetected})만 컬럼 도입 이전이라 {@code null}인 실측 행을
         * 흉내낸다 — 기존 3종은 이 컬럼 도입 이전부터 있었던 값이라 정상적으로 채워져 있는 상태다.
         * {@code toClinicNeeded}가 필드별로 분기하지 않고 {@code Boolean}을 그대로 옮기므로,
         * 4번째 필드가 추가돼도 별도 처리 없이 이 케이스가 그대로 통과해야 한다.
         */
        @Test
        @DisplayName("blackheadDetected만 null이면 그 필드만 null로, 나머지 3개는 값 그대로 응답한다")
        void blackheadDetected만_null이면_그_필드만_null이다() {
            joinedLongAgo();
            SkinMeasurement measurement = SkinMeasurement.builder()
                    .userId(USER_ID).baseDate(BASE_DATE)
                    .darkCircle(60).complexion(60).barrier(60)
                    .pigmentationDetected(true).acneScarDetected(false).agingDetected(true)
                    .blackheadDetected(null)
                    .analyzedAt(BASE_DATE.atTime(9, 0).atOffset(ZoneOffset.UTC))
                    .build();
            given(skinMeasurementRepository
                    .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(USER_ID, BASE_DATE))
                    .willReturn(Optional.of(measurement));

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicNeeded().pigmentationDetected()).isTrue();
            assertThat(response.clinicNeeded().acneScarDetected()).isFalse();
            assertThat(response.clinicNeeded().agingDetected()).isTrue();
            assertThat(response.clinicNeeded().blackheadDetected()).isNull();
        }

        @Test
        @DisplayName("clinicLink는 고정 URL이다")
        void clinicLink는_고정이다() {
            joinedLongAgo();

            OverallReportResponse response = service().getOverallReport(USER_ID, BASE_DATE);

            assertThat(response.clinicLink()).isEqualTo("https://amredclinic.com/ko");
        }
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

    private SkinForecast forecast(LocalDate date, int darkCircle, Integer complexion, Integer barrier) {
        return SkinForecast.builder()
                .userId(USER_ID).baseDate(date)
                .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                .build();
    }

}
