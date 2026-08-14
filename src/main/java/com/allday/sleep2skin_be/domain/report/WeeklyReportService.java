package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.DailyScore;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse.Summary;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주간 리포트. 최근 7일({@code baseDate} 포함)의 하루치 수면 점수 추이를 보여준다.
 *
 * <p>기간은 항상 {@code baseDate - 6 ~ baseDate}로 <b>{@code baseDate} 기준 역산</b>한다 —
 * 가입일에 고정된 창이 아니라서 매일 호출할 때마다 창이 하루씩 밀린다.
 *
 * <p><b>{@code INSUFFICIENT_DATA} 판정은 가입일 기준이지, "그 주에 기록이 있었는가"가
 * 아니다.</b> 가입 당일을 1일차로 세어({@code ChronoUnit.DAYS.between(가입일, baseDate) + 1})
 * 7일 미만이면 아직 한 주 분량이 쌓일 수 없는 신규 사용자라 빈 상태다. 가입한 지 오래됐지만
 * 그 주에 안 잔 경우는 여전히 {@code FULL}이고, 그때는 {@code dailyScores}의 해당 날짜만
 * {@code null}로 나간다 — <b>데이터 품질 문제와 신규 사용자 문제를 같은 상태로 묶지 않는다.</b>
 *
 * <p><b>적중률({@code hitRate}·{@code verifiedDays})은 화면에 없어 범위에서 뺐다</b>
 * (2026-08-15). 검증(예보·실측) 조회는 이제 이 서비스가 하지 않는다 — 필요해지면
 * {@code SkinVerificationSummaryService}가 쓰는 것과 같은 방식으로 다시 붙인다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final int PERIOD_DAYS = 7;

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final DailySleepScoreCalculator dailySleepScoreCalculator;

    public WeeklyReportResponse getWeeklyReport(Long userId, LocalDate baseDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "리포트를 조회할 사용자가 없다 userId=" + userId));

        LocalDate periodStart = baseDate.minusDays(PERIOD_DAYS - 1L);

        if (daysSinceJoin(user, baseDate) < PERIOD_DAYS) {
            return WeeklyReportResponse.insufficientData(periodStart, baseDate);
        }

        Map<LocalDate, SleepSession> sessions = sessionsByDate(userId, periodStart, baseDate);

        List<DailyScore> dailyScores = periodStart.datesUntil(baseDate.plusDays(1))
                .map(date -> new DailyScore(date,
                        dailySleepScoreCalculator.calculate(userId, date, sessions.get(date))))
                .toList();

        Integer avgSleepScore = average(dailyScores.stream().map(DailyScore::sleepScore).toList());

        return WeeklyReportResponse.of(periodStart, baseDate, dailyScores, new Summary(avgSleepScore));
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
