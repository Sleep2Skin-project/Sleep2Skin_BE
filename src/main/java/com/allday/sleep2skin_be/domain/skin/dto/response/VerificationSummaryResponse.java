package com.allday.sleep2skin_be.domain.skin.dto.response;

import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 적중률 · 연속 검증 배너 (HOME-09).
 *
 * <p><b>검증 이력이 없는 것은 에러가 아니다.</b> 신규 사용자에게 일상적으로 발생하므로
 * {@code 200} + {@code NO_VERIFICATION}으로 나간다(conventions.md §2).
 */
@Schema(description = "적중률 · 연속 검증 배너 응답")
public record VerificationSummaryResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "조회 기준일", example = "2026-08-10")
        LocalDate baseDate,

        @Schema(description = "요약. 검증 이력이 없으면 `null`", nullable = true)
        Summary summary
) {

    private static final String NO_VERIFICATION_MESSAGE = "아직 검증 이력이 없어요. 첫 검증을 시작해보세요.";

    public static VerificationSummaryResponse empty(LocalDate baseDate) {
        return new VerificationSummaryResponse(QueryStatus.NO_VERIFICATION,
                NO_VERIFICATION_MESSAGE, baseDate, null);
    }

    public static VerificationSummaryResponse of(LocalDate baseDate, Summary summary) {
        return new VerificationSummaryResponse(QueryStatus.AVAILABLE, null, baseDate, summary);
    }

    /**
     * @param hitRate           <b>누적 적중률</b>(%) — 지금까지 모든 판정 중 {@code HIT} 비율.
     *                          최근 1건만 쓰면 분모가 최대 3이라 {@code 0}·{@code 33}·{@code 67}·
     *                          {@code 100}으로만 튀고 하루마다 요동친다. 배너가 말하려는 것은
     *                          "예보가 얼마나 믿을 만한가"라 표본이 쌓일수록 안정돼야 한다
     * @param verificationCount 누적 검증 횟수. <b>MY-01·REP-12가 같은 숫자를 쓴다</b>
     * @param streakCount       연속 검증 횟수 (§4.2)
     * @param latest            최근 검증 1건. <b>그날치 적중률은 여기 있다</b>
     */
    @Schema(description = "배너 요약")
    public record Summary(

            @Schema(description = "**누적** 적중률(%). 그날치가 아니라 지금까지 전체다", example = "58")
            int hitRate,

            @Schema(description = "누적 검증 횟수", example = "5")
            long verificationCount,

            @Schema(description = "연속 검증 횟수. **오늘 미검증이 연속을 끊지 않는다**", example = "3")
            int streakCount,

            @Schema(description = "최근 검증 1건")
            LatestVerification latest
    ) {
    }

    /**
     * 최근 검증 1건.
     *
     * <p><b>{@code verifications}·{@code skipped}는 셀피 응답과 같은 DTO다.</b> 앱이 파싱 코드를
     * 한 번만 쓰면 된다.
     */
    @Schema(description = "최근 검증 1건")
    public record LatestVerification(

            @Schema(description = "그 검증의 기준일", example = "2026-08-09")
            LocalDate baseDate,

            @Schema(description = "**그날치** 적중률(%)", example = "67")
            int hitRate,

            @Schema(description = "예보와 대조한 지표")
            List<MetricVerificationResponse> verifications,

            @Schema(description = "예보가 없어 대조하지 못한 지표")
            List<SkippedMetricResponse> skipped
    ) {
    }

}
