package com.allday.sleep2skin_be.domain.skin.entity;

/**
 * 스코어링 피처. <b>7종이며 {@code personal_weight} 7행과 1:1 대응한다</b>(prd.md §10.3).
 *
 * <p><b>저장하는 값과 피처는 다르다.</b> 코어 수면·기상 시각·각성 총 시간은 {@code sleep_session}에
 * 저장하지만 피처가 아니고, 반대로 {@link #BEDTIME_REGULARITY}는 컬럼이 아니라
 * {@code sleep_onset_time}에서 파생된다(최근 7일 표준편차, 3일 미만이면 결측과 동일 처리).
 *
 * <p><b>피처 → 지표 매핑을 여기 필드로 박지 않는다.</b> {@code ScoringPolicy}가 매핑을 들고 있게 한다 —
 * 확정값이 됐어도 조정될 수 있고, 그때 enum이 아니라 정책 한 파일만 바뀌어야 한다.
 * 정규화 구간(§10.5)도 같은 이유로 {@code ScoringPolicy}에 둔다.
 *
 * <p><b>{@code AWAKE_MINUTES}(각성 총 시간)는 여기 없다.</b> 수집·저장은 하지만 표시 전용으로
 * 확정됐다 — 각성 횟수와 같은 5분 임계값·같은 구간 집합에서 나와 중복 상관이 강하다.
 * 배정을 미룬 게 아니라 넣지 않기로 정한 것이니 추가하지 말 것.
 */
public enum SleepFeature {

    AWAKE_COUNT,          // 야간 각성 횟수      → DARK_CIRCLE
    TOTAL_SLEEP,          // 총 수면 시간        → DARK_CIRCLE
    DEEP_SLEEP,           // 깊은 수면           → BARRIER      (입력은 분이 아니라 비율 %)
    REM_SLEEP,            // REM 수면            → BARRIER      (입력은 분이 아니라 비율 %)
    BEDTIME_REGULARITY,   // 취침 규칙성         → COMPLEXION
    HRV,                  // 심박변이도          → COMPLEXION   (결측 가능)
    RESTING_HEART_RATE    // 안정시 심박         → COMPLEXION   (결측 가능)

}
