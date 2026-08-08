package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.dto.ScoringCommand;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse.Interpretation;
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
import java.util.Map;
import java.util.Optional;

/**
 * 어젯밤 수면 통역 카드 (HOME-02).
 *
 * <p>기준치 대비 편차를 계산해 <b>가장 부족한 지표 1개</b>를 골라 헤드라인으로 만든다.
 *
 * <p><b>기준치를 따로 두지 않는다.</b> 예보와 같은 정규화 곡선(§10.5)의 부분점수를 그대로 쓴다 —
 * 부분점수가 가장 낮은 피처가 곧 기준치에서 가장 멀어진 피처다. 기준을 따로 두면 카드는
 * "충분히 주무셨어요"라고 하는데 예보는 총 수면을 감점한 상태가 될 수 있고,
 * <b>같은 화면에서 두 문장이 서로 반박한다.</b>
 *
 * <p><b>읽기 전용이다.</b> 스코어링을 돌리지만 결과를 저장하지 않는다 — 그날 예보는 업로드
 * 시점에 이미 확정됐고, 카드가 그걸 덮어쓰면 검증의 대조 기준이 사후에 달라진다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SleepInterpretationService {

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SkinScoringEngine scoringEngine;

    public SleepInterpretationResponse getInterpretation(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "수면 통역을 조회할 사용자가 없다 userId=" + userId);
        }

        Optional<SleepSession> found = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate);
        if (found.isEmpty()) {
            // 신규 사용자에게 일상적으로 발생한다 — 에러가 아니라 정상 흐름이다
            log.info("수면 세션 없음 userId={} baseDate={}", userId, baseDate);
            return SleepInterpretationResponse.empty(baseDate);
        }

        SleepSession session = found.get();
        Double bedtimeRegularitySd = bedtimeRegularityCalculator.calculate(
                userId, baseDate, session.getSleepOnsetTime());

        return SleepInterpretationResponse.of(baseDate,
                interpret(session, bedtimeRegularitySd, featureScores(session, bedtimeRegularitySd)));
    }

    /**
     * 부분점수는 개인 가중치와 무관하다 — 가중치는 <b>지표점수를 만들 때</b>만 쓰인다.
     * 그래서 빈 맵을 넘겨 조회를 한 번 아낀다.
     */
    private Map<SleepFeature, Double> featureScores(SleepSession session, Double bedtimeRegularitySd) {
        ScoringCommand command = new ScoringCommand(
                session.getAwakeCount(), session.getTotalSleepMinutes(),
                session.getDeepSleepMinutes(), session.getRemSleepMinutes(),
                session.stagedSleepMinutes(), bedtimeRegularitySd,
                session.getHrv(), session.getRestingHeartRate(), Map.of());

        return scoringEngine.score(command).featureScores();
    }

    private Interpretation interpret(SleepSession session, Double bedtimeRegularitySd,
                                     Map<SleepFeature, Double> featureScores) {
        SleepFeature weakest = weakest(featureScores);
        double weakestScore = featureScores.get(weakest);

        if (SleepInterpretationPolicy.isSatisfactory(weakestScore)) {
            return Interpretation.praise(SleepInterpretationPolicy.praiseHeadline());
        }
        return Interpretation.improve(
                SleepInterpretationPolicy.improveHeadline(weakest, session, bedtimeRegularitySd),
                weakest, weakestScore);
    }

    /**
     * 부분점수가 가장 낮은 피처.
     *
     * <p><b>동점은 {@link SleepFeature} 선언 순서로 끊는다.</b> 방치하면 맵 순회 순서에 맡기게 되어
     * 같은 밤인데 호출할 때마다 카드 문구가 바뀐다 — 액션 추천이 동점을 {@code id} 오름차순으로
     * 끊는 것과 같은 이유다(erd.md §3.8). <b>enum 선언 순서를 바꾸면 카드 문구가 바뀐다.</b>
     *
     * <p><b>결측 피처는 애초에 후보가 아니다.</b> 워치를 안 찬 밤의 {@code HRV}는 부분점수 자체가
     * 없어 {@code featureScores}에 키가 없다. {@code AWAKE_COUNT}·{@code TOTAL_SLEEP}은 세션이
     * 존재하는 이상 결측되지 않으므로 <b>후보가 0개가 되는 경우는 없다.</b>
     */
    private SleepFeature weakest(Map<SleepFeature, Double> featureScores) {
        SleepFeature weakest = null;
        for (SleepFeature feature : SleepFeature.values()) {
            Double score = featureScores.get(feature);
            if (score == null) {
                continue;
            }
            if (weakest == null || score < featureScores.get(weakest)) {
                weakest = feature;
            }
        }
        if (weakest == null) {
            throw new IllegalStateException(
                    "부분점수가 하나도 없다 — 각성 횟수와 총 수면은 결측될 수 없다");
        }
        return weakest;
    }

}
