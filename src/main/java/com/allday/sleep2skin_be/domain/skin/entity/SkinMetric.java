package com.allday.sleep2skin_be.domain.skin.entity;

/**
 * 피부 지표. <b>3종 고정이다.</b>
 *
 * <p>유분(sebum)·칙칙함(dullness)·색소침착(pigmentation)은 기능명세서 초안에 등장하지만
 * 최종 확정에서 제외됐다. 코드에 추가하지 않는다.
 *
 * <p><b>셋 다 0~100이고 높을수록 좋은 상태다.</b> {@link #DARK_CIRCLE}은 "다크서클이 심한 정도"가
 * 아니라 <b>"회복된 정도"</b>다 — 각성이 많은 밤일수록 점수가 내려간다. UI 표시명이
 * "다크서클 회복"인 이유다.
 *
 * <p>예보와 실측이 <b>같은 방향</b>이어야 HOME-07 대조가 성립한다. LLM Vision 프롬프트에서 이
 * 방향이 뒤집히면 값 범위·타입은 그대로라 어떤 제약에도 걸리지 않고 적중률만 조용히 무너진다.
 */
public enum SkinMetric {

    DARK_CIRCLE,   // 다크서클 회복
    COMPLEXION,    // 혈색
    BARRIER        // 장벽

}
