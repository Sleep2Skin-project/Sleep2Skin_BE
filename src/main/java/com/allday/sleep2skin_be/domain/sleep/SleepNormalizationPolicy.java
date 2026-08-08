package com.allday.sleep2skin_be.domain.sleep;

import java.time.Duration;

/**
 * 수면 수집 정규화 정책. 앱이 보낸 단계 구간을 세션 한 행으로 접을 때 쓰는 임계값이다.
 *
 * <p><b>{@code ScoringPolicy}가 아니다.</b> 스코어링 파라미터(등급 컷오프·가중치·정규화 구간)와
 * 성격이 다르다 — 이건 <b>수집 단계의 정규화 규칙</b>이고, 값이 바뀌면 예보 점수가 아니라
 * 저장되는 집계값 자체가 바뀐다. 두 곳을 섞으면 "점수가 이상하다"는 신고를 받았을 때
 * 어느 층에서 틀어졌는지 구분할 수 없다.
 *
 * <p>임계값을 서버 상수로 둔 이유는 <b>앱이 보낸 세션 분할과 각성 횟수를 믿지 않기 위해서</b>다.
 * 기기·OS 버전별로 구간을 쪼개는 기준이 달라 같은 수면도 사용자마다 다르게 잘린다. 서버 상수여야
 * 앱 배포 없이 바꿀 수 있고 모든 사용자에게 같은 기준이 적용된다(erd.md §3.3).
 *
 * <p>⚠️ <b>임계값을 나중에 바꾸면 이미 저장된 세션은 재계산되지 않는다.</b> 과거 데이터와 기준이
 * 달라지므로, 바꾼다면 검증 이력이 쌓이기 전에 바꾼다.
 */
public final class SleepNormalizationPolicy {

    /**
     * 야간 각성으로 셀 최소 지속 시간 — <b>5분</b> (2026-08-06 확정, erd.md §3.3).
     *
     * <p>이보다 짧은 {@code AWAKE}는 뒤척임으로 보고 {@code awakeCount}·{@code awakeMinutes}
     * <b>양쪽 모두에서</b> 버린다. 한쪽에만 걸면 {@code awakeCount=0} · {@code awakeMinutes=12}처럼
     * REP-04 화면에서 모순되는 조합이 나온다.
     *
     * <p>임의로 고른 값이 아니다 — 건강한 성인도 밤에 미세각성이 10~30회 일어나지만 대부분 3분
     * 미만이고, 5분을 넘어가는 각성부터 수면 분절의 문제로 다뤄진다(prd.md §10.5).
     * 임계값이 없으면 30초짜리 뒤척임까지 1회로 잡혀 사람마다 각성 횟수가 20회씩 나온다.
     */
    public static final Duration AWAKE_EPISODE_THRESHOLD = Duration.ofMinutes(5);

    /**
     * 기상으로 판정할 연속 {@code AWAKE} 지속 시간 — <b>60분</b> (2026-08-07 확정, prd.md §4.1).
     *
     * <p>여기서 세션이 끝난다. 그 뒤에 다시 잠들어도 낮잠이므로 같은 행에 들어가지 않는다.
     *
     * <p>⚠️ <b>이 임계값을 빼면 {@code awakeCount}가 구조적으로 항상 0이 된다.</b> "첫 기상까지"를
     * 문자 그대로 구현하면 5분짜리 각성에서도 세션이 끊겨 {@link #AWAKE_EPISODE_THRESHOLD} 이상의
     * 각성이 세션 안에 존재할 수 없다. {@code CHECK} 제약에도 안 걸리고 값 범위도 정상이라
     * <b>다크서클 적중률만 조용히 무너진다</b>(erd.md §3.3).
     */
    public static final Duration WAKE_UP_THRESHOLD = Duration.ofMinutes(60);

    private SleepNormalizationPolicy() {
    }

}
