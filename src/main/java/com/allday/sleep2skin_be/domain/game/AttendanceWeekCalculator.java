package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.AttendanceDayStatus;
import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse.AttendanceDayResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 출석 도장판 — 기준일이 속한 주의 월~일 7칸 (HOME-04).
 *
 * <h2>달력 주다 — 리포트 주간과 앵커가 다르다</h2>
 *
 * <p>리포트 주간(REP-06)은 {@code baseDate − 6 ~ baseDate}인 <b>롤링 7일</b>이지만, 여기는
 * <b>월요일에 고정된 달력 주</b>다. 도장판은 요일 자리가 고정돼야 그릴 수 있다 — 롤링이면 첫 칸의
 * 요일이 매일 바뀐다.
 *
 * <p><b>그래서 필드 이름에 {@code weekly}를 쓰지 않는다.</b> 같은 단어를 쓰면 두 기간이 같은
 * 규칙인 줄 읽히고, 나중에 한쪽 기준으로 다른 쪽을 고치게 된다.
 *
 * <h2>주 시작일은 {@code baseDate}에서 역산한다 — 서버 시각이 아니다</h2>
 *
 * <p>서버는 "오늘"을 모른다({@code users}에 {@code time_zone}이 없다). 서버 시각으로 계산하면
 * 한국 시간 오전 9시 이전에 <b>주가 통째로 하루 밀린다</b> — 월요일 아침에 지난주 도장판이 뜬다.
 *
 * <p><b>DB를 보지 않는 순수 계산이다.</b> 출석한 날짜를 받아서 판정만 한다.
 */
@Component
public class AttendanceWeekCalculator {

    /** 월~일 7칸. 주의 첫 칸은 언제나 월요일이다. */
    private static final int DAYS_IN_WEEK = 7;

    /**
     * 기준일이 속한 주의 월요일부터 일요일까지 7칸을 만든다.
     *
     * <p><b>기준일 이후는 {@code UPCOMING}이다</b> — 기록이 없다고 {@code MISSED}로 두면 아직
     * 오지도 않은 날이 "빠뜨림"으로 그려진다.
     *
     * @param baseDate      앱이 알려준 "오늘". <b>서버 시각으로 대신하지 않는다</b>
     * @param attendedDates 그 주에 출석한 날짜들. 순서는 상관없고 주 밖의 날짜가 섞여 있어도 된다 —
     *                      7칸을 날짜로 조회하므로 걸러진다
     */
    public List<AttendanceDayResponse> calculate(LocalDate baseDate,
                                                 Collection<LocalDate> attendedDates) {

        LocalDate weekStart = weekStartOf(baseDate);
        Set<LocalDate> attended = new HashSet<>(attendedDates);

        return IntStream.range(0, DAYS_IN_WEEK)
                .mapToObj(weekStart::plusDays)
                .map(date -> new AttendanceDayResponse(
                        date, date.getDayOfWeek(), statusOf(date, baseDate, attended)))
                .toList();
    }

    /**
     * 기준일이 속한 주의 월요일. 기준일이 월요일이면 그날 자신이다.
     *
     * <p>{@code Locale}에 기대지 않는다 — 로케일 기본 주 시작일은 지역마다 다르고(미국은 일요일),
     * 서버 로케일이 바뀌면 <b>도장판이 조용히 하루 밀린다.</b>
     */
    public LocalDate weekStartOf(LocalDate baseDate) {
        return baseDate.minusDays(baseDate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private AttendanceDayStatus statusOf(LocalDate date, LocalDate baseDate,
                                         Set<LocalDate> attended) {
        if (attended.contains(date)) {
            return AttendanceDayStatus.ATTENDED;
        }
        // 기준일 당일은 미래가 아니다 — 아직 체크인하지 않았어도 오늘은 판정 대상이다
        return date.isAfter(baseDate) ? AttendanceDayStatus.UPCOMING : AttendanceDayStatus.MISSED;
    }

}
