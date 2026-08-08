package com.allday.sleep2skin_be.domain.sleep.dto;

/**
 * 수면 통역 카드의 어조. 앱은 <b>문구가 아니라 이 값으로</b> 아이콘·색을 고른다.
 */
public enum InterpretationTone {

    /** 모든 피처가 안정 구간이라 지적할 것이 없다. */
    PRAISE,

    /** 기준치에서 가장 멀어진 피처를 짚었다. */
    IMPROVE

}
