package com.allday.sleep2skin_be.domain.skin.dto;

/**
 * 지표를 산출하지 못한 사유 (api.md §3 「지표가 빈 경우」).
 *
 * <p><b>에러가 아니라 정상 응답이다.</b> 신규 사용자에게 일상적으로 발생하므로 4xx로 취급하면
 * 진짜 문제가 묻힌다. {@code null}만 주면 앱이 문구를 고를 수 없어 사유를 함께 준다.
 */
public enum UnavailableReason {

    /**
     * 워치 미착용 — {@code HRV}·{@code RESTING_HEART_RATE}가 없다.
     *
     * <p>취침 규칙성까지 함께 없어 {@code COMPLEXION} 피처가 전멸했을 때도 이 사유를 쓴다.
     * 둘 다 해당하면 <b>워치를 차는 쪽이 훨씬 빨리 해소되기 때문</b>이다 — 오늘 밤 차고 자면
     * 내일 바로 피처 2개가 살아나지만, 규칙성은 최소 3일을 더 기다려야 한다.
     */
    MISSING_FEATURES,

    /**
     * 취침 규칙성 이력 3일 미만 — 신규 사용자. 2점으로 낸 표준편차는 규칙성이 아니다.
     *
     * <p>⚠️ <b>현재 매핑(§10.3)에서는 이 사유가 나오지 않는다.</b> {@code COMPLEXION}이 비려면
     * 피처 3개가 전부 없어야 하는데, {@code HRV}나 안정시 심박이 하나라도 있으면 그 순간 혈색은
     * 산출된다 — 즉 "혈색이 빈다 ⇒ 워치도 없다"가 항상 참이라 {@link #MISSING_FEATURES}가 이긴다.
     * api.md §3의 빈 상태 사유 표에는 남아 있으므로 값 자체는 유지한다. 규칙성이 단독 피처인
     * 지표가 생기면 그때 도달한다.
     */
    INSUFFICIENT_HISTORY,

    /**
     * 단계 합({@code deep + rem + core})이 0 — 단계가 하나도 안 잡힌 밤.
     *
     * <p><b>0점으로 발급하지 않는다.</b> 그대로 계산하면 {@code BARRIER} = 0점 = "위험"이 나가는데,
     * 이건 "장벽이 위험하다"가 아니라 "측정하지 못했다"이며 <b>없는 위험을 경고하는 것</b>이다.
     */
    NO_SLEEP_STAGES

}
