package com.allday.sleep2skin_be.domain.skin;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 연속 검증 횟수 (prd.md §4.2 E5 — 2026-08-07 확정).
 *
 * <pre>
 * 연속 검증 횟수 = skin_measurement.base_date 가
 *                 오늘 또는 어제부터 하루도 빠짐없이 이어진 날짜의 개수
 * </pre>
 *
 * <h2>오늘 미검증이 연속을 끊지 않는다 — 이 규칙의 핵심이다</h2>
 *
 * <p>저녁에 검증하는 사용자가 아침에 앱을 열었을 때 어제까지 쌓은 연속이 {@code 0}으로 보이면,
 * <b>아직 하지 않은 일로 사용자를 벌주는 것</b>처럼 읽힌다. 그래서 기준일에 검증이 없으면
 * <b>전날부터</b> 다시 센다.
 *
 * <table border="1">
 *   <caption>판정</caption>
 *   <tr><th>상황</th><th>결과</th></tr>
 *   <tr><td>오늘 ✅ · 어제 ✅ · 그제 ❌</td><td>{@code 2}</td></tr>
 *   <tr><td>오늘 ❌ · 어제 ✅ · 그제 ✅</td><td>{@code 2} — 끊기지 않는다</td></tr>
 *   <tr><td>오늘 ❌ · 어제 ❌</td><td>{@code 0}</td></tr>
 *   <tr><td>오늘 첫 검증</td><td>{@code 1}</td></tr>
 * </table>
 *
 * <p><b>컬럼을 만들지 않는다.</b> {@code base_date} 연속성으로 계산된다(erd.md §2).
 *
 * <p><b>별도 컴포넌트로 뺀 이유는 HOME-09와 MY-01이 같은 숫자를 써야 하기 때문이다</b>(§4.2).
 * 각자 계산하면 두 화면이 어긋나고, 어긋나도 값 범위는 정상이라 알아채기 어렵다.
 * <b>MY-01을 만들 때 이 컴포넌트를 호출한다 — 계산을 다시 적지 말 것.</b>
 */
@Component
public class VerificationStreakCalculator {

    /**
     * @param baseDate     앱이 알려준 "오늘". <b>서버 시각으로 대신하지 않는다</b> — 타임존을
     *                     저장하지 않아 한국 시간 오전 9시 이전에 연속이 하루 밀린다
     * @param verifiedDesc 검증한 날짜들, <b>최신순</b>. 기준일보다 미래는 들어오지 않는다
     */
    public int calculate(LocalDate baseDate, List<LocalDate> verifiedDesc) {
        if (verifiedDesc.isEmpty()) {
            return 0;
        }

        LocalDate latest = verifiedDesc.getFirst();
        // 오늘도 어제도 아니면 이미 끊긴 것이다. 그제까지 아무리 이어져 있어도 "연속 중"이 아니다
        if (latest.isBefore(baseDate.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        LocalDate expected = latest;
        for (LocalDate verified : verifiedDesc) {
            if (!verified.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

}
