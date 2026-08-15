package com.allday.sleep2skin_be.domain.sleep.dto.response;

import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 수면 세션 업로드 응답 (api.md §3). <b>업로드인데 예보를 돌려준다</b> — 앱은 이 한 번의 호출로
 * 홈 화면을 그린다.
 *
 * @param processed 이번 요청으로 <b>서버 상태가 바뀌었는가.</b> 같은 데이터를 다시 받았거나
 *                  검증을 마친 날이면 {@code false}이고, 그때도 예보는 정상적으로 실려 나간다
 * @param exp       수면 점수 보상 적립 결과 (HOME-04). <b>{@code processed: false}면 언제나
 *                  {@code gained: 0}이다</b> — 재처리하지 않은 요청이라 새로 산출된 점수가 없다
 */
@Schema(description = "수면 세션 업로드 응답 (수면 집계 + 오늘의 예보 + exp 적립)")
public record SleepSessionUploadResponse(

        @Schema(description = "이번 요청으로 저장·재산출이 일어났는가", example = "true")
        boolean processed,

        @Schema(description = "기준일 — **기상일 기준**이다. 예보·검증이 전부 이 날짜로 묶인다",
                example = "2026-08-07")
        LocalDate sleepDate,

        @Schema(description = "서버가 계산한 수면 집계")
        SleepSummary sleep,

        @Schema(description = "이 수면으로 산출한 피부 예보")
        SkinForecastResponse forecast,

        @Schema(description = "수면 점수 보상 적립 결과 (HOME-04)")
        ExpResponse exp
) {

    /**
     * @param sleepScore 수면 점수 (§10.8). <b>참여 피처가 0개면 {@code null}</b>
     */
    public static SleepSessionUploadResponse of(boolean processed, SleepSession session,
                                                Integer sleepScore,
                                                SkinForecastResponse forecast, ExpResponse exp) {
        return new SleepSessionUploadResponse(processed, session.getSleepDate(),
                SleepSummary.from(session, sleepScore), forecast, exp);
    }

    /**
     * 수면 집계. 전부 서버가 단계 구간에서 계산한 값이며 앱이 보고한 총합이 아니다.
     *
     * <p><b>시각은 UTC(`Z`)로 나간다.</b> 저장 직후에는 요청 오프셋이, 재조회에서는 UTC가 나와
     * 경로마다 표기가 달라지는 것을 막기 위해 한쪽으로 고정했다. 가리키는 순간은 같으므로
     * 앱이 자기 타임존으로 표시하면 된다.
     */
    @Schema(description = "수면 집계 (서버 계산값)")
    public record SleepSummary(

            @Schema(description = "잠든 시각 — 첫 수면 구간의 시작", example = "2026-08-06T14:40:00Z")
            OffsetDateTime sleepOnsetTime,

            @Schema(description = "기상 시각 — 60분 이상 각성이 시작된 시점 또는 마지막 수면이 끝난 시점",
                    example = "2026-08-06T22:10:00Z")
            OffsetDateTime wakeTime,

            @Schema(description = "총 수면 (분). **단계 미상 구간을 포함**하므로 단계별 합보다 클 수 있다",
                    example = "402")
            int totalSleepMinutes,

            @Schema(description = "깊은 수면 (분)", example = "54")
            int deepSleepMinutes,

            @Schema(description = "REM 수면 (분)", example = "71")
            int remSleepMinutes,

            @Schema(description = "코어(얕은) 수면 (분)", example = "277")
            int coreSleepMinutes,

            @Schema(description = "야간 각성 횟수 — **5분 이상** 지속된 각성만 센다", example = "3")
            int awakeCount,

            @Schema(description = "각성 총 시간 (분) — 위와 **같은 구간들만** 합산", example = "21")
            int awakeMinutes,

            @Schema(description = """
                    수면 점수 (0~100, 높을수록 좋음). 그날 스코어링에 **참여한 피처의 부분점수
                    평균**이며 저장하지 않고 매번 계산한다.

                    **참여 피처가 0개면 `null`이다** — 점수 자체가 없는 날이고 0점이 아니다.

                    ⚠️ **피부 예보 점수와 다른 값이다.** 예보는 "이 수면이 피부에 어떻게 나타날까"이고
                    이쪽은 "수면 자체가 어땠나"다. 두 숫자가 화면에 나란히 보이므로 라벨을 섞지 않는다.
                    """, nullable = true, example = "78")
            Integer sleepScore
    ) {
        static SleepSummary from(SleepSession session, Integer sleepScore) {
            return new SleepSummary(
                    utc(session.getSleepOnsetTime()), utc(session.getWakeTime()),
                    session.getTotalSleepMinutes(), session.getDeepSleepMinutes(),
                    session.getRemSleepMinutes(), session.getCoreSleepMinutes(),
                    session.getAwakeCount(), session.getAwakeMinutes(), sleepScore);
        }

        private static OffsetDateTime utc(OffsetDateTime time) {
            return time.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }

}
