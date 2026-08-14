package com.allday.sleep2skin_be.domain.game.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 출석 체크인 응답 (HOME-04 — api.md §2.1).
 *
 * <p><b>앱이 시작될 때 한 번 호출하고, 이 응답으로 홈 화면의 출석 완료·연속 검증 보상 팝업까지
 * 그린다.</b>
 *
 * @param checkedIn   <b>이번 요청으로 출석이 기록됐는가.</b> 같은 날 재호출이면 {@code false}이며
 *                    <b>{@code 409}가 아니라 {@code 200}이다</b> — 앱은 시작할 때마다 호출하므로
 *                    하루에 다섯 번 켜면 네 번은 재호출이다. 정상 흐름을 에러로 만들면 진짜
 *                    문제가 묻힌다. 대신 이 값으로 팝업을 띄울지 정한다
 * @param streakCount 팝업에 함께 뜨는 <b>연속 검증</b> 횟수. 출석 연속이 아니다 — 출석 연속은
 *                    어디에도 쓰지 않으므로 세지 않는다.
 *                    ⚠️ <b>연속 검증 보상 자체는 이 API가 주지 않는다</b> —
 *                    {@code POST /skin/selfie}가 준다. 여기서 함께 지급하면 셀피를 찍지 않아도
 *                    보상이 나간다
 * @param exp         출석 적립 결과. 재호출이면 {@code gained: 0} · {@code reasons: []}
 */
@Schema(description = "출석 체크인 응답")
public record AttendanceResponse(

        @Schema(description = "기준일", example = "2026-08-14")
        LocalDate baseDate,

        @Schema(description = "이번 요청으로 출석이 기록됐는가. 같은 날 재호출이면 `false`",
                example = "true")
        boolean checkedIn,

        @Schema(description = "연속 **검증** 횟수 (출석 연속이 아니다). HOME-09·MY-01과 같은 값",
                example = "3")
        int streakCount,

        @Schema(description = "출석 적립 결과")
        ExpResponse exp
) {

    public static AttendanceResponse of(LocalDate baseDate, int streakCount, ExpResponse exp) {
        // 적립이 일어났다는 것과 출석이 기록됐다는 것이 같은 사실이다 — 이력 행이 곧 출석 기록이다
        return new AttendanceResponse(baseDate, exp.gained() > 0, streakCount, exp);
    }

}
