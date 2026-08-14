package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse.Summary;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse.WeekScore;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 월간 리포트. 최근 28일을 7일씩 4주(W1~W4)로 나눠 주별 평균과 최고 주를 보여준다.
 *
 * <p>4주 구간은 <b>{@code baseDate} 기준으로 역산한다 — 가입일에 고정된 앵커가 아니다.</b>
 * {@code W4}가 항상 {@code baseDate}를 포함한 최근 7일이고 {@code W1}이 가장 과거다. 가입일을
 * 앵커로 삼으면 매주 경계가 요일마다 달라져 "이번 주"라는 감각과 어긋난다.
 *
 * <p>{@code summary.avgSleepScore}는 <b>주 평균 4개의 평균이 아니라 28일 전체를 한 번에
 * 평균낸 값</b>이다 — 주마다 결측 일수가 다르면 두 계산이 갈린다(가중치가 달라진다).
 * "28일 전부 결측이면 null"이라는 조건이 28일 단위로 걸려 있어 28일 단위로 계산한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MonthlyReportService {

    private static final int PERIOD_DAYS = 28;
    private static final int WEEK_DAYS = 7;
    private static final int WEEK_COUNT = 4;

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final DailySleepScoreCalculator dailySleepScoreCalculator;

    public MonthlyReportResponse getMonthlyReport(Long userId, LocalDate baseDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "리포트를 조회할 사용자가 없다 userId=" + userId));

        LocalDate periodStart = baseDate.minusDays(PERIOD_DAYS - 1L);

        if (daysSinceJoin(user, baseDate) < PERIOD_DAYS) {
            return MonthlyReportResponse.insufficientData(periodStart, baseDate);
        }

        Map<LocalDate, SleepSession> sessions = sessionsByDate(userId, periodStart, baseDate);

        List<Integer> dailyScores = periodStart.datesUntil(baseDate.plusDays(1))
                .map(date -> dailySleepScoreCalculator.calculate(userId, date, sessions.get(date)))
                .toList();

        List<WeekScore> weeks = weeklyScores(dailyScores);
        Integer avgSleepScore = average(dailyScores);
        Summary summary = summaryOf(userId, baseDate, periodStart, avgSleepScore);

        return MonthlyReportResponse.of(periodStart, baseDate, weeks, summary);
    }

    /**
     * W1(가장 과거)~W4({@code baseDate}를 포함한 최근 7일) 순으로 28일을 4등분한다.
     * {@code isHighest}는 4주를 한 번에 보고 정한다 — 최고 평균이 여러 주에서 동점이면 전부
     * {@code true}이고, 모든 주가 {@code null}이면 비교할 값 자체가 없어 전부 {@code false}다.
     */
    private List<WeekScore> weeklyScores(List<Integer> dailyScores) {
        List<Integer> weekAverages = new ArrayList<>();
        for (int week = 0; week < WEEK_COUNT; week++) {
            weekAverages.add(average(dailyScores.subList(week * WEEK_DAYS, (week + 1) * WEEK_DAYS)));
        }

        Integer highest = weekAverages.stream().filter(Objects::nonNull).max(Integer::compareTo)
                .orElse(null);

        List<WeekScore> weeks = new ArrayList<>();
        for (int week = 0; week < WEEK_COUNT; week++) {
            Integer avg = weekAverages.get(week);
            boolean isHighest = highest != null && highest.equals(avg);
            weeks.add(new WeekScore("W" + (week + 1), avg, isHighest));
        }
        return weeks;
    }

    private Map<LocalDate, SleepSession> sessionsByDate(Long userId, LocalDate from, LocalDate to) {
        return sleepSessionRepository.findByUserIdAndSleepDateBetween(userId, from, to).stream()
                .collect(Collectors.toMap(SleepSession::getSleepDate, Function.identity()));
    }

    /**
     * <b>가입 당일을 1일차로 센다</b> — {@code ChronoUnit.DAYS.between}은 날짜 차이(0부터
     * 시작)를 주므로 그대로 쓰면 가입 당일이 0일차가 되어 문턱을 하루 늦게 넘는다.
     */
    private long daysSinceJoin(User user, LocalDate baseDate) {
        return ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), baseDate) + 1;
    }

    /**
     * <b>적중률은 지표 기준이다 — 날짜 기준이 아니다.</b> {@code SkinVerificationSummaryService}와
     * 같은 방식으로 지표별 판정을 모아 {@code HIT} 비율을 낸다. {@code skin} 도메인은 건드리지
     * 않고 같은 계산을 리포트 쪽에서 다시 짠다 — 공용 유틸로 뽑지 않기로 했다({@link
     * WeeklyReportService}와 중복이지만 의도한 것이다).
     */
    private Summary summaryOf(Long userId, LocalDate baseDate, LocalDate periodStart,
                              Integer avgSleepScore) {
        List<VerifiedDay> verifiedInPeriod = skinMeasurementRepository.findVerifiedDays(userId, baseDate)
                .stream()
                .filter(day -> !day.baseDate().isBefore(periodStart))
                .toList();

        List<MetricVerificationResponse> verifications = verifiedInPeriod.stream()
                .flatMap(day -> verificationsOf(day).stream())
                .toList();

        Integer hitRate = verifications.isEmpty() ? null : hitRate(verifications);
        return new Summary(avgSleepScore, hitRate, verifiedInPeriod.size());
    }

    /** 예보가 있는 지표만 판정한다. 다크서클은 항상 있다 — 컬럼이 {@code NOT NULL}이다. */
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

    private int hitRate(List<MetricVerificationResponse> verifications) {
        long hits = verifications.stream().filter(MetricVerificationResponse::isHit).count();
        return (int) Math.round(hits * 100.0 / verifications.size());
    }

    /** {@code null}은 평균에서 제외한다. 전부 {@code null}이면 평균도 {@code null}이다. */
    private Integer average(List<Integer> scores) {
        List<Integer> present = scores.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        double avg = present.stream().mapToInt(Integer::intValue).average().orElseThrow();
        return (int) Math.round(avg);
    }

}
