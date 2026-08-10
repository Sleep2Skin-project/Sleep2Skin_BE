package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 셀피 분석·검증 응답 (HOME-06→07→08).
 *
 * <p><b>조회 API의 {@code {status, message, 페이로드}} 형태를 쓰지 않는다.</b> 그 형태는 빈 상태가
 * 정상 흐름인 조회를 위한 것이고, 여기는 동작 API라 필요한 것이 없으면 4xx로 끝난다
 * (conventions.md §2) — 성공했다면 결과는 반드시 있다.
 *
 * <p><b>예보값을 따로 실어 보내지 않는다.</b> 판정마다 {@code forecast}·{@code measured}가 붙어
 * 있어 같은 숫자를 두 번 담을 이유가 없다.
 *
 * @param verifications 예보와 대조한 지표. <b>비지 않는다</b> — {@code DARK_CIRCLE}은 예보가 빈
 *                      상태가 될 수 없다(erd.md §3.5)
 * @param skipped       예보가 없어 대조하지 못한 지표. <b>실측값은 여기에도 있다</b>
 * @param hitRate       대조한 지표 중 {@code HIT} 비율(%)
 * @param model         개인 가중치 학습 결과 (HOME-08)
 */
@Schema(description = "셀피 분석·검증 응답")
public record SelfieVerificationResponse(

        @Schema(description = "검증 기준일", example = "2026-08-07")
        LocalDate baseDate,

        @Schema(description = """
                분석 완료 시각 (ISO 8601, 오프셋 포함).

                **서버 시각이라 운영에서는 오프셋이 `Z`(UTC)다** — 컨테이너가 `TZ=UTC`로 돈다.
                가리키는 순간은 정확하므로 앱이 자기 로컬 시각으로 바꿔 표시하면 된다.
                """, example = "2026-08-07T12:33:12Z")
        OffsetDateTime analyzedAt,

        @Schema(description = "예보와 대조한 지표. 최소 1개 이상")
        List<MetricVerificationResponse> verifications,

        @Schema(description = "예보가 없어 대조하지 못한 지표. 전부 대조했으면 빈 배열")
        List<SkippedMetricResponse> skipped,

        @Schema(description = """
                적중률(%). **분모는 `verifications`의 길이이고 3이 아니다.**

                빈 지표를 0점으로 취급하면 존재하지 않는 오차가 적중률에 섞인다.
                """, example = "50")
        int hitRate,

        @Schema(description = "개인 가중치 학습 결과 (HOME-08)")
        PersonalModelUpdateResponse model
) {
}
