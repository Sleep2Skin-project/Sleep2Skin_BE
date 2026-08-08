package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.UnavailableMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class SkinForecastServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;

    @InjectMocks
    private SkinForecastService skinForecastService;

    @Test
    @DisplayName("예보가 있으면 AVAILABLE과 함께 점수·등급을 반환한다")
    void 예보가_있으면_점수를_반환한다() {
        userExists();
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(forecast(67, 62, 81)));
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session(new BigDecimal("42.00"), 66)));

        SkinForecastQueryResponse response = skinForecastService.getForecast(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.message()).isNull();
        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.forecast().darkCircle().score()).isEqualTo(67);
        assertThat(response.forecast().complexion().score()).isEqualTo(62);
        assertThat(response.forecast().barrier().score()).isEqualTo(81);
        assertThat(response.forecast().unavailable()).isEmpty();
    }

    /**
     * 신규 사용자에게 일상적으로 발생한다. 404로 내리면 경로 오타·잘못된 {@code userId}와 섞여
     * 모니터링에서 신규 유입이 에러 급증으로 보인다.
     */
    @Test
    @DisplayName("그날 예보가 없으면 에러가 아니라 빈 상태로 응답한다")
    void 예보가_없으면_빈_상태다() {
        userExists();
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        SkinForecastQueryResponse response = skinForecastService.getForecast(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.message()).isNotBlank();
        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.forecast()).isNull();

        // 예보가 없으면 볼 세션도 없다
        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
    }

    @Test
    @DisplayName("워치를 안 찬 밤의 혈색은 null이고 사유가 MISSING_FEATURES다")
    void 워치가_없으면_혈색_사유가_MISSING_FEATURES다() {
        userExists();
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(forecast(67, null, 81)));
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session(null, null)));

        SkinForecastQueryResponse response = skinForecastService.getForecast(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.forecast().complexion()).isNull();
        assertThat(response.forecast().unavailable()).containsExactly(
                new UnavailableMetricResponse(SkinMetric.COMPLEXION, UnavailableReason.MISSING_FEATURES));
        // 나머지 두 지표는 정상 발급된다
        assertThat(response.forecast().darkCircle().score()).isEqualTo(67);
        assertThat(response.forecast().barrier().score()).isEqualTo(81);
    }

    @Test
    @DisplayName("단계가 안 잡힌 밤의 장벽은 null이고 사유가 NO_SLEEP_STAGES다")
    void 단계가_없으면_장벽_사유가_NO_SLEEP_STAGES다() {
        userExists();
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(forecast(67, 62, null)));
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session(new BigDecimal("42.00"), 66)));

        SkinForecastQueryResponse response = skinForecastService.getForecast(USER_ID, BASE_DATE);

        assertThat(response.forecast().barrier()).isNull();
        assertThat(response.forecast().unavailable()).containsExactly(
                new UnavailableMetricResponse(SkinMetric.BARRIER, UnavailableReason.NO_SLEEP_STAGES));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다 — 이건 빈 상태가 아니라 진짜 에러다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> skinForecastService.getForecast(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(skinForecastRepository, never()).findByUserIdAndBaseDate(anyLong(), any());
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private static SkinForecast forecast(int darkCircle, Integer complexion, Integer barrier) {
        return SkinForecast.builder()
                .userId(USER_ID).baseDate(BASE_DATE)
                .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                .build();
    }

    private static SleepSession session(BigDecimal hrv, Integer restingHeartRate) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(BASE_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-06T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-06T21:41:00Z"))
                .totalSleepMinutes(400).deepSleepMinutes(40)
                .remSleepMinutes(90).coreSleepMinutes(270)
                .awakeCount(3).awakeMinutes(21)
                .hrv(hrv).restingHeartRate(restingHeartRate)
                .payloadHash("a".repeat(64))
                .build();
    }

}
