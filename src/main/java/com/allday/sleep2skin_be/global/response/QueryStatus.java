package com.allday.sleep2skin_be.global.response;

/**
 * 조회 API의 상태 (conventions.md §2).
 *
 * <p><b>빈 상태는 이 서비스의 정상 흐름이다.</b> 신규 사용자·미연결 사용자에게 일상적으로
 * 발생하므로 4xx로 내리지 않는다 — 404로 내리면 경로 오타·잘못된 {@code userId}와 섞여
 * <b>모니터링에서 신규 유입이 에러 급증으로 보인다.</b>
 *
 * <p>사유별로 값을 나눈 것은 앱이 <b>문구가 아니라 상태로 분기</b>하게 하기 위해서다.
 * {@code message}는 그대로 보여줄 수 있는 한국어 문장이지만 다듬어질 수 있어 분기 조건이 될 수 없다.
 */
public enum QueryStatus {

    /** 페이로드가 실려 있다. */
    AVAILABLE,

    /** 그날 수면 데이터가 없다 — 앱이 아직 업로드하지 않았거나 그 날짜에 잔 기록이 없다. */
    NO_SLEEP_DATA,

    /** 기록이 쌓이는 중이라 아직 만들 수 없다 (주간 리포트 7일 미만 등). */
    INSUFFICIENT_HISTORY,

    /** 검증 이력이 없다 (HOME-09 배너 등). */
    NO_VERIFICATION

}
