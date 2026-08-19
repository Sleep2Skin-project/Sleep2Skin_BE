package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.ClinicNeeded;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.MetricTrendResult;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse.Trends;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 종합 리포트 (REP-09~11). 예보 지표 3종(다크서클·혈색·장벽) 각각의 최근 3주 추세를 지표별로
 * 판정하고, 앱이 관리하는 지표 목록과 클리닉 신호를 함께 보여준다.
 *
 * <p>2026-08-19 이전에는 "발동 조건"(수면 점수 추세 STABLE·RISING + 피부 지표 정체)을 만족해야만
 * 트리아지를 노출하는 구조였다. 그 게이팅을 완전히 없애고 <b>항상 지표 3종 전부의 추세를
 * 응답한다</b> — 판정은 {@link MetricTrendPolicy#classify}가 지표마다 독립적으로 낸다. 수면
 * 세션·수면 점수는 더 이상 이 판정에 쓰이지 않는다 — 추세는 예보 점수(다크서클·혈색·장벽) 자체의
 * 최근 3주 변화를 본다.
 *
 * <p><b>{@code status}는 REP-06·07(주간·월간)과 같은 가입일 기준 게이트다.</b> 가입 당일을
 * 1일차로 세어 {@link MetricTrendPolicy#WINDOW_DAYS}(21일) 미만이면 {@code INSUFFICIENT_DATA}이고
 * {@code trends}는 {@code null}이다. 21일 이상이면 항상 {@code FULL}이며, 특정 지표만 표본이
 * 부족한 경우는 그 지표의 {@code trend}만 {@code INSUFFICIENT_SAMPLE}로 표시한다 — 리포트
 * 전체를 부족 처리하지 않는다.
 *
 * <p><b>{@code clinicNeeded}(REP-10)는 이 게이트와 무관하게 항상 계산한다.</b> 실측 이력
 * 유무로만 결정되는 별개의 신호라 21일 관찰 창과 관계가 없다 — 판정 로직 자체는 이전과
 * 같다.
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

    private static final int WINDOW_DAYS = MetricTrendPolicy.WINDOW_DAYS;   // 21
    private static final int WEEK_DAYS = MetricTrendPolicy.WEEK_DAYS;       // 7

    private final UserRepository userRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;

    public OverallReportResponse getOverallReport(Long userId, LocalDate baseDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "리포트를 조회할 사용자가 없다 userId=" + userId));

        LocalDate periodStart = baseDate.minusDays(WINDOW_DAYS - 1L);
        ClinicNeeded clinicNeeded = latestClinicNeeded(userId, baseDate);

        if (daysSinceJoin(user, baseDate) < WINDOW_DAYS) {
            return OverallReportResponse.insufficientData(periodStart, baseDate, APP_MANAGED_METRICS,
                    clinicNeeded);
        }

        LocalDate w1End = periodStart.plusDays(WEEK_DAYS - 1L);
        LocalDate w2Start = periodStart.plusDays(WEEK_DAYS);
        LocalDate w2End = periodStart.plusDays(WEEK_DAYS * 2L - 1L);
        LocalDate w3Start = periodStart.plusDays(WEEK_DAYS * 2L);   // == baseDate - 6

        Map<LocalDate, SkinForecast> forecasts = forecastsByDate(userId, periodStart, baseDate);

        Trends trends = new Trends(
                trendOf(SkinMetric.DARK_CIRCLE, forecasts, periodStart, w1End, w2Start, w2End, w3Start, baseDate),
                trendOf(SkinMetric.COMPLEXION, forecasts, periodStart, w1End, w2Start, w2End, w3Start, baseDate),
                trendOf(SkinMetric.BARRIER, forecasts, periodStart, w1End, w2Start, w2End, w3Start, baseDate));

        return OverallReportResponse.of(periodStart, baseDate, trends, APP_MANAGED_METRICS, clinicNeeded);
    }

    /**
     * <b>가입 당일을 1일차로 센다</b> — {@code ChronoUnit.DAYS.between}은 날짜 차이(0부터
     * 시작)를 주므로 그대로 쓰면 가입 당일이 0일차가 되어 문턱을 하루 늦게 넘는다.
     */
    private long daysSinceJoin(User user, LocalDate baseDate) {
        return ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), baseDate) + 1;
    }

    private MetricTrendResult trendOf(SkinMetric metric, Map<LocalDate, SkinForecast> forecasts,
                                      LocalDate w1Start, LocalDate w1End,
                                      LocalDate w2Start, LocalDate w2End,
                                      LocalDate w3Start, LocalDate w3End) {
        Integer w1Average = weekAverage(metric, forecasts, w1Start, w1End);
        Integer w2Average = weekAverage(metric, forecasts, w2Start, w2End);
        Integer w3Average = weekAverage(metric, forecasts, w3Start, w3End);
        return MetricTrendPolicy.classify(w1Average, w2Average, w3Average);
    }

    /** 그 7일 중 예보가 없는 날은 분모에서 뺀다. 7일 전부 없으면 {@code null}이다. */
    private Integer weekAverage(SkinMetric metric, Map<LocalDate, SkinForecast> forecasts,
                                LocalDate from, LocalDate to) {
        List<Integer> scores = from.datesUntil(to.plusDays(1))
                .map(date -> valueOf(metric, forecasts.get(date)))
                .filter(Objects::nonNull)
                .toList();
        return average(scores);
    }

    private Integer valueOf(SkinMetric metric, SkinForecast forecast) {
        if (forecast == null) {
            return null;
        }
        return switch (metric) {
            case DARK_CIRCLE -> forecast.getDarkCircle();
            case COMPLEXION -> forecast.getComplexion();
            case BARRIER -> forecast.getBarrier();
        };
    }

    /** {@code null}은 평균에서 제외한다. 전부 {@code null}이면 평균도 {@code null}이다. */
    private Integer average(List<Integer> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElseThrow();
        return (int) Math.round(avg);
    }

    private Map<LocalDate, SkinForecast> forecastsByDate(Long userId, LocalDate from, LocalDate to) {
        return skinForecastRepository.findByUserIdAndBaseDateBetween(userId, from, to).stream()
                .collect(Collectors.toMap(SkinForecast::getBaseDate, Function.identity()));
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
