package com.allday.sleep2skin_be.domain.sleep.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 정규화 결과. {@code sleep_session} 한 행 + {@code sleep_stage_segment} 여러 행이 될 값들이다.
 *
 * <p>저장은 하지 않는다 — 이 결과를 엔티티로 옮기는 것은 상위 서비스의 일이다. 정규화는
 * 순수 계산이라 DB 없이 단위 테스트로 검증된다.
 *
 * @param sleepDate         <b>기상일 기준.</b> {@code wakeTime}의 날짜다. 이래야
 *                          {@code skin_forecast.base_date}와 같은 값이 되어 변환 없이 조인된다
 * @param sleepOnsetTime    잠든 시각 — 첫 {@code asleep} 구간의 시작
 * @param wakeTime          기상 시각 — 60분 이상 {@code AWAKE}가 시작되는 시점과 마지막
 *                          {@code asleep}이 끝나는 시점 중 앞선 쪽
 * @param totalSleepMinutes {@code asleep} 구간의 합. <b>{@code UNSPECIFIED}를 포함한다</b> —
 *                          {@code deep + rem + core}보다 클 수 있고, 그 차이가 곧 미상 구간의
 *                          길이다. 단, 스코어링의 비율 분모는 이 값이 아니라
 *                          {@code deep + rem + core}다(prd.md §10.5)
 * @param awakeCount        5분 이상 지속된 <b>세션 내부</b> 각성 구간의 개수
 * @param awakeMinutes      위와 <b>같은 구간들만</b> 합산한 총 시간. 표시 전용이며 스코어링
 *                          피처가 아니다(prd.md §10.3)
 * @param segments          잘라낸 뒤 남은 세션 구간 — {@code sleepOnsetTime}부터 {@code wakeTime}까지.
 *                          {@code UNSPECIFIED}도 그대로 남는다
 * @param payloadHash       정규화 결과의 SHA-256 hex(64자). 같은 수면의 재수신을 저장 전에 걸러낸다
 */
public record SleepNormalizationResult(

        LocalDate sleepDate,

        OffsetDateTime sleepOnsetTime,

        OffsetDateTime wakeTime,

        int totalSleepMinutes,

        int deepSleepMinutes,

        int remSleepMinutes,

        int coreSleepMinutes,

        int awakeCount,

        int awakeMinutes,

        BigDecimal hrv,

        Integer restingHeartRate,

        List<SleepSegmentCommand> segments,

        String payloadHash
) {

    /**
     * 스코어링의 비율 분모 — <b>총 수면이 아니라 단계 합이다</b>(prd.md §10.5).
     *
     * <p>총 수면에는 단계 미상 구간이 섞일 수 있는데 그걸 분모에 넣으면 <b>측정하지 못한 시간이
     * "깊은 수면이 아니었던 시간"으로 계산된다.</b> 420분 중 45분만 잡힌 밤에서 깊은수면 20분은
     * 총 수면 분모로는 4.8%(0점)지만 단계 합 분모로는 44%로 정상 범위다.
     *
     * <p>{@code 0}이면 단계가 하나도 안 잡힌 밤이며 <b>{@code BARRIER}를 빈 상태로</b> 낸다 —
     * 0점으로 발급하지 않는다. 0점과 결측은 다르고, 그대로 계산하면 없는 위험을 경고하게 된다
     * (prd.md §10.6).
     */
    public int stagedSleepMinutes() {
        return deepSleepMinutes + remSleepMinutes + coreSleepMinutes;
    }

}
