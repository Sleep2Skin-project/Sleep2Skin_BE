package com.allday.sleep2skin_be.domain.game.dto.response;

import com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 출석 체크인 응답 (HOME-04 — api.md §2.1).
 *
 * <p><b>앱이 시작될 때 한 번 호출하고, 이 응답으로 홈 화면의 출석 완료·연속 검증 보상 팝업과
 * 주간 출석 도장판까지 그린다.</b>
 *
 * @param checkedIn     <b>이번 요청으로 출석이 기록됐는가.</b> 같은 날 재호출이면 {@code false}이며
 *                      <b>{@code 409}가 아니라 {@code 200}이다</b> — 앱은 시작할 때마다 호출하므로
 *                      하루에 다섯 번 켜면 네 번은 재호출이다. 정상 흐름을 에러로 만들면 진짜
 *                      문제가 묻힌다. 대신 이 값으로 팝업을 띄울지 정한다
 * @param streakCount   팝업에 함께 뜨는 <b>연속 검증</b> 횟수. 출석 연속이 아니다 — 출석 연속은
 *                      어디에도 쓰지 않으므로 세지 않는다.
 *                      ⚠️ <b>연속 검증 보상 자체는 이 API가 주지 않는다</b> —
 *                      {@code POST /skin/selfie}가 준다. 여기서 함께 지급하면 셀피를 찍지 않아도
 *                      보상이 나간다
 * @param exp           출석 적립 결과. 재호출이면 {@code gained: 0} · {@code reasons: []}
 * @param weekStartDate 도장판 첫 칸의 날짜 — <b>기준일이 속한 주의 월요일</b>
 * @param weekDays      월~일 <b>7칸 고정</b>. 기록이 없는 날도 빠지지 않는다 — 빼면 도장판 칸 수가
 *                      주마다 달라진다
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
        ExpResponse exp,

        @Schema(description = "도장판 첫 칸 — 기준일이 속한 주의 **월요일**", example = "2026-08-10")
        LocalDate weekStartDate,

        @Schema(description = "월~일 출석 도장판. **항상 7칸**이며 첫 칸이 월요일이다")
        List<AttendanceDayResponse> weekDays
) {

    /**
     * @param weekDays {@code AttendanceWeekCalculator}가 만든 월~일 7칸.
     *                 <b>{@code weekStartDate}를 따로 받지 않는다</b> — 첫 칸의 날짜가 곧 주
     *                 시작일이라, 둘을 각각 받으면 어긋날 자리가 생긴다
     */
    public static AttendanceResponse of(LocalDate baseDate, int streakCount, ExpResponse exp,
                                        List<AttendanceDayResponse> weekDays) {
        // 적립이 일어났다는 것과 출석이 기록됐다는 것이 같은 사실이다 — 이력 행이 곧 출석 기록이다
        return new AttendanceResponse(baseDate, exp.gained() > 0, streakCount, exp,
                weekDays.getFirst().date(), List.copyOf(weekDays));
    }

    /**
     * 도장판 한 칸.
     *
     * @param dayOfWeek 요일. 앱이 {@code date}에서 다시 구할 수 있지만, <b>요일 이름을 그리는 것이
     *                  이 배열의 목적</b>이라 함께 보낸다. {@code "MONDAY"} 같은 영어 상수이며
     *                  <b>표시 문구는 클라이언트가 만든다</b> — 서버가 "월"을 내려보내면 문구 하나
     *                  바꾸는 데 배포가 필요하다
     * @param status    ⚠️ <b>{@code MISSED}와 {@code UPCOMING}을 반드시 구분해 그릴 것.</b> 둘을
     *                  같은 빈 칸으로 그리면 아직 오지 않은 날이 빠뜨린 날처럼 보인다
     */
    @Schema(description = "출석 도장판 한 칸")
    public record AttendanceDayResponse(

            @Schema(description = "날짜", example = "2026-08-10")
            LocalDate date,

            @Schema(description = "요일 (영어 상수). 표시 문구는 클라이언트가 만든다",
                    example = "MONDAY")
            DayOfWeek dayOfWeek,

            @Schema(description = "출석 여부. `UPCOMING`은 기준일보다 미래라 **빠뜨린 것이 아니다**",
                    example = "ATTENDED")
            AttendanceDayStatus status
    ) {
    }

}
