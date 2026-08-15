package com.allday.sleep2skin_be.domain.game.dto;

import com.allday.sleep2skin_be.domain.game.entity.ExpReason;

/**
 * exp 적립 한 건의 요청. {@code ExpService}로 들어가는 입력이다.
 *
 * <p><b>양을 호출부가 정해서 넘긴다.</b> 값 자체는 전부 {@code LevelPolicy}에서 나오지만, 어떤
 * 구간·어떤 증가폭인지는 그날의 상태를 아는 도메인만 판단할 수 있다 — 연속 일수는 {@code skin}이,
 * 수면 점수 증감은 {@code sleep}이 안다.
 *
 * @param reason 적립 사유
 * @param amount 지급량. <b>회수(TODO 되돌리기)에서는 음수다</b>
 */
public record ExpGrantCommand(ExpReason reason, int amount) {
}
