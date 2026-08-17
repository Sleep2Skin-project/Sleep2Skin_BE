package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.ClinicNeeded;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.Triage;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 종합 리포트 (REP-09~11). 트리아지 발동 조건의 두 절반 — 수면 점수 추세와 피부 지표 정체 —을
 * 합쳐 판정하고, 앱이 관리하는 지표 목록과 클리닉 신호를 함께 보여준다.
 *
 * <p><b>가입일 기반 게이트가 없다.</b> 주간·월간 리포트(REP-06·07)는 "가입한 지 기간만큼 지났는가"로
 * 응답 전체를 여닫지만, 이 API의 {@code INSUFFICIENT_DATA}는 <b>실제로 쌓인 유효 표본 수</b>로만
 * 결정된다(신규 사용자든 오래된 사용자든 최근 3주에 잔 날이 5일 미만이면 같은 판정을 받는다).
 * 그래서 이 서비스는 {@code stagnantMetrics}·{@code clinicNeeded}·{@code appManaged}를 상태와
 * 무관하게 항상 계산한다 — {@code TriagePolicy}의 각 판정 함수가 표본 부족을 스스로 걸러낸다.
 *
 * <p><b>{@code baseDate}가 모든 조회의 상한이다.</b> "오늘"이나 "전체 최신"을 쓰는 곳이 없다 —
 * 서버는 오늘을 모르고(conventions.md §8), 미래 날짜가 섞이면 재현 가능성이 깨진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OverallReportService {

    /** {@code SkinMetric}의 선언 순서를 그대로 쓴다 — 예보 응답(HOME-03 등)과 같은 순서다. */
    private static final List<SkinMetric> APP_MANAGED_METRICS = List.of(SkinMetric.values());

    private static final int WINDOW_DAYS = TriagePolicy.SLEEP_TREND_WINDOW_DAYS;      // 21
    private static final int HALF_DAYS = TriagePolicy.SLEEP_TREND_HALF_DAYS;          // 10

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final DailySleepScoreCalculator dailySleepScoreCalculator;

    public OverallReportResponse getOverallReport(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "리포트를 조회할 사용자가 없다 userId=" + userId);
        }

        LocalDate periodStart = baseDate.minusDays(WINDOW_DAYS - 1L);
        LocalDate firstHalfEnd = periodStart.plusDays(HALF_DAYS - 1L);          // 전반부 마지막 날(10일차)
        LocalDate secondHalfStart = periodStart.plusDays(HALF_DAYS + 1L);       // 후반부 첫날(12일차) — 11일차(가운데)는 뺀다

        Map<LocalDate, SleepSession> sessions = sessionsByDate(userId, periodStart, baseDate);

        List<Integer> firstHalfScores = dailyScoresOf(userId, periodStart, firstHalfEnd, sessions);
        List<Integer> secondHalfScores = dailyScoresOf(userId, secondHalfStart, baseDate, sessions);

        SleepTrend sleepTrend = TriagePolicy.classifySleepTrend(firstHalfScores, secondHalfScores);

        List<SkinForecast> forecasts = skinForecastRepository
                .findByUserIdAndBaseDateBetween(userId, periodStart, baseDate);
        List<SkinMetric> stagnantMetrics = Arrays.stream(SkinMetric.values())
                .filter(metric -> isStagnant(metric, forecasts))
                .toList();

        boolean triggered = (sleepTrend == SleepTrend.STABLE || sleepTrend == SleepTrend.RISING)
                && !stagnantMetrics.isEmpty();

        ClinicNeeded clinicNeeded = latestClinicNeeded(userId, baseDate);

        return OverallReportResponse.of(periodStart, baseDate,
                new Triage(triggered, sleepTrend, stagnantMetrics), APP_MANAGED_METRICS, clinicNeeded);
    }

    private List<Integer> dailyScoresOf(Long userId, LocalDate from, LocalDate to,
                                        Map<LocalDate, SleepSession> sessions) {
        return from.datesUntil(to.plusDays(1))
                .map(date -> dailySleepScoreCalculator.calculate(userId, date, sessions.get(date)))
                .toList();
    }

    private Map<LocalDate, SleepSession> sessionsByDate(Long userId, LocalDate from, LocalDate to) {
        return sleepSessionRepository.findByUserIdAndSleepDateBetween(userId, from, to).stream()
                .collect(Collectors.toMap(SleepSession::getSleepDate, Function.identity()));
    }

    /**
     * 예보 지표 하나가 정체인가. 유효 표본이 하나도 없으면 굳이 정책을 부르지 않는다 —
     * {@link TriagePolicy#isStagnantMetric}도 표본 부족을 걸러내지만, 평균·최솟값·최댓값 계산이
     * 빈 리스트에서 성립하지 않아 여기서 먼저 막는다.
     */
    private boolean isStagnant(SkinMetric metric, List<SkinForecast> forecasts) {
        List<Integer> scores = forecasts.stream()
                .map(forecast -> valueOf(metric, forecast))
                .filter(Objects::nonNull)
                .toList();

        if (scores.isEmpty()) {
            return false;
        }
        double average = scores.stream().mapToInt(Integer::intValue).average().orElseThrow();
        int min = Collections.min(scores);
        int max = Collections.max(scores);
        return TriagePolicy.isStagnantMetric(scores.size(), average, min, max);
    }

    private Integer valueOf(SkinMetric metric, SkinForecast forecast) {
        return switch (metric) {
            case DARK_CIRCLE -> forecast.getDarkCircle();
            case COMPLEXION -> forecast.getComplexion();
            case BARRIER -> forecast.getBarrier();
        };
    }

    /**
     * {@code baseDate} 이하 범위에서 가장 최근 실측 1건의 감지 플래그. <b>실측 이력이 전혀 없으면
     * {@code null}이다</b> — 세 플래그를 전부 {@code false}로 채우면 "감지되지 않았다"와 "측정한
     * 적이 없다"를 구분할 수 없다.
     */
    private ClinicNeeded latestClinicNeeded(Long userId, LocalDate baseDate) {
        return skinMeasurementRepository
                .findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(userId, baseDate)
                .map(this::toClinicNeeded)
                .orElse(null);
    }

    /**
     * 행은 있지만 특정 필드가 {@code null}일 수 있다 — 이 컬럼 도입 이전에 만들어진 실측 행이다.
     * {@code Boolean}을 그대로 옮기면 그 필드만 {@code null}로 나가고 나머지는 실제 값이
     * 그대로 응답된다. 별도 분기가 필요 없다.
     */
    private ClinicNeeded toClinicNeeded(SkinMeasurement measurement) {
        return new ClinicNeeded(measurement.getPigmentationDetected(), measurement.getAcneScarDetected(),
                measurement.getAgingDetected());
    }

}
