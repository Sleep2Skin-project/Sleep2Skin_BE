package com.allday.sleep2skin_be.global.infra.openai;

/**
 * LLM Vision 이 셀피에서 읽어낸 지표 3종. <b>전부 0~100 이고 높을수록 좋은 상태다.</b>
 *
 * <p><b>{@code SkinMetric} 을 쓰지 않고 필드 3개로 편 것은 의존 방향 때문이다.</b>
 * {@code SkinMetric} 은 {@code domain/skin} 의 enum 이고 이 패키지는 {@code global} 이다 —
 * {@code global → domain} 참조는 금지돼 있다(architecture.md §2). 지표가 3종으로 고정이라
 * (prd.md §4.1) 필드로 펴도 늘어날 일이 없다.
 *
 * <p><b>⚠️ {@code darkCircle} 은 "다크서클이 심한 정도"가 아니라 "회복된 정도"다.</b>
 * 눈 밑이 맑을수록 100 이다. 방향이 뒤집혀도 값은 0~100 정수라 {@code CHECK} 제약도
 * Structured Outputs 스키마도 걸러내지 못하고 <b>적중률만 조용히 무너진다.</b> 방어선은
 * {@link SkinVisionPrompt} 의 필드 설명 하나뿐이다.
 *
 * @param darkCircle 다크서클 회복 — 100 이면 눈 밑이 맑다
 * @param complexion 혈색 — 100 이면 안색에 생기가 돈다
 * @param barrier    장벽 — 100 이면 장벽이 튼튼하다
 */
public record SkinVisionScores(int darkCircle, int complexion, int barrier) {
}
