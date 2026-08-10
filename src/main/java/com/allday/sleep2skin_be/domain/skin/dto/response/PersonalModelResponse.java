package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 내 모델 — 일반 vs 개인화 (REP-12).
 *
 * <p><b>{@code personal_weight}가 유일한 출처다</b>(§10.7 L1). REP-07(관측된 상관)·MY-01(검증
 * 횟수)과 <b>서로 다른 질문에 답한다</b> — 문구에서 이 구분을 지킨다.
 *
 * <p><b>신뢰도 등급을 서버가 만들지 않는다</b>(§4.5, L8 해결). {@code verificationCount}를 그대로
 * 주고 등급 해석은 클라이언트가 한다 — 컷오프를 서버에 두면 바꿀 때마다 배포해야 한다.
 */
@Schema(description = "내 모델 (일반 vs 개인화) 응답")
public record PersonalModelResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "모델. 아직 개인화 전이면 `null`", nullable = true)
        Model model
) {

    private static final String NOT_PERSONALIZED_MESSAGE =
            "아직 개인 모델이 없어요. 셀피로 검증하면 나에게 맞게 학습돼요.";

    /** <b>행이 0개면 개인화 전이다</b> — 행의 존재 자체가 개인화 시작 여부다(erd.md §3.7). */
    public static PersonalModelResponse empty() {
        return new PersonalModelResponse(QueryStatus.NO_VERIFICATION, NOT_PERSONALIZED_MESSAGE, null);
    }

    public static PersonalModelResponse of(Model model) {
        return new PersonalModelResponse(QueryStatus.AVAILABLE, null, model);
    }

    /**
     * @param headline 지표 안에서 최대/최소 비가 가장 큰 지표로 만든 한 문장.
     *                 <b>비율은 같은 지표 안에서만 의미를 갖는다</b>(erd.md §3.7)
     */
    @Schema(description = "개인 모델")
    public record Model(

            @Schema(description = "누적 검증 횟수. **등급 해석은 클라이언트가 한다**", example = "5")
            long verificationCount,

            @Schema(description = "가장 두드러진 학습 결과 한 문장",
                    example = "야간 각성에 1.6배 민감해요")
            String headline,

            @Schema(description = "지표별 피처 비중")
            List<MetricWeights> metrics
    ) {
    }

    @Schema(description = "지표 하나의 피처 비중")
    public record MetricWeights(

            @Schema(description = "피부 지표", example = "DARK_CIRCLE")
            SkinMetric metric,

            @Schema(description = "그 지표에 붙은 피처들")
            List<FeatureWeight> features
    ) {
    }

    /**
     * @param generalShare  개인화 전 기준선. <b>지표 내 균등</b>이라 {@code 1/n}이다
     * @param personalShare 재정규화된 비중. <b>예보가 실제로 쓰는 숫자와 같다</b> — 다르면 화면과
     *                      계산이 어긋난다
     * @param ratio         {@code personalShare / generalShare}. <b>{@code 1.0}이면 아직 배울 게
     *                      없었던 것</b>이며 신규 사용자에게 정상이다
     */
    @Schema(description = "피처 하나의 비중")
    public record FeatureWeight(

            @Schema(description = "수면 피처", example = "AWAKE_COUNT")
            SleepFeature feature,

            @Schema(description = "피처의 한국어 이름. **앱이 따로 하드코딩하면 문구가 어긋난다**",
                    example = "야간 각성")
            String label,

            @Schema(description = "개인화 전 비중 (지표 내 균등)", example = "0.5")
            double generalShare,

            @Schema(description = "학습된 비중 (지표 내 합이 1)", example = "0.615")
            double personalShare,

            @Schema(description = "일반 대비 배수. `1.0`이면 아직 학습되지 않았다", example = "1.23")
            double ratio
    ) {
    }

}
