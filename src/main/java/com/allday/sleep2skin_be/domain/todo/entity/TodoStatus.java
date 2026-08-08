package com.allday.sleep2skin_be.domain.todo.entity;

/**
 * TODO 상태. <b>2종뿐이라 되돌리기도 같은 엔드포인트</b>({@code PATCH /todo/{id}})로 처리된다.
 *
 * <p>초안에는 {@code SCHEDULED}(알림 예약)·{@code UNCHECKABLE}(미확인)이 더 있었으나 둘 다
 * 밤 체크리스트 전용이었고, 그 기능이 빠지면서 함께 제거했다.
 *
 * <p>남은 항목은 전부 사용자가 직접 체크할 수 있다 — 그래서 달성률이 {@code DONE / 전체}로
 * 단순해진다. 분모에서 빼야 할 상태가 없다.
 */
public enum TodoStatus {

    PENDING,   // 미완료 (기본값)
    DONE       // 완료

}
