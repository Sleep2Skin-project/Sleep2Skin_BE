package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkippedMetricResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse.LatestVerification;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse.PreviousVerification;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse.Summary;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 적중률 · 연속 검증 배너 (HOME-09).
 *
 * <p><b>검증 이력 테이블이 없다.</b> 예보와 실측을 조인해 그때그때 계산한다 — 저장할 게 남지
 * 않아서다(erd.md §4). 검증을 마친 날의 예보가 절대 바뀌지 않기로 한 덕분에 스냅샷도 필요 없다.
 *
 * <h2>적중률은 누적이다</h2>
 *
 * <p>최근 1건만 쓰면 분모가 최대 3이라 숫자가 {@code 0}·{@code 33}·{@code 67}·{@code 100}으로만
 * 튀고 하루마다 요동친다. 배너가 말하려는 것은 <b>"예보가 얼마나 믿을 만한가"</b>이므로 표본이
 * 쌓일수록 안정되는 쪽이 맞다. 그날치는 {@code latest.hitRate}로 따로 준다.
 *
 * <p><b>{@code previous}는 화면의 "지난번 대비" 기준선일 뿐이다.</b> 그날치끼리의 비교라 누적과
 * 성격이 다르며, 상승폭은 서버가 아니라 앱이 뺀다.
 *
 * <p><b>누적 분모에서도 빈 지표는 빠진다.</b> 그날 예보가 없던 지표는 판정 자체가 없었으므로
 * 세지 않는다 — 셀피 응답과 같은 규칙이며, 0점으로 채우면 존재하지 않는 오차가 섞인다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SkinVerificationSummaryService {

    private final UserRepository userRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final VerificationStreakCalculator streakCalculator;

    public VerificationSummaryResponse getSummary(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "배너를 조회할 사용자가 없다 userId=" + userId);
        }

        List<VerifiedDay> verifiedDays = skinMeasurementRepository.findVerifiedDays(userId, baseDate);
        if (verifiedDays.isEmpty()) {
            // 신규 사용자에게 일상적으로 발생한다 — 에러가 아니라 정상 흐름이라 info로 남긴다
            log.info("검증 이력 없음 userId={} baseDate={}", userId, baseDate);
            return VerificationSummaryResponse.empty(baseDate);
        }

        List<MetricVerificationResponse> all = verifiedDays.stream()
                .flatMap(day -> verificationsOf(day).stream())
                .toList();

        return VerificationSummaryResponse.of(baseDate, new Summary(
                hitRate(all),
                skinMeasurementRepository.countByUserId(userId),
                streakCalculator.calculate(baseDate,
                        verifiedDays.stream().map(VerifiedDay::baseDate).toList()),
                previous(verifiedDays),
                latest(userId, verifiedDays.getFirst())));
    }

    /** 조회는 최신순이라 첫 원소가 가장 최근 검증이다. */
    private LatestVerification latest(Long userId, VerifiedDay day) {
        List<MetricVerificationResponse> verifications = verificationsOf(day);
        return new LatestVerification(day.baseDate(), hitRate(verifications),
                verifications, skippedOf(userId, day));
    }

    /**
     * 직전 검증 1건 — 화면의 <b>"지난번 대비"</b> 기준선이다. 최신순 목록의 두 번째 원소이며,
     * <b>검증이 1건뿐이면 {@code null}</b>이다(비교할 대상이 없다).
     *
     * <p><b>이미 읽어 둔 목록에서 꺼낸다 — 조회가 늘지 않는다.</b> 누적 적중률이 어차피 전량을
     * 훑기 때문이다.
     *
     * <p><b>상승폭은 여기서 계산하지 않는다.</b> {@code latest.hitRate − previous.hitRate}는 앱이
     * 뺀다 — 서버가 함께 담으면 같은 사실을 말하는 자리가 둘이 되어 어긋날 수 있다.
     *
     * <p>{@code skipped}를 만들지 않으므로 <b>세션을 읽지 않는다.</b> 제외 사유는 배너에서 최근
     * 1건에만 나온다.
     */
    private PreviousVerification previous(List<VerifiedDay> verifiedDays) {
        if (verifiedDays.size() < 2) {
            return null;
        }
        VerifiedDay day = verifiedDays.get(1);
        return new PreviousVerification(day.baseDate(), hitRate(verificationsOf(day)));
    }

    /**
     * 예보가 있는 지표만 판정한다. <b>다크서클은 항상 있다</b> — 컬럼이 {@code NOT NULL}이라
     * 타입이 그걸 말한다.
     */
    private List<MetricVerificationResponse> verificationsOf(VerifiedDay day) {
        List<MetricVerificationResponse> verifications = new ArrayList<>();
        verifications.add(MetricVerificationResponse.of(
                SkinMetric.DARK_CIRCLE, day.forecastDarkCircle(), day.measuredDarkCircle()));

        if (day.forecastComplexion() != null) {
            verifications.add(MetricVerificationResponse.of(
                    SkinMetric.COMPLEXION, day.forecastComplexion(), day.measuredComplexion()));
        }
        if (day.forecastBarrier() != null) {
            verifications.add(MetricVerificationResponse.of(
                    SkinMetric.BARRIER, day.forecastBarrier(), day.measuredBarrier()));
        }
        return verifications;
    }

    /**
     * 대조하지 못한 지표. <b>최근 1건에만 필요하다</b> — 누적 적중률은 판정된 것만 세면 되고
     * 사유는 배너에 나오지 않는다.
     *
     * <p>사유를 가르려면 그날 워치 착용 여부가 필요해 세션을 한 번 더 읽는다. 최근 1건에만
     * 도는 조회라 누적 계산에는 영향이 없다.
     */
    private List<SkippedMetricResponse> skippedOf(Long userId, VerifiedDay day) {
        if (day.forecastComplexion() != null && day.forecastBarrier() != null) {
            return List.of();       // 전부 대조했다 — 세션을 읽을 이유가 없다
        }

        boolean watchDataMissing = sleepSessionRepository
                .findByUserIdAndSleepDate(userId, day.baseDate())
                .map(SleepSession::isWatchDataMissing)
                .orElse(true);

        List<SkippedMetricResponse> skipped = new ArrayList<>();
        if (day.forecastComplexion() == null) {
            skipped.add(SkippedMetricResponse.of(
                    SkinMetric.COMPLEXION, day.measuredComplexion(), watchDataMissing));
        }
        if (day.forecastBarrier() == null) {
            skipped.add(SkippedMetricResponse.of(
                    SkinMetric.BARRIER, day.measuredBarrier(), watchDataMissing));
        }
        return List.copyOf(skipped);
    }

    /** <b>분모는 판정한 지표 수다 — 검증 일수 × 3이 아니다.</b> */
    private int hitRate(List<MetricVerificationResponse> verifications) {
        long hits = verifications.stream().filter(MetricVerificationResponse::isHit).count();
        return (int) Math.round(hits * 100.0 / verifications.size());
    }

}
