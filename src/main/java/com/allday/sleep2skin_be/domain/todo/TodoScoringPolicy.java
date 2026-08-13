package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * TODO-02 추천 엔진의 매칭·가중·정렬·절단.
 *
 * <p><b>후보 추출(카테고리별 활성 행 조회)까지만 Repository가 하고, 그 이후는 전부 여기서
 * DB 없이 계산한다.</b> 단위 테스트가 DB 없이 돌아야 한다는 원칙(architecture.md §3.3)을
 * 따른다.
 *
 * <p>우선순위 = {@code impactScore × (100 − 오늘 예보 점수) + verdictBonus}
 *
 * <p><b>임계값 매칭은 예보 점수만 본다.</b> 실측(직전 검증)은 임계값 비교에 관여하지 않고,
 * {@code verdictBonus}로만 우선순위에 반영된다 — 후보 추출과 우선순위 계산이 서로 다른
 * 점수 소스를 쓰는 것은 의도된 설계다.
 */
public final class TodoScoringPolicy {

    /** verdict가 OVERESTIMATED(위험 과소평가)일 때만 붙는 보너스의 배율. */
    private static final int OVERESTIMATED_BONUS_MULTIPLIER = 10;

    private TodoScoringPolicy() {
    }

    /**
     * 후보 중 임계값을 만족하는 것만 우선순위순으로 정렬해 상위 {@code limit}개를 고른다.
     *
     * <p>그날 예보가 없는 지표(complexion·barrier는 워치 미착용 등으로 null일 수 있다)를
     * targetMetric으로 하는 후보는 매칭 대상에서 제외한다 — 비교할 점수가 없기 때문이다.
     *
     * @param forecastScores 지표별 오늘 예보 점수. 산출 불가한 지표는 맵에서 빠져 있다
     * @param latestVerdicts 지표별 가장 최근 검증 판정. 검증 이력이 없으면 빈 맵
     */
    public static List<ActionMaster> selectTop(List<ActionMaster> candidates,
                                                Map<SkinMetric, Integer> forecastScores,
                                                Map<SkinMetric, VerificationVerdict> latestVerdicts,
                                                int limit) {
        return candidates.stream()
                .filter(action -> forecastScores.get(action.getTargetMetric()) != null)
                .filter(action -> forecastScores.get(action.getTargetMetric()) <= action.getThreshold())
                .sorted(Comparator
                        .comparingInt((ActionMaster action) -> priority(action, forecastScores, latestVerdicts))
                        .reversed()
                        .thenComparing(ActionMaster::getId))   // 동점은 id 오름차순
                .limit(limit)
                .toList();
    }

    private static int priority(ActionMaster action, Map<SkinMetric, Integer> forecastScores,
                                Map<SkinMetric, VerificationVerdict> latestVerdicts) {
        int score = forecastScores.get(action.getTargetMetric());
        int base = action.getImpactScore() * (100 - score);
        return base + verdictBonus(action, latestVerdicts);
    }

    /**
     * 가장 최근 verdict가 OVERESTIMATED(피부가 예상보다 나빴음)일 때만 보너스를 준다.
     * UNDERESTIMATED(예상보다 좋았음)는 위험 신호가 아니므로 반영하지 않는다.
     */
    private static int verdictBonus(ActionMaster action, Map<SkinMetric, VerificationVerdict> latestVerdicts) {
        VerificationVerdict verdict = latestVerdicts.get(action.getTargetMetric());
        if (verdict != VerificationVerdict.OVERESTIMATED) {
            return 0;
        }
        return action.getImpactScore() * OVERESTIMATED_BONUS_MULTIPLIER;
    }

}
