package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 개인 가중치 학습 결과 (HOME-08 — §10.7).
 *
 * <p><b>{@code changes}는 실제로 값이 바뀐 행만 담는다.</b> 그날 참여하지 않은 피처와, 참여했지만
 * 보정량이 0이던 피처는 빠진다.
 *
 * <p><b>보정량이 0인 날은 버그가 아니다.</b> 두 피처의 부분점수가 같으면 오차를 어느 쪽 탓으로
 * 돌릴 근거가 없어 {@code Δw = 0}이 된다 — 그런 날은 아무것도 학습하지 않는 것이 맞다(§10.7).
 *
 * @param updated 이번 검증으로 {@code personal_weight}에 쓴 것이 있는가.
 *                <b>첫 검증은 7행을 만들기 때문에 값이 하나도 안 움직여도 {@code true}다</b> —
 *                행의 존재 자체가 "개인화가 시작됐다"는 뜻이라(erd.md §3.7) 그 사실을 알린다
 * @param message 사용자에게 그대로 보여줄 수 있는 한 문장
 * @param changes 값이 바뀐 (피처, 지표) 쌍
 */
@Schema(description = "개인 가중치 학습 결과 (HOME-08)")
public record PersonalModelUpdateResponse(

        @Schema(description = "이번 검증으로 개인 가중치를 갱신했는가", example = "true")
        boolean updated,

        @Schema(description = "학습 결과 안내 문구. 갱신하지 않았으면 `null`", nullable = true,
                example = "야간 각성을 조금 더 중요하게 보도록 학습했어요.")
        String message,

        @Schema(description = "값이 바뀐 가중치. 보정량이 0이면 비어 있다")
        List<WeightChangeResponse> changes
) {

    /**
     * 학습하지 않았다. <b>그날 대조한 지표가 하나도 없거나 수면 세션을 찾지 못한 경우</b>이며,
     * 앞은 예보가 전부 빈 상태일 때(실제로는 다크서클이 항상 있어 발생하지 않는다), 뒤는
     * 데이터가 어긋난 경우다.
     */
    public static PersonalModelUpdateResponse notUpdated() {
        return new PersonalModelUpdateResponse(false, null, List.of());
    }

    @Schema(description = "가중치 하나의 변화")
    public record WeightChangeResponse(

            @Schema(description = "수면 피처", example = "AWAKE_COUNT")
            SleepFeature feature,

            @Schema(description = "그 피처가 붙은 피부 지표", example = "DARK_CIRCLE")
            SkinMetric metric,

            @Schema(description = "피처의 한국어 이름. **앱이 따로 하드코딩하면 문구가 어긋난다**",
                    example = "야간 각성")
            String label,

            @Schema(description = "보정 전 배수", example = "1.0000")
            BigDecimal before,

            @Schema(description = "보정 후 배수. 0.5~2.0으로 클램프된다", example = "1.0110")
            BigDecimal after
    ) {
    }

}
