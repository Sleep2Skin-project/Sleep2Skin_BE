package com.allday.sleep2skin_be.domain.sleep.dto.request;

import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 수면 세션 업로드 요청 (api.md §3).
 *
 * <p><b>집계값을 받지 않는다.</b> 총 수면·단계별 분·각성 횟수를 앱이 보내지 않는 이유는 서버가
 * 세션을 첫 기상에서 자르기 때문이다 — 앱이 보고한 총합에는 그 뒤의 낮잠이 섞여 있을 수 있다.
 * <b>서버가 자를 거면 서버가 세는 것이 맞다.</b>
 *
 * <p><b>{@code baseDate}를 받지 않는다.</b> 날짜가 필요한 API는 전부 받는다는 공통 규약의
 * 유일한 예외다 — 여기서는 서버가 {@code wakeTime}에서 {@code sleepDate}를 정한다.
 */
@Schema(description = "수면 세션 업로드 요청")
public record SleepSessionUploadRequest(

        @Schema(description = """
                수면 단계 구간 배열. 시간순이 아니어도 서버가 정렬한다.

                **`UNSPECIFIED`를 `CORE`로 바꿔 보내면 안 된다.** 스코어링의 비율 분모가
                `deep + rem + core`라 미상 구간이 `core`에 섞이면 장벽 점수만 조용히 틀린다.
                미상 구간은 총 수면에만 반영된다.

                **`inBed`는 보내지 않는다.** 서버가 무시한다.
                """)
        @NotEmpty(message = "수면 단계 구간은 최소 1개 이상이어야 합니다.")
        @Valid
        List<SleepSegmentRequest> segments,

        @Schema(description = "야간 심박변이도 RMSSD (ms). **워치를 착용하지 않았으면 `null`**",
                example = "41.2")
        BigDecimal hrv,

        @Schema(description = "야간 안정시 심박 (bpm). **워치를 착용하지 않았으면 `null`**",
                example = "63")
        Integer restingHeartRate
) {

    public SleepNormalizationCommand toCommand() {
        return new SleepNormalizationCommand(
                segments.stream().map(SleepSegmentRequest::toCommand).toList(),
                hrv, restingHeartRate);
    }

    /**
     * 단계 구간 하나.
     *
     * <p>시각은 {@link OffsetDateTime}이라 <b>오프셋이 반드시 있어야 한다.</b> 없으면 역직렬화가
     * 실패해 {@code 400}이 난다 — 조용히 UTC로 해석되어 {@code sleepDate}가 하루 밀리는 것보다 낫다.
     */
    @Schema(description = "수면 단계 구간")
    public record SleepSegmentRequest(

            @Schema(description = "수면 단계", example = "CORE",
                    allowableValues = {"AWAKE", "CORE", "DEEP", "REM", "UNSPECIFIED"})
            @NotNull(message = "수면 단계는 필수입니다.")
            SleepStage stage,

            @Schema(description = "구간 시작 시각. **오프셋 필수**", example = "2026-08-06T23:40:00+09:00")
            @NotNull(message = "구간 시작 시각은 필수입니다.")
            OffsetDateTime startTime,

            @Schema(description = "구간 종료 시각. **오프셋 필수**", example = "2026-08-07T00:55:00+09:00")
            @NotNull(message = "구간 종료 시각은 필수입니다.")
            OffsetDateTime endTime
    ) {
        public SleepSegmentCommand toCommand() {
            return new SleepSegmentCommand(stage, startTime, endTime);
        }
    }

}
