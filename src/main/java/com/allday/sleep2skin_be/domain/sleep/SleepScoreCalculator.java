package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 수면 점수 (확정값 PRD §10.8).
 *
 * <pre>
 * 수면 점수 = round( Σ s(f) / n )     f ∈ 그날 스코어링에 참여한 수면 피처
 *                                     n = 그 개수
 * </pre>
 *
 * <p>{@code s(f)}는 §10.5의 구간선형 정규화로 나온 <b>0~100 부분점수</b>이고, 예보 산출이 이미
 * 계산해 둔 값 그대로다. 0~100이며 <b>높을수록 좋다</b> — 지표 3종과 같은 방향이다.
 *
 * <h2>세 가지 판단</h2>
 *
 * <ul>
 *   <li><b>가중치를 쓰지 않는다 — 단순 평균이다.</b> §10.4의 가중치는 지표별로 재정규화된 값
 *       (지표 내 합 = 1)이라 "수면 자체"의 점수로 합칠 기준이 없다. 세 지표의 가중치를 그냥
 *       섞으면 <b>두 지표에 걸친 피처가 두 번 세어진다</b></li>
 *   <li><b>결측 피처는 분모에서 뺀다.</b> §10.6과 같은 규칙이다 — 워치를 안 찬 밤에 HRV를 0점으로
 *       넣으면 없던 값이 점수를 끌어내린다. 참여 피처는 맵의 키로 표현돼 있어 분기가 필요 없다</li>
 *   <li><b>저장하지 않는다.</b> {@code sleep_session}에서 다시 계산한다 — 파생값을 컬럼으로 두지
 *       않는다는 원칙 그대로다(erd.md §2 원칙 ①)</li>
 * </ul>
 *
 * <p>⚠️ <b>분모가 날마다 달라질 수 있다.</b> 워치를 찬 밤(7개)과 안 찬 밤(5개)의 점수를 그대로
 * 비교하면 수면이 그대로여도 점수가 움직인다. <b>전날 대비 증감을 말하는 자리
 * ({@code SLEEP_SCORE_IMPROVED} 적립)에서는 이 한계를 안고 가는 것이 확정 사항이다</b> —
 * 분모를 고정하려면 결측 피처에 값을 대입해야 하는데, 그건 §10.6이 하지 않기로 한 바로 그것이다.
 *
 * <p><b>피부 예보 점수와 다른 값이다.</b> 예보는 "이 수면이 피부에 어떻게 나타날까"이고 수면
 * 점수는 "수면 자체가 어땠나"이다. 화면에서 두 숫자가 나란히 보이므로 <b>라벨을 섞지 않는다.</b>
 *
 * <p>REP-02·06·08도 이 값을 쓴다 — <b>리포트를 만들 때 계산을 다시 적지 말 것.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SleepScoreCalculator {

    private final SleepSessionRepository sleepSessionRepository;
    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SkinScoringEngine scoringEngine;

    /**
     * 이미 계산된 부분점수에서 바로 구한다. <b>예보 산출 경로가 쓰는 쪽이다</b> — 같은 밤의
     * 부분점수를 두 번 계산할 이유가 없다.
     *
     * <p><b>DB를 보지 않는 순수 계산이다.</b>
     *
     * @param featureScores 그날 스코어링에 <b>참여한</b> 피처의 부분점수. 결측 피처는 키가 없다
     * @return 0~100. <b>참여 피처가 0개면 {@code null}</b> — 점수 자체가 없는 날이고 0점이 아니다
     */
    public Integer calculate(Map<SleepFeature, Double> featureScores) {
        if (featureScores.isEmpty()) {
            return null;
        }
        double sum = featureScores.values().stream().mapToDouble(Double::doubleValue).sum();
        return (int) Math.round(sum / featureScores.size());
    }

    /**
     * 저장된 세션에서 다시 계산한다. <b>전날 점수를 구하는 쪽이 쓴다</b>
     * ({@code SLEEP_SCORE_IMPROVED} 판정 — prd.md §10.9).
     *
     * <p><b>세션이 없으면 {@code null}이다.</b> 그 경우 증가 보상은 지급되지 않는다 — 비교 대상이
     * 없는 것이지 0점에서 오른 것이 아니다. 0으로 대신하면 <b>신규 사용자의 첫날이
     * {@code +180}을 받는다.</b>
     *
     * <p>개인 가중치를 넘기지 않는 것은 <b>부분점수가 가중치 이전 단계</b>라서다
     * ({@link ScoringCommand#forFeatureScores}).
     */
    @Transactional(readOnly = true)
    public Integer calculateFor(Long userId, LocalDate sleepDate) {
        return sleepSessionRepository.findByUserIdAndSleepDate(userId, sleepDate)
                .map(session -> calculate(scoringEngine.featureScores(
                        ScoringCommand.forFeatureScores(session,
                                bedtimeRegularityCalculator.calculate(
                                        userId, sleepDate, session.getSleepOnsetTime())))))
                .orElse(null);
    }

}
