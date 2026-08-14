package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.BedtimeRegularityCalculator;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 하루치 "수면 점수"(§10.8) 계산. 일간·주간·월간 리포트가 전부 이 계산을 쓴다.
 *
 * <p><b>예보 점수(§10.4)와 다른 계산이다.</b> 예보는 지표별 가중평균이고 개인 가중치가
 * 반영되지만, 여기서는 그날 참여한 수면 피처 부분점수({@code s(f)})의 <b>단순 평균</b>이다 —
 * "그날 수면이 전반적으로 몇 점이었나"를 보여주는 화면이라 지표별 가중치를 섞지 않는다.
 * {@code SkinScoringEngine.featureScores}(가중치를 곱하기 전 단계)만 재사용하고 지표점수
 * 계산({@code weightedScore})은 쓰지 않는다.
 *
 * <p>원래 {@code DailyReportService} 안에 private 메서드로만 있던 계산을 이 컴포넌트로
 * 추출했다 — 주간·월간이 같은 계산을 하루마다(최대 28회) 반복 호출해야 하는데, private
 * 메서드로 남겨두면 세 서비스가 각자 다시 적어야 한다. <b>{@code skin} 도메인은 건드리지
 * 않는다</b> — {@code report} 도메인 안에서만 추출했고, {@code SkinScoringEngine}·
 * {@code ScoringCommand}는 기존 그대로 참조만 한다.
 */
@Component
@RequiredArgsConstructor
public class DailySleepScoreCalculator {

    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SkinScoringEngine skinScoringEngine;

    /**
     * @param session 그날의 수면 세션. <b>{@code null}을 받으면 곧바로 {@code null}을
     *                반환한다</b> — 주간·월간처럼 기간의 각 날짜를 순회하며 그날 세션이
     *                있는지 미리 알 수 없는 호출부를 위한 편의다. 일간 리포트처럼 세션 존재를
     *                이미 확인한 호출부는 이 분기를 타지 않는다
     */
    public Integer calculate(Long userId, LocalDate sleepDate, SleepSession session) {
        if (session == null) {
            return null;
        }

        Double bedtimeRegularitySd = bedtimeRegularityCalculator.calculate(
                userId, sleepDate, session.getSleepOnsetTime());

        ScoringCommand command = ScoringCommand.forFeatureScores(session, bedtimeRegularitySd);
        Map<SleepFeature, Double> featureScores = skinScoringEngine.featureScores(command);

        return featureScores.isEmpty() ? null : averageRounded(featureScores);
    }

    private int averageRounded(Map<SleepFeature, Double> featureScores) {
        double average = featureScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        return (int) Math.round(average);
    }

}
