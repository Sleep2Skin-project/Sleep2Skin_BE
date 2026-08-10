package com.allday.sleep2skin_be.domain.skin.dto;

import java.time.LocalDate;

/**
 * 예보와 실측이 모두 있는 하루. <b>검증 이력 테이블이 없어서 조인으로 만든다</b>(erd.md §4).
 *
 * <p>저장할 게 남지 않아 테이블을 만들지 않았다 — 예보·실측은 각자 남아 있고, 오차·판정·적중률은
 * 전부 그 둘에서 나온다. <b>검증을 마친 날의 예보가 절대 바뀌지 않기로</b> 한 덕분에 스냅샷도
 * 필요 없다.
 *
 * <p><b>예보 쪽만 {@code null}일 수 있다.</b> 실측 3종은 {@code NOT NULL}이다 — LLM은 예보와
 * 무관하게 셋을 산출한다. 갈리는 것은 대조 가능 여부이며, 예보가 없는 지표는 판정에서 빠지고
 * <b>적중률 분모에서도 빠진다.</b>
 */
public record VerifiedDay(

        LocalDate baseDate,

        int forecastDarkCircle,

        Integer forecastComplexion,

        Integer forecastBarrier,

        int measuredDarkCircle,

        int measuredComplexion,

        int measuredBarrier
) {
}
