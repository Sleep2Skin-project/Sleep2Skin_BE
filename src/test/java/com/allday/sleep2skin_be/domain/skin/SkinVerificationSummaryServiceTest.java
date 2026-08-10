package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinVerificationSummaryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 10);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;

    private SkinVerificationSummaryService service;

    private SkinVerificationSummaryService service() {
        // 연속 계산은 진짜를 쓴다 — 스텁으로 두면 검증하려는 규칙을 테스트가 직접 정하게 된다
        return new SkinVerificationSummaryService(userRepository, skinMeasurementRepository,
                sleepSessionRepository, new VerificationStreakCalculator());
    }

    /**
     * <b>누적 적중률의 분모는 판정한 지표 수다 — 검증 일수 × 3이 아니다.</b>
     */
    @Test
    @DisplayName("적중률은 모든 검증의 판정을 모아 낸 비율이다")
    void 적중률은_누적이다() {
        userExists();
        // 8/10: 3개 중 2개 적중 · 8/9: 3개 중 0개 적중 → 6개 중 2개 = 33%
        verifiedDays(
                day(BASE_DATE, 67, 62, 81, 65, 60, 60),               // 적중 · 적중 · 과대
                day(BASE_DATE.minusDays(1), 67, 62, 81, 40, 30, 40)); // 전부 과대
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(2L);

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.summary().hitRate()).isEqualTo(33);
        assertThat(response.summary().verificationCount()).isEqualTo(2);
    }

    /**
     * 두 숫자를 바꿔 쓰면 화면이 다른 뜻을 말하게 된다. 최상위는 "예보가 얼마나 믿을 만한가",
     * {@code latest}는 "어제 예보가 얼마나 맞았나"다.
     */
    @Test
    @DisplayName("그날치 적중률은 latest 안에 따로 있다")
    void 최근_1건의_적중률은_따로다() {
        userExists();
        verifiedDays(
                day(BASE_DATE, 67, 62, 81, 65, 60, 80),               // 3개 전부 적중 = 100%
                day(BASE_DATE.minusDays(1), 67, 62, 81, 40, 30, 40)); // 3개 전부 과대 = 0%
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(2L);

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.summary().hitRate()).isEqualTo(50);              // 누적
        assertThat(response.summary().latest().hitRate()).isEqualTo(100);    // 그날치
        assertThat(response.summary().latest().baseDate()).isEqualTo(BASE_DATE);
    }

    /**
     * 0점으로 채워 세면 <b>존재하지 않는 오차가 적중률에 섞인다</b> — 셀피 응답과 같은 규칙이다.
     */
    @Test
    @DisplayName("예보가 빈 지표는 누적 분모에서도 빠진다")
    void 빈_지표는_분모에서_빠진다() {
        userExists();
        // 혈색 예보가 없던 날 — 판정은 2개뿐이고 둘 다 적중이다
        verifiedDays(day(BASE_DATE, 67, null, 81, 65, 55, 80));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(1L);
        watchNotWorn();

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.summary().hitRate()).isEqualTo(100);      // 3이 분모였다면 67이다
        assertThat(response.summary().latest().verifications())
                .extracting(MetricVerificationResponse::metric)
                .containsExactly(SkinMetric.DARK_CIRCLE, SkinMetric.BARRIER);
    }

    @Test
    @DisplayName("대조하지 못한 지표는 최근 1건에 실측·사유와 함께 실린다")
    void 최근_1건에_제외_사유가_실린다() {
        userExists();
        verifiedDays(day(BASE_DATE, 67, null, 81, 65, 55, 80));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(1L);
        watchNotWorn();

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.summary().latest().skipped()).hasSize(1);
        assertThat(response.summary().latest().skipped().getFirst().metric())
                .isEqualTo(SkinMetric.COMPLEXION);
        assertThat(response.summary().latest().skipped().getFirst().measured().score()).isEqualTo(55);
        assertThat(response.summary().latest().skipped().getFirst().reason())
                .isEqualTo(UnavailableReason.MISSING_FEATURES);
    }

    /** 전부 대조한 날은 사유를 가릴 필요가 없어 세션을 읽지 않는다. */
    @Test
    @DisplayName("전부 대조한 날은 수면 세션을 읽지 않는다")
    void 제외가_없으면_세션을_안_읽는다() {
        userExists();
        verifiedDays(day(BASE_DATE, 67, 62, 81, 65, 60, 80));
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(1L);

        service().getSummary(USER_ID, BASE_DATE);

        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
    }

    @Test
    @DisplayName("연속 검증 횟수가 함께 나온다")
    void 연속_횟수가_나온다() {
        userExists();
        verifiedDays(
                day(BASE_DATE, 67, 62, 81, 65, 60, 80),
                day(BASE_DATE.minusDays(1), 67, 62, 81, 65, 60, 80),
                day(BASE_DATE.minusDays(3), 67, 62, 81, 65, 60, 80));   // 하루 비었다
        given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(3L);

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.summary().streakCount()).isEqualTo(2);
        assertThat(response.summary().verificationCount()).isEqualTo(3);   // 누적은 3이다
    }

    /**
     * 신규 사용자에게 일상적으로 발생한다. 404로 내리면 경로 오타·잘못된 {@code userId}와 섞여
     * 모니터링에서 신규 유입이 에러 급증으로 보인다.
     */
    @Test
    @DisplayName("검증 이력이 없으면 에러가 아니라 빈 상태로 응답한다")
    void 이력이_없으면_빈_상태다() {
        userExists();
        verifiedDays();

        VerificationSummaryResponse response = service().getSummary(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.NO_VERIFICATION);
        assertThat(response.message()).isNotBlank();
        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.summary()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다 — 이건 빈 상태가 아니다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service().getSummary(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(skinMeasurementRepository, never()).findVerifiedDays(anyLong(), any());
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    /** 조회는 최신순으로 준다 — 두 계산이 모두 앞에서부터 읽는다. */
    private void verifiedDays(VerifiedDay... days) {
        given(skinMeasurementRepository.findVerifiedDays(USER_ID, BASE_DATE))
                .willReturn(List.of(days));
    }

    private static VerifiedDay day(LocalDate baseDate, int fDark, Integer fComplexion, Integer fBarrier,
                                   int mDark, int mComplexion, int mBarrier) {
        return new VerifiedDay(baseDate, fDark, fComplexion, fBarrier, mDark, mComplexion, mBarrier);
    }

    private void watchNotWorn() {
        given(sleepSessionRepository.findByUserIdAndSleepDate(anyLong(), any()))
                .willReturn(Optional.of(SleepSession.builder()
                        .userId(USER_ID).sleepDate(BASE_DATE)
                        .sleepOnsetTime(OffsetDateTime.parse("2026-08-09T14:40:00Z"))
                        .wakeTime(OffsetDateTime.parse("2026-08-09T22:00:00Z"))
                        .totalSleepMinutes(440).deepSleepMinutes(40)
                        .remSleepMinutes(90).coreSleepMinutes(310)
                        .awakeCount(3).awakeMinutes(21)
                        .hrv(null).restingHeartRate(null)
                        .payloadHash("a".repeat(64))
                        .build()));
    }

}
