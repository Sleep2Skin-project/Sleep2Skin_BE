package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 엔진의 매칭·가중·정렬·절단 (TODO-02).
 *
 * <p><b>DB를 쓰지 않는다.</b> 후보 추출까지만 Repository가 하고 그 이후는 전부 순수 계산이라는
 * 설계(architecture.md §3.3)가 지켜지는 한 이 테스트는 DB 없이 돈다.
 */
class TodoScoringPolicyTest {

    /**
     * 임계값 미달인 쪽이 우선순위가 더 높아도 뒤로 밀린다 — 임계값이 정렬 1순위라는 뜻이다.
     * ({@code 미발동}은 영향도가 커서 우선순위 점수 자체는 앞선다: 9 × 55 &gt; 5 × 55)
     */
    @Test
    @DisplayName("예보 점수가 임계값 이하인 액션이 항상 앞선다")
    void 임계값을_만족하는_것이_먼저다() {
        ActionMaster 발동 = action(1L, SkinMetric.BARRIER, 50, 5);
        ActionMaster 미발동 = action(2L, SkinMetric.BARRIER, 40, 9);

        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(발동, 미발동), Map.of(SkinMetric.BARRIER, 45), Map.of(), 5);

        assertThat(selected).containsExactly(발동, 미발동);
    }

    /**
     * <b>임계값이 후보를 거르면 컨디션이 좋은 날 목록이 4개·0개로 내려간다.</b> 임계값을
     * 올린 뒤(2026-08-17, 전 행 +20) 실제로 그렇게 됐고, 화면이 그리는 칸 수가 날마다 달라졌다.
     */
    @Test
    @DisplayName("임계값을 만족하는 후보가 모자라면 미만족 후보가 뒤를 채운다")
    void 모자라면_임계값_미만족으로_채운다() {
        ActionMaster 발동 = action(1L, SkinMetric.BARRIER, 90, 5);
        ActionMaster 미발동_높음 = action(2L, SkinMetric.BARRIER, 50, 9);
        ActionMaster 미발동_낮음 = action(3L, SkinMetric.BARRIER, 50, 1);

        // 예보 80 — 만족하는 것은 발동 하나뿐이다
        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(발동, 미발동_낮음, 미발동_높음), Map.of(SkinMetric.BARRIER, 80), Map.of(), 3);

        assertThat(selected).containsExactly(발동, 미발동_높음, 미발동_낮음);
    }

    /**
     * 경계값이 어느 쪽에 붙는지는 값 범위로는 드러나지 않는다. 이제 미발동도 목록에 실리므로
     * <b>순서로 확인한다</b> — 경계가 발동이면 우선순위가 더 높은 미발동보다 앞에 온다.
     */
    @Test
    @DisplayName("점수가 임계값과 같으면 발동한다")
    void 경계값은_포함이다() {
        ActionMaster 경계 = action(1L, SkinMetric.BARRIER, 50, 5);
        ActionMaster 한_끗_미달 = action(2L, SkinMetric.BARRIER, 49, 9);

        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(경계, 한_끗_미달), Map.of(SkinMetric.BARRIER, 50), Map.of(), 5);

        assertThat(selected).containsExactly(경계, 한_끗_미달);
    }

    /**
     * 워치 미착용·이력 부족으로 혈색·장벽 예보가 비는 날이 있다. 비교할 점수가 없으면
     * 임계값 판정 자체가 성립하지 않는다.
     */
    @Test
    @DisplayName("그날 예보가 없는 지표를 겨냥한 액션은 후보에서 빠진다")
    void 예보가_없는_지표는_제외된다() {
        ActionMaster 혈색 = action(1L, SkinMetric.COMPLEXION, 100, 9);
        ActionMaster 다크서클 = action(2L, SkinMetric.DARK_CIRCLE, 100, 1);

        // 혈색 예보가 산출되지 않아 맵에 아예 없다
        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(혈색, 다크서클), Map.of(SkinMetric.DARK_CIRCLE, 60), Map.of(), 5);

        assertThat(selected).containsExactly(다크서클);
    }

    /**
     * <b>가중하지 않으면 거의 정상인 지표의 액션이 심각한 지표를 밀어낸다.</b> 세 지표의 후보를
     * 한 풀에 넣고 뽑기 때문에 구조적으로 발생하며, 시드를 잘 채워도 사라지지 않는다
     * (erd.md §3.8의 예시 그대로).
     */
    @Test
    @DisplayName("영향도가 낮아도 지표가 심각하면 앞선다")
    void 심각도로_가중한다() {
        ActionMaster 장벽_세라마이드 = action(1L, SkinMetric.BARRIER, 100, 6);      // 6 × 78 = 468
        ActionMaster 혈색_산책 = action(2L, SkinMetric.COMPLEXION, 100, 9);        // 9 × 29 = 261

        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(혈색_산책, 장벽_세라마이드),
                Map.of(SkinMetric.BARRIER, 22, SkinMetric.COMPLEXION, 71), Map.of(), 5);

        assertThat(selected).containsExactly(장벽_세라마이드, 혈색_산책);
    }

    /**
     * {@code OVERESTIMATED}는 예보 점수를 실제보다 <b>높게</b> 낸 것 = 피부 위험을 과소평가한
     * 것이다. 점수 축과 위험 축이 반대라 여기서 뒤집기 쉽다.
     */
    @Test
    @DisplayName("직전 검증이 OVERESTIMATED인 지표의 액션이 위로 올라간다")
    void 위험을_과소평가한_지표에_보너스가_붙는다() {
        ActionMaster 다크서클 = action(1L, SkinMetric.DARK_CIRCLE, 100, 5);    // 5 × 40 = 200
        ActionMaster 장벽 = action(2L, SkinMetric.BARRIER, 100, 5);            // 5 × 50 = 250
        Map<SkinMetric, Integer> scores = Map.of(SkinMetric.DARK_CIRCLE, 60, SkinMetric.BARRIER, 50);

        List<ActionMaster> 보너스_없음 = TodoScoringPolicy.selectTop(
                List.of(다크서클, 장벽), scores, Map.of(), 5);
        // 다크서클에 보너스 5 × 10 = 50 → 250 으로 동점이 되고 id 로 갈린다
        List<ActionMaster> 보너스_있음 = TodoScoringPolicy.selectTop(
                List.of(다크서클, 장벽), scores,
                Map.of(SkinMetric.DARK_CIRCLE, VerificationVerdict.OVERESTIMATED), 5);

        assertThat(보너스_없음).containsExactly(장벽, 다크서클);
        assertThat(보너스_있음).containsExactly(다크서클, 장벽);
    }

    /**
     * 예상보다 좋았던 지표는 위험 신호가 아니다. 나머지 셋에 보너스가 새면 <b>거의 모든 날
     * 어딘가에 보너스가 붙어</b> 가중이 무의미해진다.
     */
    @Test
    @DisplayName("OVERESTIMATED가 아닌 판정에는 보너스가 없다")
    void 나머지_판정에는_보너스가_없다() {
        ActionMaster 다크서클 = action(1L, SkinMetric.DARK_CIRCLE, 100, 5);
        ActionMaster 장벽 = action(2L, SkinMetric.BARRIER, 100, 5);
        Map<SkinMetric, Integer> scores = Map.of(SkinMetric.DARK_CIRCLE, 60, SkinMetric.BARRIER, 50);

        for (VerificationVerdict verdict : List.of(VerificationVerdict.HIT, VerificationVerdict.CLOSE,
                VerificationVerdict.UNDERESTIMATED)) {
            List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                    List.of(다크서클, 장벽), scores, Map.of(SkinMetric.DARK_CIRCLE, verdict), 5);

            assertThat(selected).as("%s 에는 보너스가 없어야 한다", verdict).containsExactly(장벽, 다크서클);
        }
    }

    /**
     * 정렬 기준을 둔 이유 자체가 "매번 같은 목록"을 보장하기 위해서다. 동점을 방치하면 DB가 주는
     * 순서에 맡기게 되어 그 목적이 깨진다.
     */
    @Test
    @DisplayName("우선순위가 같으면 id 오름차순으로 끊는다")
    void 동점은_id_순이다() {
        ActionMaster 나중 = action(9L, SkinMetric.BARRIER, 100, 5);
        ActionMaster 먼저 = action(2L, SkinMetric.BARRIER, 100, 5);

        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(나중, 먼저), Map.of(SkinMetric.BARRIER, 50), Map.of(), 5);

        assertThat(selected).containsExactly(먼저, 나중);
    }

    @Test
    @DisplayName("limit 개수만큼 자른다")
    void 상위_N개만_남긴다() {
        List<ActionMaster> candidates = List.of(
                action(1L, SkinMetric.BARRIER, 100, 9),
                action(2L, SkinMetric.BARRIER, 100, 8),
                action(3L, SkinMetric.BARRIER, 100, 7),
                action(4L, SkinMetric.BARRIER, 100, 6));

        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                candidates, Map.of(SkinMetric.BARRIER, 50), Map.of(), 3);

        assertThat(selected).hasSize(3)
                .extracting(ActionMaster::getImpactScore)
                .containsExactly(9, 8, 7);
    }

    /**
     * 남는 빈 상태는 이것 하나다 — <b>임계값 미달로는 더 이상 비지 않는다.</b>
     * 예보가 산출된 지표를 겨냥한 액션이 하나도 없을 때만 목록이 빈다.
     */
    @Test
    @DisplayName("우선순위를 매길 후보가 하나도 없으면 빈 목록이다 — 예외가 아니다")
    void 후보가_없으면_비어_있다() {
        ActionMaster 혈색 = action(1L, SkinMetric.COMPLEXION, 30, 9);

        // 혈색 예보가 산출되지 않아 맵에 아예 없다
        List<ActionMaster> selected = TodoScoringPolicy.selectTop(
                List.of(혈색), Map.of(SkinMetric.BARRIER, 90), Map.of(), 5);

        assertThat(selected).isEmpty();
    }

    // ===== 픽스처 =====

    private static ActionMaster action(Long id, SkinMetric metric, int threshold, int impactScore) {
        ActionMaster action = ActionMaster.builder()
                .category(ActionCategory.DO)
                .title("테스트 액션 " + id)
                .reason("테스트 근거 " + id)
                .targetMetric(metric)
                .threshold(threshold)
                .impactScore(impactScore)
                .active(true)
                .build();
        // id는 @GeneratedValue라 영속화 전에는 null이다. 동점 정렬을 검증하려면 값이 있어야 한다
        ReflectionTestUtils.setField(action, "id", id);
        return action;
    }

}
