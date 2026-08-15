package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 일간 리포트의 수면 요약 섹션 (REP-02·04).
 *
 * <p>{@code SkinForecastQueryResponse}와 같은 {@code {status, message, 페이로드}} 모양을 섹션
 * 단위로 쓴다. 일간 리포트는 이 섹션과 {@link SkinForecastSection}을 함께 담는 합성 응답인데
 * 둘은 서로 무관하게 비어 있을 수 있어, 응답 전체를 하나의 {@code status}로 감싸면 한쪽이
 * 없다는 이유로 있는 쪽 데이터까지 숨기게 된다.
 */
@Schema(description = "수면 요약 섹션")
public record SleepSummarySection(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "수면 요약. 그날 수면 데이터가 없으면 `null`", nullable = true)
        SleepSummary summary
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "그날 수면 데이터가 없습니다.";

    public static SleepSummarySection of(SleepSummary summary) {
        return new SleepSummarySection(QueryStatus.AVAILABLE, null, summary);
    }

    public static SleepSummarySection empty() {
        return new SleepSummarySection(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE, null);
    }

    /**
     * @param sleepScore 그날 스코어링에 참여한 수면 피처 부분점수({@code s(f)})의 단순 평균,
     *                   반올림(§10.8). <b>예보 점수(§10.4, 가중평균)와 다른 값이다</b> — 개인
     *                   가중치를 곱하지 않는다. 참여 피처가 0개면 {@code null}이지만, 세션이
     *                   존재하는 한 야간 각성·총 수면 두 피처는 항상 참여하므로({@code SleepFeature}
     *                   중 결측되지 않는 둘) 실제로는 일어나지 않는다
     */
    @Schema(description = "수면 요약")
    public record SleepSummary(

            @Schema(description = "총 수면 시간(분)", example = "432")
            int totalSleepMinutes,

            @Schema(description = "그날 수면 피처 부분점수의 단순 평균(§10.8). 참여 피처가 없으면 `null`",
                    nullable = true, example = "70")
            Integer sleepScore,

            @Schema(description = "깊은 수면(분)", example = "126")
            int deepSleepMinutes,

            @Schema(description = "얕은 수면(분) — `SleepSession.coreSleepMinutes`", example = "71")
            int lightSleepMinutes,

            @Schema(description = "야간 각성 횟수. 5분 이상 지속된 것만 1회로 센다(정규화 시점에 확정)",
                    example = "2")
            int awakeCount,

            @Schema(description = "야간 각성 총 시간(분). 표시 전용 — 스코어링 피처가 아니다",
                    example = "7")
            int awakeMinutes
    ) {
    }

}
