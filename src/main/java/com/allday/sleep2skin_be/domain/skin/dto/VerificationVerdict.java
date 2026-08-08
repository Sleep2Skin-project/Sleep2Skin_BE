package com.allday.sleep2skin_be.domain.skin.dto;

/**
 * 검증 판정 (HOME-07, prd.md §10.2 확정). <b>지표별로 따로 판정하고, 적중률은 3개 중 적중 비율이다.</b>
 *
 * <p>기준은 {@code 예보 − 실측}이다.
 *
 * <p><b>"과소/과대"는 점수 축 기준이다.</b> 점수는 높을수록 좋으므로 <b>점수를 과소예측한 것은
 * 피부 위험을 과대평가한 것</b>이다. 두 축이 서로 반대라, 문구를 쓸 때 어느 축인지 밝히지 않으면
 * 뜻이 뒤집힌다 — "위험을 낮게 예측했다"는 {@link #OVERESTIMATED}를 가리킨다.
 */
public enum VerificationVerdict {

    /** ±5점 이내 — 예보와 실측이 거의 일치. */
    HIT,

    /** ±6~15점 — 비슷하게 예측. */
    CLOSE,

    /** −16점 이하 — 예보 점수를 실제보다 낮게 냈다(= 피부 위험을 과대평가). */
    UNDERESTIMATED,

    /** +16점 이상 — 예보 점수를 실제보다 높게 냈다(= 피부 위험을 과소평가). */
    OVERESTIMATED

}
