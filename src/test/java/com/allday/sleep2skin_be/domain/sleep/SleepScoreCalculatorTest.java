package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 수면 점수 (확정값 PRD §10.8).
 *
 * <p>평균 계산은 DB 없이 돈다. 전날 조회만 리포지토리를 탄다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SleepScoreCalculatorTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SLEEP_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private SleepSessionRepository sleepSessionRepository;

    private SleepScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        SkinScoringEngine scoringEngine = new SkinScoringEngine();
        calculator = new SleepScoreCalculator(sleepSessionRepository,
                new BedtimeRegularityCalculator(sleepSessionRepository), scoringEngine);
    }

    @Nested
    @DisplayName("부분점수 평균")
    class 부분점수_평균 {

        /**
         * <b>가중치를 쓰지 않는다.</b> §10.4의 가중치는 지표별로 재정규화된 값이라 "수면 자체"의
         * 점수로 합칠 기준이 없다 — 그냥 섞으면 두 지표에 걸친 피처가 두 번 세어진다.
         */
        @Test
        @DisplayName("참여 피처의 단순 평균을 반올림한다")
        void 단순_평균이다() {
            assertThat(calculator.calculate(scores(80.0, 70.0, 60.0))).isEqualTo(70);
        }

        @Test
        @DisplayName("반올림한다 — 소수점을 버리지 않는다")
        void 반올림한다() {
            // (80 + 75 + 71) / 3 = 75.333…
            assertThat(calculator.calculate(scores(80.0, 75.0, 71.0))).isEqualTo(75);
            // (80 + 75 + 76) / 3 = 77
            assertThat(calculator.calculate(scores(80.0, 75.0, 76.0))).isEqualTo(77);
        }

        /**
         * <b>결측 피처는 분모에서 뺀다.</b> 워치를 안 찬 밤에 HRV를 0점으로 넣으면 없던 값이
         * 점수를 끌어내린다 — §10.6과 같은 규칙이다. 참여 피처가 맵의 키로 표현돼 있어
         * 분기 없이 그렇게 된다.
         */
        @Test
        @DisplayName("결측 피처는 분모에 들어가지 않는다")
        void 결측은_분모에서_빠진다() {
            // 두 피처만 참여한 밤 — 7로 나누면 20점대가 되지만 분모는 2다
            assertThat(calculator.calculate(scores(80.0, 60.0))).isEqualTo(70);
        }

        /** <b>0점이 아니다.</b> 점수 자체가 없는 날이다. */
        @Test
        @DisplayName("참여 피처가 0개면 null이다")
        void 참여가_없으면_null이다() {
            assertThat(calculator.calculate(Map.of())).isNull();
        }
    }

    @Nested
    @DisplayName("저장된 세션에서 다시 계산")
    class 저장된_세션에서_계산 {

        @Test
        @DisplayName("세션이 있으면 부분점수를 다시 계산해 평균을 낸다")
        void 세션에서_다시_계산한다() {
            given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                    .willReturn(Optional.of(session()));
            given(sleepSessionRepository.findSleepOnsetTimes(anyLong(), any(), any()))
                    .willReturn(List.of());

            assertThat(calculator.calculateFor(USER_ID, SLEEP_DATE)).isBetween(0, 100);
        }

        /**
         * <b>전날 세션이 없으면 증가 보상이 지급되지 않게 하는 자리다.</b> 0으로 대신하면
         * 신규 사용자의 첫날이 {@code +180}을 받는다(§10.9).
         */
        @Test
        @DisplayName("세션이 없으면 null이다 — 0이 아니다")
        void 세션이_없으면_null이다() {
            given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                    .willReturn(Optional.empty());

            assertThat(calculator.calculateFor(USER_ID, SLEEP_DATE)).isNull();
        }
    }

    // ===== 픽스처 =====

    /** 값만 중요하므로 어떤 피처에 담기는지는 결과에 영향을 주지 않는다. */
    private Map<SleepFeature, Double> scores(double... values) {
        Map<SleepFeature, Double> scores = new EnumMap<>(SleepFeature.class);
        SleepFeature[] features = SleepFeature.values();
        for (int i = 0; i < values.length; i++) {
            scores.put(features[i], values[i]);
        }
        return scores;
    }

    private SleepSession session() {
        return SleepSession.builder()
                .userId(USER_ID)
                .sleepDate(SLEEP_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-06T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-06T22:10:00Z"))
                .totalSleepMinutes(402)
                .deepSleepMinutes(54)
                .remSleepMinutes(71)
                .coreSleepMinutes(277)
                .awakeCount(3)
                .awakeMinutes(21)
                .hrv(new BigDecimal("42.00"))
                .restingHeartRate(58)
                .payloadHash("hash")
                .build();
    }

}
