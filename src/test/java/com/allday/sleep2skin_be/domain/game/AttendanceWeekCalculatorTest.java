package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus;
import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse.AttendanceDayResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus.ATTENDED;
import static com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus.MISSED;
import static com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출석 도장판 계산 (HOME-04). DB 없이 도는 순수 로직이다.
 */
class AttendanceWeekCalculatorTest {

    /** 2026-08-10은 월요일, 2026-08-16은 일요일이다. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 16);

    private final AttendanceWeekCalculator calculator = new AttendanceWeekCalculator();

    @Test
    @DisplayName("항상 7칸이고 첫 칸이 월요일이다")
    void 월요일부터_7칸이다() {
        List<AttendanceDayResponse> week = calculator.calculate(SUNDAY, List.of());

        assertThat(week).hasSize(7);
        assertThat(week.getFirst().date()).isEqualTo(MONDAY);
        assertThat(week.getLast().date()).isEqualTo(SUNDAY);
        assertThat(week).extracting(AttendanceDayResponse::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY);
    }

    /**
     * <b>도장판은 요일 자리가 고정돼야 그릴 수 있다.</b> 리포트 주간(REP-06)처럼 롤링 7일이면
     * 첫 칸의 요일이 매일 바뀐다 — 두 기간은 앵커가 다르다.
     */
    @Test
    @DisplayName("기준일이 주중 어디든 같은 주면 같은 7칸이 나온다")
    void 기준일이_달라도_주가_같으면_칸이_같다() {
        LocalDate wednesday = MONDAY.plusDays(2);

        List<LocalDate> fromSunday = calculator.calculate(SUNDAY, List.of()).stream()
                .map(AttendanceDayResponse::date).toList();

        assertThat(calculator.calculate(wednesday, List.of()))
                .extracting(AttendanceDayResponse::date)
                .containsExactlyElementsOf(fromSunday);
    }

    @Test
    @DisplayName("월요일이 기준일이면 그날이 주 시작일이다")
    void 월요일은_그날이_주_시작일이다() {
        assertThat(calculator.weekStartOf(MONDAY)).isEqualTo(MONDAY);
        assertThat(calculator.weekStartOf(SUNDAY)).isEqualTo(MONDAY);
        assertThat(calculator.weekStartOf(MONDAY.minusDays(1))).isEqualTo(MONDAY.minusWeeks(1));
    }

    @Test
    @DisplayName("출석한 날은 ATTENDED, 지나간 빈 날은 MISSED다")
    void 출석과_결석을_가른다() {
        LocalDate friday = MONDAY.plusDays(4);

        List<AttendanceDayStatus> statuses =
                statusesOf(calculator.calculate(friday, List.of(MONDAY, MONDAY.plusDays(2), friday)));

        assertThat(statuses).containsExactly(
                ATTENDED,   // 월 — 출석
                MISSED,     // 화
                ATTENDED,   // 수 — 출석
                MISSED,     // 목
                ATTENDED,   // 금 — 기준일
                UPCOMING,   // 토
                UPCOMING);  // 일
    }

    /**
     * <b>이 구분이 3상태 enum의 존재 이유다.</b> 아직 오지 않은 날을 {@code MISSED}로 두면
     * 사용자가 하지도 않은 일로 도장판이 비어 있는 것을 보게 된다.
     */
    @Test
    @DisplayName("기준일 이후는 MISSED가 아니라 UPCOMING이다")
    void 미래는_결석이_아니다() {
        List<AttendanceDayStatus> statuses = statusesOf(calculator.calculate(MONDAY, List.of()));

        assertThat(statuses.getFirst()).isEqualTo(MISSED);          // 월 — 기준일
        assertThat(statuses.subList(1, 7)).containsOnly(UPCOMING);  // 화~일
    }

    /** 기준일 당일은 미래가 아니다 — 오늘 칸은 판정 대상이다. */
    @Test
    @DisplayName("기준일 당일에 출석했으면 오늘 칸이 ATTENDED다")
    void 오늘_출석은_오늘_칸에_찍힌다() {
        LocalDate tuesday = MONDAY.plusDays(1);

        assertThat(statusesOf(calculator.calculate(tuesday, List.of(tuesday))).get(1))
                .isEqualTo(ATTENDED);
    }

    /** 조회 상한이 기준일이라 실제로는 오지 않지만, 섞여 들어와도 7칸을 오염시키지 않는다. */
    @Test
    @DisplayName("주 밖의 날짜가 섞여 들어와도 무시된다")
    void 주_밖의_날짜는_무시된다() {
        List<AttendanceDayStatus> statuses = statusesOf(
                calculator.calculate(SUNDAY, List.of(MONDAY.minusDays(1), SUNDAY.plusDays(1))));

        assertThat(statuses).containsOnly(MISSED);
    }

    private List<AttendanceDayStatus> statusesOf(List<AttendanceDayResponse> week) {
        return week.stream().map(AttendanceDayResponse::status).toList();
    }

}
