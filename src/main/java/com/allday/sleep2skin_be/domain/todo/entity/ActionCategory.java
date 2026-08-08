package com.allday.sleep2skin_be.domain.todo.entity;

/**
 * 액션 카테고리. <b>2종 고정이다.</b>
 *
 * <p>처방(HOME-07 이후)과 TODO 탭이 같은 마스터를 쓰고, 둘 다 "피해야 할 것 / 해야 할 것"
 * 두 갈래로만 보여준다. 초안에 있던 {@code NIGHT_CHECK}(밤 체크리스트)은 기능 자체가 빠지면서
 * 함께 제거됐다.
 *
 * <p>세 번째 카테고리가 사라지면서 <b>모든 항목이 동일한 규칙 하나로 처리된다</b> —
 * 임계값 매칭 → 심각도 가중 정렬 → 상위 3개 절단. "이 카테고리만 자르지 않는다" 같은 예외가
 * 없어 추천 엔진에 분기가 생기지 않는다.
 */
public enum ActionCategory {

    AVOID,   // 피하세요
    DO       // 이렇게

}
