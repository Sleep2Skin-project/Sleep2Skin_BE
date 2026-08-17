package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 *
 * <p><b>임계값은 후보를 거르지 않는다 — 정렬 1순위일 뿐이다.</b> 만족하는 것을 먼저 담고
 * 모자란 만큼 미만족에서 이어 붙인다. 걸러 내던 시절에는 컨디션이 좋은 날 후보가 {@code limit}에
 * 못 미쳐 목록이 4개·0개로 내려갔고, <b>화면이 그리는 칸 수가 날마다 달라졌다.</b>
 */
public final class TodoScoringPolicy {

    /** verdict가 OVERESTIMATED(위험 과소평가)일 때만 붙는 보너스의 배율. */
    private static final int OVERESTIMATED_BONUS_MULTIPLIER = 10;

    private TodoScoringPolicy() {
    }

    /**
     * 후보를 우선순위순으로 정렬해 {@code limit}개를 고른다. <b>임계값을 만족하는 것이 항상
     * 앞서고</b>, 그것만으로 {@code limit}이 차지 않으면 미만족 후보가 뒤를 채운다.
     *
     * <p>그날 예보가 없는 지표(complexion·barrier는 워치 미착용 등으로 null일 수 있다)를
     * targetMetric으로 하는 후보는 <b>여기서만 진짜로 제외된다</b> — 비교할 점수가 없어
     * 우선순위 자체를 계산할 수 없다. 그런 날은 {@code limit}보다 적게 나올 수 있다.
     *
     * @param forecastScores 지표별 오늘 예보 점수. 산출 불가한 지표는 맵에서 빠져 있다
     * @param latestVerdicts 지표별 가장 최근 검증 판정. 검증 이력이 없으면 빈 맵
     */
    public static List<ActionMaster> selectTop(List<ActionMaster> candidates,
                                                Map<SkinMetric, Integer> forecastScores,
                                                Map<SkinMetric, VerificationVerdict> latestVerdicts,
                                                int limit) {
        // 한 번만 정렬해 두고 두 갈래로 나눈다 — 정렬이 안정적이라 각 갈래 안의 순서가 그대로 남는다
        List<ActionMaster> sorted = candidates.stream()
                .filter(action -> forecastScores.get(action.getTargetMetric()) != null)
                .sorted(Comparator
                        .comparingInt((ActionMaster action) -> priority(action, forecastScores, latestVerdicts))
                        .reversed()
                        .thenComparing(ActionMaster::getId))   // 동점은 id 오름차순
                .toList();

        return Stream.concat(
                        sorted.stream().filter(action -> matchesThreshold(action, forecastScores)),
                        sorted.stream().filter(action -> !matchesThreshold(action, forecastScores)))
                .limit(limit)
                .toList();
    }

    /** 예보 점수가 임계값 이하면 "지금 필요한 액션"이다. 경계값은 포함이다. */
    private static boolean matchesThreshold(ActionMaster action, Map<SkinMetric, Integer> forecastScores) {
        return forecastScores.get(action.getTargetMetric()) <= action.getThreshold();
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
