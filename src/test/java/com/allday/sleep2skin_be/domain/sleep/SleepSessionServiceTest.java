package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationResult;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResult;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepStageSegmentRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * 재수신 4분기와 배선 검증.
 *
 * <p><b>정규화기와 스코어링 엔진은 진짜를 쓴다.</b> 둘 다 순수 계산이라 스텁으로 바꿔도 얻는 게
 * 없고, 오히려 배선이 어긋나도 통과하는 테스트가 된다.
 */
@ExtendWith(MockitoExtension.class)
class SleepSessionServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SLEEP_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SleepStageSegmentRepository sleepStageSegmentRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private PersonalWeightRepository personalWeightRepository;

    private SleepSessionService service;

    private final SleepSessionNormalizer normalizer = new SleepSessionNormalizer();

    @BeforeEach
    void setUp() {
        service = new SleepSessionService(userRepository, sleepSessionRepository,
                sleepStageSegmentRepository, skinForecastRepository, skinMeasurementRepository,
                personalWeightRepository, normalizer, new SkinScoringEngine());
    }

    @Test
    @DisplayName("그날 첫 수신이면 저장하고 예보를 산출한다")
    void 첫_수신은_저장하고_산출한다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.empty());
        stubScoringLookups();
        given(sleepSessionRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(skinForecastRepository.save(any())).willAnswer(call -> call.getArgument(0));

        SleepSessionUploadResult result = service.upload(USER_ID, night());

        assertThat(result.created()).isTrue();
        assertThat(result.response().processed()).isTrue();
        assertThat(result.response().sleepDate()).isEqualTo(SLEEP_DATE);
        assertThat(result.response().forecast().darkCircle()).isNotNull();

        verify(sleepSessionRepository).save(any());
        verify(skinForecastRepository).save(any());
        verify(sleepStageSegmentRepository).saveAll(any());
    }

    @Test
    @DisplayName("같은 페이로드가 다시 오면 아무것도 저장하지 않고 기존 예보를 반환한다")
    void 해시가_같으면_아무것도_하지_않는다() {
        userExists();
        SleepNormalizationResult stored = normalizer.normalize(night());
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(stored.toEntity(USER_ID)));
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(forecast(41, 55, 72)));

        SleepSessionUploadResult result = service.upload(USER_ID, night());

        assertThat(result.created()).isFalse();
        assertThat(result.response().processed()).isFalse();
        assertThat(result.response().forecast().darkCircle().score()).isEqualTo(41);

        verify(sleepSessionRepository, never()).save(any());
        verify(sleepStageSegmentRepository, never()).saveAll(any());
        verify(skinForecastRepository, never()).save(any());
        // 재수신 판정에 검증 이력을 볼 필요가 없다 — 해시가 같으면 바꿀 것 자체가 없다
        verify(skinMeasurementRepository, never()).findByUserIdAndBaseDate(anyLong(), any());
    }

    /**
     * 이 규칙이 깨지면 이미 끝난 셀피 검증의 대조 기준이 사후에 달라져 적중률이 훼손되고
     * 개인 가중치가 중복 학습된다. 성능이 아니라 정확성 문제다.
     */
    @Test
    @DisplayName("검증을 마친 날은 내용이 달라도 예보를 갱신하지 않는다")
    void 검증을_마친_날은_예보가_바뀌지_않는다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(normalizer.normalize(otherNight()).toEntity(USER_ID)));
        given(skinMeasurementRepository.findByUserIdAndBaseDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(SkinMeasurement.builder()
                        .userId(USER_ID).baseDate(SLEEP_DATE)
                        .darkCircle(50).complexion(50).barrier(50)
                        .analyzedAt(OffsetDateTime.parse("2026-08-07T10:00:00Z")).build()));
        SkinForecast existing = forecast(41, 55, 72);
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(existing));

        SleepSessionUploadResult result = service.upload(USER_ID, night());

        assertThat(result.response().processed()).isFalse();
        assertThat(existing.getDarkCircle()).isEqualTo(41);
        assertThat(result.response().forecast().darkCircle().score()).isEqualTo(41);

        verify(sleepSessionRepository, never()).save(any());
        verify(sleepStageSegmentRepository, never()).deleteBySleepSessionId(anyLong());
    }

    @Test
    @DisplayName("검증 전에 내용이 다른 데이터가 오면 갱신하고 재산출한다")
    void 검증_전이면_갱신하고_재산출한다() {
        userExists();
        SleepSession session = normalizer.normalize(otherNight()).toEntity(USER_ID);
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(session));
        given(skinMeasurementRepository.findByUserIdAndBaseDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.empty());
        stubScoringLookups();
        SkinForecast existing = forecast(41, 55, 72);
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.of(existing));

        SleepSessionUploadResult result = service.upload(USER_ID, night());

        // 새 행이 생긴 게 아니라 갱신이므로 201이 아니다
        assertThat(result.created()).isFalse();
        assertThat(result.response().processed()).isTrue();

        SleepNormalizationResult expected = normalizer.normalize(night());
        assertThat(session.getPayloadHash()).isEqualTo(expected.payloadHash());
        assertThat(session.getTotalSleepMinutes()).isEqualTo(expected.totalSleepMinutes());
        assertThat(existing.getDarkCircle()).isNotEqualTo(41);

        verify(sleepStageSegmentRepository).deleteBySleepSessionId(session.getId());
        verify(sleepStageSegmentRepository).saveAll(any());
    }

    @Test
    @DisplayName("없는 사용자면 USER_NOT_FOUND이고 정규화조차 시작하지 않는다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.upload(USER_ID, night()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
    }

    @Test
    @DisplayName("취침 이력이 3일 미만이면 혈색을 빈 상태로 낸다 — 나머지 두 지표는 정상 발급된다")
    void 이력이_부족하면_혈색만_빈다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.empty());
        given(sleepSessionRepository.findSleepOnsetTimes(eq(USER_ID), any(), any()))
                .willReturn(List.of());   // 이번 밤 1개뿐 → 3일 미만
        given(personalWeightRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(sleepSessionRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(skinForecastRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // 워치도 없는 신규 사용자 — COMPLEXION 피처가 전멸한다
        SleepSessionUploadResult result = service.upload(USER_ID, nightWithoutWatch());

        assertThat(result.response().forecast().complexion()).isNull();
        assertThat(result.response().forecast().darkCircle()).isNotNull();
        assertThat(result.response().forecast().barrier()).isNotNull();
        assertThat(result.response().forecast().unavailable())
                .singleElement()
                .satisfies(entry -> assertThat(entry.reason().name()).isEqualTo("MISSING_FEATURES"));
    }

    @Test
    @DisplayName("취침 규칙성은 이번 밤을 정규화 결과에서 가져온다 — DB의 옛 값에 기대지 않는다")
    void 이번_밤은_DB가_아니라_정규화_결과에서_온다() {
        userExists();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, SLEEP_DATE))
                .willReturn(Optional.empty());
        given(sleepSessionRepository.findSleepOnsetTimes(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        OffsetDateTime.parse("2026-08-05T14:40:00Z"),
                        OffsetDateTime.parse("2026-08-04T14:40:00Z")));
        given(personalWeightRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(sleepSessionRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(skinForecastRepository.save(any())).willAnswer(call -> call.getArgument(0));

        SleepSessionUploadResult result = service.upload(USER_ID, night());

        // 지난 2일 + 이번 밤 = 3일이라 규칙성이 살아나고, 셋 다 같은 시각이라 편차 0 → 100점
        assertThat(result.response().forecast().complexion()).isNotNull();
        assertThat(result.response().forecast().unavailable()).isEmpty();

        // 조회 구간은 오늘을 빼고 앞선 6일이다 — 갱신 경로에서 옛 값이 섞이는 것을 막는다
        verify(sleepSessionRepository).findSleepOnsetTimes(
                USER_ID, SLEEP_DATE.minusDays(6), SLEEP_DATE.minusDays(1));
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private void stubScoringLookups() {
        given(sleepSessionRepository.findSleepOnsetTimes(eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(personalWeightRepository.findByUserId(USER_ID)).willReturn(List.of());
    }

    /** 23:40 잠듦 → 07:10 기상, 중간에 7분 각성 1회. */
    private static SleepNormalizationCommand night() {
        return new SleepNormalizationCommand(segments(
                seg(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T01:00"),
                seg(SleepStage.DEEP, "2026-08-07T01:00", "2026-08-07T02:00"),
                seg(SleepStage.AWAKE, "2026-08-07T02:00", "2026-08-07T02:07"),
                seg(SleepStage.REM, "2026-08-07T02:07", "2026-08-07T03:30"),
                seg(SleepStage.CORE, "2026-08-07T03:30", "2026-08-07T07:10")),
                new java.math.BigDecimal("41.2"), 63);
    }

    /** 같은 날짜지만 내용이 다른 밤 — 해시가 달라진다. */
    private static SleepNormalizationCommand otherNight() {
        return new SleepNormalizationCommand(segments(
                seg(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T02:00"),
                seg(SleepStage.DEEP, "2026-08-07T02:00", "2026-08-07T03:00"),
                seg(SleepStage.CORE, "2026-08-07T03:00", "2026-08-07T07:10")),
                new java.math.BigDecimal("41.2"), 63);
    }

    private static SleepNormalizationCommand nightWithoutWatch() {
        return new SleepNormalizationCommand(night().segments(), null, null);
    }

    private static List<SleepSegmentCommand> segments(SleepSegmentCommand... segments) {
        return new ArrayList<>(List.of(segments));
    }

    private static SleepSegmentCommand seg(SleepStage stage, String start, String end) {
        return new SleepSegmentCommand(stage,
                OffsetDateTime.parse(start + ":00+09:00"), OffsetDateTime.parse(end + ":00+09:00"));
    }

    private static SkinForecast forecast(int darkCircle, Integer complexion, Integer barrier) {
        return SkinForecast.builder()
                .userId(USER_ID).baseDate(SLEEP_DATE)
                .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                .build();
    }

}
