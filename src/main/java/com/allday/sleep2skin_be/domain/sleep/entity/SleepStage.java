package com.allday.sleep2skin_be.domain.sleep.entity;

/**
 * 수면 단계. 앱이 {@code POST /api/v1/sleep/sessions}로 보내는 구간의 단계값이다(api.md §3).
 *
 * <p><b>{@link #UNSPECIFIED}를 {@link #CORE}로 바꿔 보내면 안 된다.</b> 스코어링의 비율 분모가
 * {@code deep + rem + core}인데(prd.md §10.5), 미상 구간이 {@code core}에 섞이면 측정하지 못한
 * 시간이 "깊은 수면이 아니었던 시간"으로 계산되어 <b>장벽 점수만 조용히 틀린다.</b>
 * 미상 구간은 {@code total_sleep_minutes}에만 반영된다.
 *
 * <p>{@link #AWAKE}를 제외한 나머지가 {@code asleep} 구간이다 — 총 수면 시간의 합산 대상이고,
 * 세션의 시작({@code sleep_onset_time})과 끝({@code wake_time})을 정하는 기준이 된다.
 */
public enum SleepStage {

    DEEP,           // 깊은 수면
    REM,            // REM 수면
    CORE,           // 얕은 수면 (HealthKit asleepCore)
    AWAKE,          // 각성
    UNSPECIFIED;    // 단계 미상 (HealthKit asleepUnspecified)

    /** 잠들어 있는 구간인가. 각성만 아니면 전부 수면이다. */
    public boolean isAsleep() {
        return this != AWAKE;
    }

}
