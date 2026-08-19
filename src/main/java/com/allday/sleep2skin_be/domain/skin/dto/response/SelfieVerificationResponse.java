package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
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
 * @param hitRate       대조한 지표들의 <b>예보 정확도 평균</b>(%). {@code HIT} 개수 비율이 아니다
 * @param model         개인 가중치 학습 결과 (HOME-08)
 * @param streakCount   <b>이번 검증을 포함한</b> 연속 검증 횟수. 팝업 문구("3일 연속!")와 지급액이
 *                      같은 숫자에서 나와야 한다 — 검증 전 값을 쓰면 화면과 보상이 하루씩 어긋난다
 * @param exp           연속 검증 보상 적립 결과 (HOME-04). <b>1일차는 {@code gained: 0}이다</b> —
 *                      보상 구간이 2일부터다
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
                적중률(%) — **대조한 지표들의 예보 정확도 평균이다.** `HIT` 개수 비율이 아니다.

                지표마다 `|예보 − 실측|`을 로그 곡선에 태워 0~100으로 바꾸고 평균낸다.
                **적중(`HIT`)이어도 `100`이 아니고, 완전히 빗나가도 `20` 밑으로 내려가지 않는다.**

                **평균낼 분모는 `verifications`의 길이이고 3이 아니다.** 빈 지표를 0점으로
                취급하면 존재하지 않는 오차가 적중률에 섞인다.
                """, example = "87")
        int hitRate,

        @Schema(description = "개인 가중치 학습 결과 (HOME-08)")
        PersonalModelUpdateResponse model,

        @Schema(description = """
                **이번 검증을 포함한** 연속 검증 횟수. HOME-09 배너·MY-01 프로필과 같은 계산에서 나온다.

                **오늘 미검증이 연속을 끊지 않는다** — 오늘 또는 어제부터 이어져 있으면 유효하다.
                """, example = "3")
        int streakCount,

        @Schema(description = "연속 검증 보상 적립 결과 (HOME-04). 1일차는 `gained: 0`이다")
        ExpResponse exp
) {
}
