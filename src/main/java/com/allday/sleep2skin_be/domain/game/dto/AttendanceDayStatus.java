package com.allday.sleep2skin_be.domain.game.dto;

/**
 * 출석 도장판 한 칸의 상태 (HOME-04 — api.md §2.1).
 *
 * <h2>{@code MISSED}와 {@code UPCOMING}을 나눈 것이 이 enum의 존재 이유다</h2>
 *
 * <p>{@code attended: boolean} 하나로 두면 <b>아직 오지 않은 날과 빠뜨린 날이 똑같이
 * {@code false}로 나간다.</b> 오늘이 화요일인데 수·목·금이 "빠뜨림"으로 그려지면, 사용자는
 * <b>하지도 않은 일로 도장판이 비어 있는 것</b>을 보게 된다.
 *
 * <p>판정을 서버가 하는 이유는 <b>"오늘"이 무엇인지 아는 쪽이 하나여야</b> 하기 때문이다.
 * 앱이 {@code date}와 {@code baseDate}를 다시 비교하면 같은 판정이 두 곳에 생긴다.
 *
 * <p><b>{@code QueryStatus}를 재사용하지 않는다.</b> 그쪽은 응답 전체의 조회 상태
 * ({@code AVAILABLE}·{@code NO_SLEEP_DATA})이고 여기는 <b>배열 한 칸</b>의 상태다 —
 * 대응하는 값이 하나도 없다.
 */
public enum AttendanceDayStatus {

    /** 그날 출석 기록이 있다. <b>이번 요청으로 찍혔는지와 무관하다</b> — 재호출이어도 여기다. */
    ATTENDED,

    /** 이미 지난 날인데 출석 기록이 없다. */
    MISSED,

    /** 기준일보다 <b>미래</b>라 아직 판정할 수 없다. 빠뜨린 것이 아니다. */
    UPCOMING

}
