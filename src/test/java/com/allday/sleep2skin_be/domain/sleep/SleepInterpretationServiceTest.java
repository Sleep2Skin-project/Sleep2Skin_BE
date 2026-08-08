package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.dto.InterpretationTone;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <b>스코어링 엔진은 진짜를 쓴다.</b> 부분점수 계산이 스텁이면 "가장 낮은 것을 고른다"는 이 서비스의
 * 유일한 판단이 검증되지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class SleepInterpretationServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;

    private SleepInterpretationService service;

    @BeforeEach
    void setUp() {
        service = new SleepInterpretationService(userRepository, sleepSessionRepository,
                new BedtimeRegularityCalculator(sleepSessionRepository), new SkinScoringEngine());
    }

    @Test
    @DisplayName("가장 낮은 부분점수의 피처를 짚고 헤드라인에 실제 값을 넣는다")
    void 가장_낮은_피처를_짚는다() {
        userExists();
        // 각성 3회 → 50점이 최저다 (총 수면 400분 → 83, 깊은수면 10% → 62.5, REM 22.5% → 100)
        session(Fixture.baseline());
        noBedtimeHistory();

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.interpretation().tone()).isEqualTo(InterpretationTone.IMPROVE);
        assertThat(response.interpretation().focus().feature()).isEqualTo(SleepFeature.AWAKE_COUNT);
        assertThat(response.interpretation().focus().label()).isEqualTo("야간 각성");
        assertThat(response.interpretation().focus().score()).isEqualTo(50);
        assertThat(response.interpretation().headline()).contains("3번");
    }

    /**
     * 분기가 없으면 <b>전부 100점인 밤에도 무언가를 "부족하다"고 지목한다.</b> 잘 잔 사용자에게
     * 없는 문제를 알려주는 셈이다.
     */
    @Test
    @DisplayName("모든 지표가 안정 구간이면 지적하지 않고 칭찬한다")
    void 전부_안정이면_칭찬한다() {
        userExists();
        // 각성 0회 → 100, 총 수면 480분 → 100, 깊은수면 18% → 100, REM 22% → 100
        session(Fixture.perfect());
        noBedtimeHistory();

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.interpretation().tone()).isEqualTo(InterpretationTone.PRAISE);
        assertThat(response.interpretation().focus()).isNull();
        assertThat(response.interpretation().headline()).isNotBlank();
    }

    @Test
    @DisplayName("경계값 76점은 지적하지 않고 75점은 지적한다")
    void 칭찬_컷은_76이다() {
        userExists();
        // 총 수면만 낮춰 부분점수를 경계에 맞춘다 — 곡선 (300,0)~(420,100)에서 391.2분 = 76점
        session(Fixture.perfect().totalSleep(392));   // 76.67 → 안정
        noBedtimeHistory();
        assertThat(service.getInterpretation(USER_ID, BASE_DATE).interpretation().tone())
                .isEqualTo(InterpretationTone.PRAISE);

        session(Fixture.perfect().totalSleep(390));   // 75.0 → 지적
        assertThat(service.getInterpretation(USER_ID, BASE_DATE).interpretation().tone())
                .isEqualTo(InterpretationTone.IMPROVE);
    }

    /**
     * 방치하면 맵 순회 순서에 맡기게 되어 <b>같은 밤인데 호출할 때마다 카드 문구가 바뀐다.</b>
     */
    @Test
    @DisplayName("동점이면 SleepFeature 선언 순서가 앞선 피처를 고른다")
    void 동점은_선언_순서로_끊는다() {
        userExists();
        // 각성 3회 → 50점, 깊은수면 9% → 50점. 둘이 같고 AWAKE_COUNT 가 먼저 선언돼 있다
        session(Fixture.baseline().awakeCount(3).stages(9, 22, 69));
        noBedtimeHistory();

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.interpretation().focus().score()).isEqualTo(50);
        assertThat(response.interpretation().focus().feature()).isEqualTo(SleepFeature.AWAKE_COUNT);
    }

    @Test
    @DisplayName("결측 피처는 후보에서 빠진다 — 워치를 안 찬 밤의 HRV를 짚지 않는다")
    void 결측_피처는_후보가_아니다() {
        userExists();
        // 워치가 있었다면 HRV 0점으로 최저였을 밤이지만, 값이 없으므로 후보가 아니다
        session(Fixture.baseline().noWatch());
        noBedtimeHistory();

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.interpretation().focus().feature())
                .isNotIn(SleepFeature.HRV, SleepFeature.RESTING_HEART_RATE);
        assertThat(response.interpretation().focus().feature()).isEqualTo(SleepFeature.AWAKE_COUNT);
    }

    @Test
    @DisplayName("단계가 하나도 안 잡힌 밤도 각성·총 수면으로 카드를 만든다")
    void 단계가_없어도_카드가_나온다() {
        userExists();
        session(Fixture.baseline().stages(0, 0, 0));
        noBedtimeHistory();

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.interpretation().focus().feature())
                .isIn(SleepFeature.AWAKE_COUNT, SleepFeature.TOTAL_SLEEP);
    }

    @Test
    @DisplayName("취침 이력이 3일 이상이면 규칙성도 후보에 들어간다")
    void 이력이_쌓이면_규칙성도_후보다() {
        userExists();
        session(Fixture.perfect());
        // 취침 시각이 3시간씩 흔들린 이력 → 규칙성 부분점수가 0점이 되어 최저가 된다
        given(sleepSessionRepository.findSleepOnsetTimes(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        OffsetDateTime.parse("2026-08-05T11:40:00Z"),
                        OffsetDateTime.parse("2026-08-04T17:40:00Z")));

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.interpretation().tone()).isEqualTo(InterpretationTone.IMPROVE);
        assertThat(response.interpretation().focus().feature())
                .isEqualTo(SleepFeature.BEDTIME_REGULARITY);
        assertThat(response.interpretation().headline()).contains("분으로 흔들렸어요");
    }

    @Test
    @DisplayName("그날 수면 데이터가 없으면 에러가 아니라 빈 상태로 응답한다")
    void 수면_데이터가_없으면_빈_상태다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        SleepInterpretationResponse response = service.getInterpretation(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.message()).isNotBlank();
        assertThat(response.interpretation()).isNull();

        verify(sleepSessionRepository, never()).findSleepOnsetTimes(anyLong(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.getInterpretation(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private void session(Fixture fixture) {
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(fixture.build()));
    }

    /** 이력이 1일뿐이라 규칙성은 결측이다 — 다른 피처를 검증할 때의 기본 상태. */
    private void noBedtimeHistory() {
        given(sleepSessionRepository.findSleepOnsetTimes(eq(USER_ID), any(), any()))
                .willReturn(List.of());
    }

    private static final class Fixture {

        private int awakeCount = 3;
        private int totalSleepMinutes = 400;
        private int deep = 40;
        private int rem = 90;
        private int core = 270;
        private BigDecimal hrv = new BigDecimal("42.00");
        private Integer restingHeartRate = 66;

        /** 각성 3회(50점)가 최저인 밤. */
        static Fixture baseline() {
            return new Fixture();
        }

        /** 네 피처가 전부 100점인 밤. */
        static Fixture perfect() {
            Fixture fixture = new Fixture();
            fixture.awakeCount = 0;
            fixture.totalSleepMinutes = 480;
            fixture.deep = 18;
            fixture.rem = 22;
            fixture.core = 60;
            fixture.hrv = new BigDecimal("60.00");
            fixture.restingHeartRate = 55;
            return fixture;
        }

        Fixture awakeCount(int value) {
            this.awakeCount = value;
            return this;
        }

        Fixture totalSleep(int minutes) {
            this.totalSleepMinutes = minutes;
            return this;
        }

        Fixture stages(int deep, int rem, int core) {
            this.deep = deep;
            this.rem = rem;
            this.core = core;
            return this;
        }

        Fixture noWatch() {
            this.hrv = null;
            this.restingHeartRate = null;
            return this;
        }

        SleepSession build() {
            return SleepSession.builder()
                    .userId(USER_ID).sleepDate(BASE_DATE)
                    .sleepOnsetTime(OffsetDateTime.parse("2026-08-06T14:40:00Z"))
                    .wakeTime(OffsetDateTime.parse("2026-08-06T21:41:00Z"))
                    .totalSleepMinutes(totalSleepMinutes)
                    .deepSleepMinutes(deep).remSleepMinutes(rem).coreSleepMinutes(core)
                    .awakeCount(awakeCount).awakeMinutes(21)
                    .hrv(hrv).restingHeartRate(restingHeartRate)
                    .payloadHash("a".repeat(64))
                    .build();
        }
    }

}
