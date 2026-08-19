package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.report.dto.MetricTrend;
import com.allday.sleep2skin_be.domain.report.dto.OverallReportStatus;
import com.allday.sleep2skin_be.domain.report.dto.VolatileDirection;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 종합 리포트 (REP-09~11). 예보 지표 3종(다크서클·혈색·장벽) 각각의 최근 3주 추세와, 클리닉이
 * 필요할 수 있는 신호를 함께 보여준다.
 *
 * <p>2026-08-19 이전에는 "발동 조건"(수면 점수 추세 + 피부 지표 정체)을 만족할 때만 트리아지를
 * 노출하는 구조였다 — {@code triage.triggered}가 그 게이트였다. 그 발동 조건을 완전히 없애고
 * <b>항상 지표 3종 전부의 추세를 보여주는 구조</b>로 바꿨다. 판정 기준은
 * {@link com.allday.sleep2skin_be.domain.report.MetricTrendPolicy} 참고.
 *
 * <p><b>문장을 만들지 않는다.</b> {@code trends}는 판정 결과(추세 값·변동 방향·구간 평균)만
 * 담고, "혈색이 오르다 다시 내렸어요" 같은 문구는 서버가 만들지 않는다 — 문구를 서버에 두면
 * 한 글자 고치는 데 배포가 필요하다.
 */
@Schema(description = "종합 리포트")
public record OverallReportResponse(

        @Schema(description = "조회 상태. 가입일 기준 21일 미만이면 INSUFFICIENT_DATA", example = "FULL")
        OverallReportStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "관찰 기간 시작일 (`baseDate - 20`)", example = "2026-07-25")
        LocalDate periodStart,

        @Schema(description = "관찰 기간 종료일 (`baseDate`)", example = "2026-08-14")
        LocalDate periodEnd,

        @Schema(description = "예보 지표 3종의 최근 3주 추세. `INSUFFICIENT_DATA`면 `null`",
                nullable = true)
        Trends trends,

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

    private static final String INSUFFICIENT_DATA_MESSAGE = "아직 종합 리포트를 만들기에 기록이 부족합니다.";

    /**
     * 가입일 기준 21일 미만인 신규 사용자. {@code trends}는 {@code null}이다 — 관찰 창 자체가
     * 성립하지 않아 지표별 판정을 낼 근거가 없다. {@code clinicNeeded}는 이 게이트와 무관하게
     * 호출부가 항상 계산해 넘긴다(REP-10은 실측 이력 유무로만 결정된다).
     */
    public static OverallReportResponse insufficientData(LocalDate periodStart, LocalDate periodEnd,
                                                          List<SkinMetric> appManaged,
                                                          ClinicNeeded clinicNeeded) {
        return new OverallReportResponse(OverallReportStatus.INSUFFICIENT_DATA, INSUFFICIENT_DATA_MESSAGE,
                periodStart, periodEnd, null, appManaged, clinicNeeded, CLINIC_LINK);
    }

    public static OverallReportResponse of(LocalDate periodStart, LocalDate periodEnd, Trends trends,
                                           List<SkinMetric> appManaged, ClinicNeeded clinicNeeded) {
        return new OverallReportResponse(OverallReportStatus.FULL, null, periodStart, periodEnd, trends,
                appManaged, clinicNeeded, CLINIC_LINK);
    }

    /** 예보 지표 3종 각각의 추세. 셋 다 항상 채워진다 — 표본이 부족한 지표는 값이 아니라 {@code trend}로 표시된다. */
    @Schema(description = "예보 지표 3종의 최근 3주 추세")
    public record Trends(

            @Schema(description = "다크서클 회복 추세")
            MetricTrendResult darkCircle,

            @Schema(description = "혈색 추세")
            MetricTrendResult complexion,

            @Schema(description = "장벽 추세")
            MetricTrendResult barrier
    ) {
    }

    /**
     * 지표 하나의 추세 판정 (REP-09 — {@code MetricTrendPolicy.classify} 참고).
     *
     * @param trend             이 지표의 추세 판정
     * @param volatileDirection {@code trend}가 {@code VOLATILE}일 때만 값이 있다. 그 외에는
     *                          항상 {@code null}
     * @param w1Average         W1(가장 과거 7일) 평균 예보 점수. 그 7일 전부 결측이면
     *                          {@code null}이고, 이때 {@code trend}는 {@code INSUFFICIENT_SAMPLE}이다
     * @param w3Average         W3(baseDate로 끝나는 최근 7일) 평균 예보 점수. 그 7일 전부
     *                          결측이면 {@code null}이고, 이때 {@code trend}는
     *                          {@code INSUFFICIENT_SAMPLE}이다. <b>W2(중간 7일) 평균은 방향
     *                          일관성 체크에만 쓰고 응답에는 싣지 않는다</b>
     */
    @Schema(description = "지표 하나의 추세 판정")
    public record MetricTrendResult(

            @Schema(description = "추세", example = "IMPROVED")
            MetricTrend trend,

            @Schema(description = "VOLATILE일 때의 변동 방향. 그 외 추세에서는 `null`", nullable = true,
                    example = "null")
            VolatileDirection volatileDirection,

            @Schema(description = "W1(가장 과거 7일) 평균 예보 점수. 그 7일 전부 결측이면 `null`",
                    nullable = true, example = "48")
            Integer w1Average,

            @Schema(description = "W3(baseDate로 끝나는 최근 7일) 평균 예보 점수. 그 7일 전부 결측이면 `null`",
                    nullable = true, example = "79")
            Integer w3Average
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
