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
     * @param hitRate           <b>누적 적중률</b>(%) — 지금까지 모든 판정의 정확도 평균.
     *                          최근 1건만 쓰면 표본이 최대 3이라 하루마다 요동친다. 배너가
     *                          말하려는 것은 "예보가 얼마나 믿을 만한가"라 표본이 쌓일수록
     *                          안정돼야 한다. <b>판정 하나가 한 표이므로 지표가 2개뿐이던 날은
     *                          자동으로 가벼워진다</b>
     * @param verificationCount 누적 검증 횟수. <b>MY-01·REP-12가 같은 숫자를 쓴다</b>
     * @param streakCount       연속 검증 횟수 (§4.2)
     * @param previous          <b>직전</b> 검증 1건. 화면의 "지난번 대비" 기준선이며, 검증이
     *                          1건뿐이면 {@code null}이다 — 비교할 대상이 없다
     * @param latest            최근 검증 1건. <b>그날치 적중률은 여기 있다</b>
     */
    @Schema(description = "배너 요약")
    public record Summary(

            @Schema(description = "**누적** 적중률(%). 그날치가 아니라 지금까지 전체다", example = "72")
            int hitRate,

            @Schema(description = "누적 검증 횟수", example = "5")
            long verificationCount,

            @Schema(description = "연속 검증 횟수. **오늘 미검증이 연속을 끊지 않는다**", example = "3")
            int streakCount,

            @Schema(description = "직전 검증 1건. 검증이 1건뿐이면 `null`", nullable = true)
            PreviousVerification previous,

            @Schema(description = "최근 검증 1건")
            LatestVerification latest
    ) {
    }

    /**
     * 직전 검증 1건 — 화면의 <b>"지난번 대비 +N%p"</b> 기준선이다.
     *
     * <p><b>상승폭을 서버가 내려주지 않는다.</b> {@code latest.hitRate − previous.hitRate}는
     * 같은 응답 안의 두 필드로 나오는 값이라, 서버가 따로 담으면 같은 사실을 말하는 자리가
     * 둘이 되어 어긋날 수 있다. 앱이 뺄셈한다.
     *
     * <p><b>{@code latest}와 달리 판정 목록을 싣지 않는다.</b> 기준선으로 쓸 숫자만 필요하고,
     * 지난 검증의 지표별 내역은 배너에 나오지 않는다.
     *
     * <p>⚠️ <b>그날치 적중률은 표본이 최대 3이라 요동친다.</b> 한 지표가 10점 더 빗나가는 것만으로
     * {@code 5%p} 안팎이 움직인다 — 최상위 {@code hitRate}(누적)와 성격이 다른 숫자이며,
     * "예보가 얼마나 믿을 만한가"는 여전히 누적 쪽이 말한다.
     *
     * @param baseDate 그 검증의 기준일. <b>{@code latest.baseDate}의 바로 앞 검증일이지 전날이
     *                 아니다</b> — 하루 걸러 검증했으면 이틀 전일 수 있다
     * @param hitRate  그날치 적중률(%). 분모는 {@code latest}와 같은 규칙이다(예보가 있던 지표만)
     */
    @Schema(description = "직전 검증 1건")
    public record PreviousVerification(

            @Schema(description = "그 검증의 기준일. **전날이 아니라 바로 앞 검증일이다**",
                    example = "2026-08-08")
            LocalDate baseDate,

            @Schema(description = "**그날치** 적중률(%)", example = "63")
            int hitRate
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

            @Schema(description = "**그날치** 적중률(%)", example = "94")
            int hitRate,

            @Schema(description = "예보와 대조한 지표")
            List<MetricVerificationResponse> verifications,

            @Schema(description = "예보가 없어 대조하지 못한 지표")
            List<SkippedMetricResponse> skipped
    ) {
    }

}
