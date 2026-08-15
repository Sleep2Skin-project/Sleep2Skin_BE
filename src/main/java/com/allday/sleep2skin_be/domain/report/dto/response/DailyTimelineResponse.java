package com.allday.sleep2skin_be.domain.report.dto.response;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 일간 수면 타임라인 (REP-03).
 *
 * <p>{@code GET /skin/forecast}(`SkinForecastQueryResponse`)와 같은 최상위 {@code status}·
 * {@code message}로 빈 상태를 표현한다 — 세션 하나의 존재 여부가 곧 응답 전체의 존재 여부와
 * 같아서(합성 응답인 {@link DailyReportResponse}와 달리 섹션을 나눌 이유가 없다) 상태 하나로
 * 충분하다.
 *
 * <p><b>다만 페이로드를 하나의 중첩 객체로 묶지는 않는다.</b> {@code SkinForecastQueryResponse}는
 * 페이로드를 {@code forecast} 하나에 담지만, 여기서는 {@code sleepOnsetTime}·{@code wakeTime}·
 * {@code segments}를 최상위에 나란히 둔다 — 셋 다 타임라인 렌더링에 함께 쓰이는 값이라 한 단계
 * 더 감쌀 실익이 없다.
 *
 * <p>{@code segments}의 정렬은 {@code SleepStageSegmentRepository}의 파생 쿼리
 * ({@code OrderByStartTimeAsc})가 SQL {@code ORDER BY}로 보장한다 — 여기서 다시 정렬하지 않는다.
 */
@Schema(description = "일간 수면 타임라인")
public record DailyTimelineResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "조회 기준일", example = "2026-08-14")
        LocalDate baseDate,

        @Schema(description = "잠든 시각. 세션이 없으면 `null`", nullable = true)
        OffsetDateTime sleepOnsetTime,

        @Schema(description = "기상 시각. 세션이 없으면 `null`", nullable = true)
        OffsetDateTime wakeTime,

        @Schema(description = "수면 단계 구간. `startTime` 오름차순(리포지토리가 보장). 세션이 없으면 빈 배열")
        List<SegmentResponse> segments
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "그날 수면 데이터가 없습니다.";

    /**
     * @param segments <b>이미 {@code startTime} 오름차순으로 정렬돼 들어온다.</b> 호출부가
     *                 {@code SleepStageSegmentRepository.findBySleepSessionIdOrderByStartTimeAsc}로
     *                 조회한 결과를 그대로 넘기면 된다
     */
    public static DailyTimelineResponse of(LocalDate baseDate, SleepSession session,
                                           List<SleepStageSegment> segments) {
        return new DailyTimelineResponse(QueryStatus.AVAILABLE, null, baseDate,
                session.getSleepOnsetTime(), session.getWakeTime(),
                segments.stream().map(SegmentResponse::of).toList());
    }

    public static DailyTimelineResponse empty(LocalDate baseDate) {
        return new DailyTimelineResponse(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE, baseDate,
                null, null, List.of());
    }

    /**
     * 구간 하나. <b>집계값(분·비율)을 담지 않는다</b> — 그건 {@code SleepSession}이 이미 들고
     * 있고 {@code GET /report/daily}가 내보낸다. 여기는 렌더링을 위해 시각만 그대로 전달한다.
     */
    @Schema(description = "수면 단계 구간")
    public record SegmentResponse(

            @Schema(description = "단계 (`DEEP`·`REM`·`CORE`·`AWAKE`·`UNSPECIFIED`)", example = "DEEP")
            SleepStage stage,

            @Schema(description = "구간 시작 시각")
            OffsetDateTime startTime,

            @Schema(description = "구간 종료 시각")
            OffsetDateTime endTime
    ) {
        static SegmentResponse of(SleepStageSegment segment) {
            return new SegmentResponse(segment.getStage(), segment.getStartTime(), segment.getEndTime());
        }
    }

}
