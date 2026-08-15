package com.allday.sleep2skin_be.domain.report.dto;

/**
 * 주간·월간 리포트의 조회 상태.
 *
 * <p>일간 리포트({@code SleepSummarySection}·{@code SkinForecastSection})는 두 섹션이 서로
 * 독립적으로 빈 상태가 될 수 있는 합성 응답이라 섹션별 상태를 뒀지만, 주간·월간은 <b>기간
 * 하나가 응답 전체의 성립 여부를 가른다</b> — {@code GET /report/daily/timeline}과 같은 구조로
 * 최상위 상태 하나면 충분하다.
 *
 * <p>기존 {@code QueryStatus}(전역, {@code AVAILABLE}·{@code NO_SLEEP_DATA} 등)를 재사용하지
 * 않는다. 값 이름 자체가 "가입 후 그 기간만큼 지났는가"를 말하는, 이 두 API 고유의 의미라
 * {@code QueryStatus}의 기존 값 중 정확히 대응하는 것이 없다.
 */
public enum ReportPeriodStatus {

    /**
     * 가입일부터 기간 전체가 지나 정상 산출됐다. <b>기간 안의 날짜에 수면 기록이 없어도
     * 여기다</b> — 그건 데이터 품질 문제이지 사용자가 새로워서 생긴 문제가 아니다.
     */
    FULL,

    /** 가입한 지 기간(7일·28일)만큼 지나지 않은 신규 사용자 — 아직 만들 수 없다. */
    INSUFFICIENT_DATA

}
