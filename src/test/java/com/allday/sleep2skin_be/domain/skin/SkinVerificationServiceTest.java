package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkippedMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkinVerificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SkinModelService skinModelService;

    @InjectMocks
    private SkinVerificationService skinVerificationService;

    @Test
    @DisplayName("지표별로 판정하고 적중률은 적중 개수의 비율이다")
    void 지표별로_판정한다() {
        forecastIs(67, 62, 81);
        watchWorn();
        savesWhatItGets();

        // 차이: 6(근접) · 7(근접) · 3(적중)
        SelfieVerificationResponse response = analyze(61, 55, 78);

        assertThat(response.verifications()).extracting(
                        MetricVerificationResponse::metric,
                        MetricVerificationResponse::difference,
                        MetricVerificationResponse::verdict)
                .containsExactly(
                        tuple(SkinMetric.DARK_CIRCLE, 6, VerificationVerdict.CLOSE),
                        tuple(SkinMetric.COMPLEXION, 7, VerificationVerdict.CLOSE),
                        tuple(SkinMetric.BARRIER, 3, VerificationVerdict.HIT));

        assertThat(response.skipped()).isEmpty();
        assertThat(response.hitRate()).isEqualTo(33);            // 3개 중 1개
        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.analyzedAt()).isNotNull();
    }

    /**
     * <b>이 테스트가 지키는 것은 분모다.</b> 빈 지표를 0점으로 취급하면 존재하지 않는 오차가
     * 적중률에 섞이고, 같은 값이 HOME-08의 학습 입력이 되어 없던 값이 개인 가중치를 움직인다.
     */
    @Test
    @DisplayName("예보가 빈 지표는 대조에서 빠지고 적중률 분모에서도 빠진다")
    void 빈_지표는_분모에서_빠진다() {
        forecastIs(67, null, 81);       // 워치를 안 찬 밤 — 혈색 예보가 없다
        watchNotWorn();
        savesWhatItGets();

        SelfieVerificationResponse response = analyze(61, 55, 78);

        assertThat(response.verifications()).extracting(MetricVerificationResponse::metric)
                .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.BARRIER);
        assertThat(response.hitRate()).isEqualTo(50);            // 2개 중 1개 — 3이 분모가 아니다
    }

    /**
     * 사진을 못 읽은 것이 아니라 비교 대상이 없는 것이다. 앱은 실측값 3종을 모두 보여줄 수 있어야
     * 한다.
     */
    @Test
    @DisplayName("대조하지 못한 지표도 실측값과 사유를 함께 돌려준다")
    void 대조하지_못해도_실측은_있다() {
        forecastIs(67, null, 81);
        watchNotWorn();
        savesWhatItGets();

        SelfieVerificationResponse response = analyze(61, 55, 78);

        assertThat(response.skipped()).hasSize(1);
        SkippedMetricResponse skipped = response.skipped().getFirst();
        assertThat(skipped.metric()).isEqualTo(SkinMetric.COMPLEXION);
        assertThat(skipped.measured().score()).isEqualTo(55);
        assertThat(skipped.reason()).isEqualTo(UnavailableReason.MISSING_FEATURES);
    }

    @Test
    @DisplayName("단계가 안 잡힌 밤은 장벽이 NO_SLEEP_STAGES 사유로 빠진다")
    void 장벽이_빠질_수도_있다() {
        forecastIs(67, 62, null);
        watchWorn();
        savesWhatItGets();

        SelfieVerificationResponse response = analyze(61, 55, 78);

        assertThat(response.skipped()).extracting(
                        SkippedMetricResponse::metric, SkippedMetricResponse::reason)
                .containsExactly(tuple(SkinMetric.BARRIER, UnavailableReason.NO_SLEEP_STAGES));
    }

    /**
     * <b>부호가 뒤집히면 과소·과대가 서로 바뀐다.</b> 점수 축과 위험 축이 반대라 문구에서
     * 뒤집히기 쉬운 자리다 — 점수를 낮게 예측한 것은 피부 위험을 <b>과대</b>평가한 것이다.
     */
    @Test
    @DisplayName("예보가 실측보다 낮으면 UNDERESTIMATED다 — 점수 축 기준이다")
    void 부호로_과소_과대를_가른다() {
        forecastIs(40, 62, 81);
        watchWorn();
        savesWhatItGets();

        SelfieVerificationResponse response = analyze(70, 55, 78);

        MetricVerificationResponse darkCircle = response.verifications().getFirst();
        assertThat(darkCircle.difference()).isEqualTo(-30);
        assertThat(darkCircle.verdict()).isEqualTo(VerificationVerdict.UNDERESTIMATED);
    }

    @Test
    @DisplayName("대조할 예보가 없으면 저장하지 않고 404 SKIN_FORECAST_NOT_FOUND다")
    void 예보가_없으면_404다() {
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> analyze(61, 55, 78))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SKIN_FORECAST_NOT_FOUND);

        // 검증이 성립하지 않는 날의 실측을 남기지 않는다
        verify(skinMeasurementRepository, never()).save(any());
    }

    /**
     * 선검사와 저장 사이에 같은 사용자의 두 요청이 겹치면 유니크 제약이 걸린다. 그때 500이
     * 나가면 앱은 재시도할 수 없다고 판단한다.
     */
    @Test
    @DisplayName("하루 1회 제약에 걸리면 500이 아니라 409다")
    void 유니크_위반은_409다() {
        forecastIs(67, 62, 81);
        given(skinMeasurementRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("uk_skin_measurement_user_base_date"));

        assertThatThrownBy(() -> analyze(61, 55, 78))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_ALREADY_DONE);
    }

    /**
     * <b>학습은 대조한 지표만 받는다.</b> {@code skipped}까지 넘기면 예보가 없어 오차가 존재하지
     * 않는 지표로 가중치를 움직이게 된다 — 값 범위는 정상이라 아무 제약에도 안 걸린다.
     */
    @Test
    @DisplayName("학습에는 대조한 지표만 넘긴다 — skipped는 넘기지 않는다")
    void 학습에_대조_결과만_넘긴다() {
        forecastIs(67, null, 81);       // 혈색은 예보가 없다
        watchNotWorn();
        savesWhatItGets();

        analyze(61, 55, 78);

        ArgumentCaptor<List<MetricVerificationResponse>> captor = ArgumentCaptor.captor();
        verify(skinModelService).learn(eq(USER_ID), any(SleepSession.class), captor.capture());

        assertThat(captor.getValue()).extracting(MetricVerificationResponse::metric)
                .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.BARRIER)
                .doesNotContain(SkinMetric.COMPLEXION);
    }

    /**
     * 예보가 있는데 세션이 없는 것은 데이터가 어긋난 상태다. 그래도 <b>검증 자체는 성립했으므로</b>
     * 응답까지 실패시키지 않는다 — 하루 1회 제약 때문에 재시도할 방법도 없다.
     */
    @Test
    @DisplayName("수면 세션이 없으면 학습을 건너뛰고 검증 결과는 그대로 낸다")
    void 세션이_없으면_학습을_건너뛴다() {
        forecastIs(67, 62, 81);
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());
        savesWhatItGets();

        SelfieVerificationResponse response = analyze(61, 55, 78);

        assertThat(response.model().updated()).isFalse();
        assertThat(response.verifications()).isNotEmpty();
        verify(skinModelService, never()).learn(any(), any(), any());
    }

    // ===== 픽스처 =====

    private SelfieVerificationResponse analyze(int darkCircle, int complexion, int barrier) {
        return skinVerificationService.record(USER_ID, BASE_DATE,
                new SkinVisionScores(darkCircle, complexion, barrier));
    }

    private void forecastIs(int darkCircle, Integer complexion, Integer barrier) {
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(SkinForecast.builder()
                        .userId(USER_ID).baseDate(BASE_DATE)
                        .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                        .build()));
    }

    /** {@code save}가 ID를 채워 돌려주는 것은 여기서 검증할 대상이 아니라 그대로 돌려준다. */
    private void savesWhatItGets() {
        given(skinMeasurementRepository.save(any(SkinMeasurement.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private void watchWorn() {
        sessionIs(new BigDecimal("42.00"), 66);
    }

    private void watchNotWorn() {
        sessionIs(null, null);
    }

    private void sessionIs(BigDecimal hrv, Integer restingHeartRate) {
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(SleepSession.builder()
                        .userId(USER_ID).sleepDate(BASE_DATE)
                        .sleepOnsetTime(OffsetDateTime.parse("2026-08-06T14:40:00Z"))
                        .wakeTime(OffsetDateTime.parse("2026-08-06T21:41:00Z"))
                        .totalSleepMinutes(400).deepSleepMinutes(40)
                        .remSleepMinutes(90).coreSleepMinutes(270)
                        .awakeCount(3).awakeMinutes(21)
                        .hrv(hrv).restingHeartRate(restingHeartRate)
                        .payloadHash("a".repeat(64))
                        .build()));
    }

}
