package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.report.dto.OverallReportStatus;
import com.allday.sleep2skin_be.domain.report.dto.SleepTrend;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 종합 리포트 (REP-09~11). 최근 3주 수면 점수 추세와 피부 지표 정체 여부로 클리닉 트리아지를
 * 판정하고, 앱이 관리하는 지표와 클리닉이 필요할 수 있는 신호를 함께 보여준다.
 *
 * <p><b>문장을 만들지 않는다.</b> {@code triage}는 판정 결과(추세 값·정체 지표 목록·발동 여부)만
 * 담고, "수면은 개선됐지만 혈색이 정체됐어요" 같은 문구는 서버가 만들지 않는다 — 문구를 서버에
 * 두면 한 글자 고치는 데 배포가 필요하고, REP-02·TODO-01에서 이미 같은 판단을 했다(prd.md §4.4 ⑧).
 *
 * <p>판정 기준값은 {@link com.allday.sleep2skin_be.domain.report.TriagePolicy}에 있으며
 * <b>확정값이다</b>(prd.md §10.10 · sub-docs/report-overall.md §5).
 */
@Schema(description = "종합 리포트")
public record OverallReportResponse(

        @Schema(description = "조회 상태. 수면 점수 추세를 판정할 수 없으면 INSUFFICIENT_DATA",
                example = "FULL")
        OverallReportStatus status,

        @Schema(description = "관찰 기간 시작일 (`baseDate - 20`)", example = "2026-07-25")
        LocalDate periodStart,

        @Schema(description = "관찰 기간 종료일 (`baseDate`)", example = "2026-08-14")
        LocalDate periodEnd,

        @Schema(description = "클리닉 트리아지 판정")
        Triage triage,

        @Schema(description = "앱이 관리하는 예보 지표 3종. 점수·등급 없이 고정 목록")
        List<SkinMetric> appManaged,

        @Schema(description = "클리닉이 필요할 수 있는 신호 3종. 실측 이력이 전혀 없으면 `null`",
                nullable = true)
        ClinicNeeded clinicNeeded,

        @Schema(description = "클리닉 연결 링크(고정값)", example = CLINIC_LINK)
        String clinicLink
) {

    /** 클리닉 연결 링크. 앱이 파싱하지 않고 그대로 여는 고정 URL이다. */
    public static final String CLINIC_LINK = "https://amredclinic.com/ko";

    public static OverallReportResponse of(LocalDate periodStart, LocalDate periodEnd, Triage triage,
                                           List<SkinMetric> appManaged, ClinicNeeded clinicNeeded) {
        OverallReportStatus status = triage.sleepTrend() == SleepTrend.INSUFFICIENT_DATA
                ? OverallReportStatus.INSUFFICIENT_DATA
                : OverallReportStatus.FULL;

        return new OverallReportResponse(status, periodStart, periodEnd, triage, appManaged,
                clinicNeeded, CLINIC_LINK);
    }

    /**
     * @param triggered       발동 여부 — {@code sleepTrend}가 {@code STABLE}·{@code RISING} 중
     *                        하나이고 {@code stagnantMetrics}가 1개 이상일 때만 {@code true}
     * @param sleepTrend      최근 3주 수면 점수 추세
     * @param stagnantMetrics 정체로 판정된 예보 지표. 표본 부족인 지표는 여기 담기지 않는다 —
     *                        판정 불가와 "정체 아님"을 이 배열에서는 구분하지 않는다
     */
    @Schema(description = "클리닉 트리아지 판정")
    public record Triage(

            @Schema(description = "발동 여부", example = "false")
            boolean triggered,

            @Schema(description = "최근 3주 수면 점수 추세", example = "STABLE")
            SleepTrend sleepTrend,

            @Schema(description = "정체로 판정된 예보 지표. 표본 부족인 지표는 빠진다")
            List<SkinMetric> stagnantMetrics
    ) {
    }

    /**
     * {@code baseDate} 이하 범위에서 가장 최근 실측 1건의 감지 플래그를 그대로 옮긴 값이다.
     * 비교·트렌드는 없다 — "지금 클리닉이 필요해 보이는가"만 보여준다.
     *
     * <p><b>레코드 전체가 {@code null}인 것과 필드 하나가 {@code null}인 것은 다른 뜻이다.</b>
     * {@code clinicNeeded} 전체가 {@code null}이면 실측 이력이 전혀 없는 것이고, 레코드는 있는데
     * 필드 하나만 {@code null}이면 그 실측 행이 이 컬럼 도입 이전에 만들어졌다는 뜻이다(미측정).
     * 어느 쪽도 {@code false}(실제 미검출)로 채우지 않는다.
     */
    @Schema(description = "클리닉이 필요할 수 있는 신호 3종 (가장 최근 실측 기준)")
    public record ClinicNeeded(

            @Schema(description = "색소침착 감지 여부. null이면 이 실측 행이 컬럼 도입 이전 데이터(미측정)",
                    example = "false", nullable = true)
            Boolean pigmentationDetected,

            @Schema(description = "여드름 흉터 감지 여부. null이면 이 실측 행이 컬럼 도입 이전 데이터(미측정)",
                    example = "false", nullable = true)
            Boolean acneScarDetected,

            @Schema(description = "구조적 노화 징후 감지 여부. null이면 이 실측 행이 컬럼 도입 이전 데이터(미측정)",
                    example = "false", nullable = true)
            Boolean agingDetected
    ) {
    }

}
