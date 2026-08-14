package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.sleep.BedtimeRegularityCalculator;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 하루치 수면 점수(§10.8) 계산.
 *
 * <p><b>스코어링 엔진은 진짜를 쓴다.</b> 부분점수를 스텁으로 두면 "어느 피처가 참여했는가"를
 * 테스트가 직접 정하게 되어, 검증하려는 계산과 순환한다({@code SkinModelServiceTest}와 같은 이유).
 * {@code BedtimeRegularityCalculator}만 스텁으로 둔다 — DB를 보는 컴포넌트라 단위 테스트 범위
 * 밖이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailySleepScoreCalculatorTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SLEEP_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private BedtimeRegularityCalculator bedtimeRegularityCalculator;

    private DailySleepScoreCalculator calculator() {
        return new DailySleepScoreCalculator(bedtimeRegularityCalculator, new SkinScoringEngine());
    }

    @Test
    @DisplayName("세션이 있으면 참여한 피처 부분점수의 평균을 낸다")
    void 세션이_있으면_점수를_낸다() {
        given(bedtimeRegularityCalculator.calculate(eq(USER_ID), eq(SLEEP_DATE), any()))
                .willReturn(null); // 이력 3일 미만이어도 나머지 피처는 참여한다

        Integer score = calculator().calculate(USER_ID, SLEEP_DATE, session(new BigDecimal("42.00"), 55));

        assertThat(score).isNotNull();
        assertThat(score).isBetween(0, 100);
    }

    /**
     * 주간·월간 리포트가 기간의 각 날짜를 순회하며 그날 세션이 있는지 모르는 채로 호출하는
     * 경우를 위한 편의다.
     */
    @Test
    @DisplayName("세션이 null이면 계산 없이 null을 반환한다")
    void 세션이_없으면_null이다() {
        Integer score = calculator().calculate(USER_ID, SLEEP_DATE, null);

        assertThat(score).isNull();
        verify(bedtimeRegularityCalculator, org.mockito.Mockito.never())
                .calculate(org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    /**
     * 취침 규칙성까지 결측되면 남는 건 야간 각성·총 수면뿐이다 — 그래도 0개는 아니므로
     * {@code null}이 아니라 그 둘만으로 평균을 낸다.
     */
    @Test
    @DisplayName("워치를 안 찬 밤도 각성·총 수면 피처는 항상 참여해 null이 아니다")
    void 워치가_없어도_점수가_나온다() {
        given(bedtimeRegularityCalculator.calculate(eq(USER_ID), eq(SLEEP_DATE), any()))
                .willReturn(null);

        Integer score = calculator().calculate(USER_ID, SLEEP_DATE, session(null, null));

        assertThat(score).isNotNull();
    }

    private static SleepSession session(BigDecimal hrv, Integer restingHeartRate) {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(SLEEP_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-13T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-13T22:10:00Z"))
                .totalSleepMinutes(432).deepSleepMinutes(126)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(2).awakeMinutes(7)
                .hrv(hrv).restingHeartRate(restingHeartRate)
                .payloadHash("a".repeat(64))
                .build();
    }

}
