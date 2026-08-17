package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.SkinForecastSection;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection;
import com.allday.sleep2skin_be.domain.report.dto.response.SleepSummarySection.SleepSummary;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
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
import java.util.Optional;

/**
 * 일간 리포트 (REP-02·04·05).
 *
 * <p><b>수면 점수(§10.8)는 예보 점수(§10.4)와 다른 계산이다.</b> 예보는 지표별 가중평균이고
 * 개인 가중치가 반영되지만, 리포트의 {@code sleepScore}는 <b>그날 참여한 피처 부분점수의 단순
 * 평균</b>이다 — "오늘 수면이 전반적으로 몇 점짜리였나"를 보여주는 화면이라 지표별 가중치를
 * 섞으면 오히려 뜻이 흐려진다. 그래서 {@code SkinScoringEngine.featureScores}(가중치를 곱하기
 * 전 단계, {@code s(f)})만 재사용하고 지표점수 계산({@code weightedScore})은 쓰지 않는다.
 *
 * <p><b>{@code sleepSummary}와 {@code skinForecast}를 독립적으로 조회하고 독립적으로 빈 상태를
 * 판정한다.</b> 세션과 예보는 보통 같은 업로드 트랜잭션에서 함께 생기지만, "검증을 마친 날의
 * 예보는 세션이 갱신돼도 재산출하지 않는다"는 정책 때문에 완전히 같이 가지 않는다 — 그래서
 * 한쪽이 없다고 다른 쪽까지 비었다고 가정하지 않는다.
 *
 * <p>{@code sleepScore} 계산 자체는 {@link DailySleepScoreCalculator}로 뽑아뒀다 — 주간·월간
 * 리포트가 같은 계산을 하루마다 반복 호출하기 때문이다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyReportService {

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final DailySleepScoreCalculator dailySleepScoreCalculator;

    public DailyReportResponse getDailyReport(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "리포트를 조회할 사용자가 없다 userId=" + userId);
        }

        SleepSummarySection sleepSummary = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate)
                .map(session -> SleepSummarySection.of(buildSleepSummary(userId, baseDate, session)))
                .orElseGet(() -> {
                    // 신규 사용자·앱 미실행 등에서 일상적으로 발생한다 — 에러가 아니라 정상 흐름이다
                    log.info("일간 리포트 — 수면 데이터 없음 userId={} baseDate={}", userId, baseDate);
                    return SleepSummarySection.empty();
                });

        SkinForecastSection skinForecast = buildSkinForecastSection(userId, baseDate);

        return DailyReportResponse.of(baseDate, sleepSummary, skinForecast);
    }

    private SleepSummary buildSleepSummary(Long userId, LocalDate baseDate, SleepSession session) {
        Integer sleepScore = dailySleepScoreCalculator.calculate(userId, baseDate, session);

        return new SleepSummary(session.getTotalSleepMinutes(), sleepScore,
                session.getDeepSleepMinutes(), session.getCoreSleepMinutes(),
                session.getAwakeCount(), session.getAwakeMinutes(),
                session.getRemSleepMinutes(),
                session.getHrv() == null ? null : session.getHrv().doubleValue(),
                session.getRestingHeartRate());
    }

    /**
     * <b>{@code sleepSummary}와 별도로 조회한다.</b> 세션 유무와 예보 유무가 항상 같이 가지는
     * 않기 때문이다(클래스 Javadoc 참고).
     */
    private SkinForecastSection buildSkinForecastSection(Long userId, LocalDate baseDate) {
        Optional<SkinForecast> today = skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (today.isEmpty()) {
            log.info("일간 리포트 — 예보 없음 userId={} baseDate={}", userId, baseDate);
            return SkinForecastSection.empty();
        }

        Optional<SkinForecast> yesterday = skinForecastRepository
                .findByUserIdAndBaseDate(userId, baseDate.minusDays(1));
        return SkinForecastSection.of(today.get(), yesterday);
    }

}
