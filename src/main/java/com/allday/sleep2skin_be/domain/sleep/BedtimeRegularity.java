package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 취침 규칙성 — 최근 {@code sleep_onset_time}의 표준편차(분). {@code COMPLEXION}의 피처다(§10.3).
 *
 * <p>세션 한 행에 담을 수 없는 값이라 {@code sleep_session}에 컬럼을 두지 않고 매번 계산한다.
 *
 * <h2>자정을 넘나드는 취침 시각</h2>
 *
 * <p><b>시각을 그대로 분으로 환산하면 이 피처가 통째로 망가진다.</b> 23:50과 00:10은 20분 차이인데
 * 하루 중 분으로 보면 {@code 1430}과 {@code 10}이라 <b>1420분 차이</b>가 된다. 자정 전후로 자는
 * 사람 — 즉 대부분의 사용자 — 은 매우 규칙적이어도 표준편차가 수백 분으로 나오고, 곡선상
 * {@code 120분 이상 = 0점}이라 <b>혈색 예보가 늘 최악으로 눌린다.</b>
 *
 * <p>값 범위는 정상이고 어떤 제약에도 걸리지 않는다. 그래서 기준 시각 하나를 잡고 나머지를
 * ±12시간 안으로 접어 계산한다 — 원형(circular) 데이터로 다루는 것이다.
 *
 * <h2>타임존</h2>
 *
 * <p><b>모든 시각을 UTC로 맞춘 뒤 비교한다.</b> 이번 밤의 값은 요청의 오프셋(예: {@code +09:00})을
 * 그대로 달고 오지만 지난 날들은 DB에서 UTC로 올라온다 — 섞으면 9시간짜리 가짜 편차가 생긴다.
 * 사용자가 한 타임존에 머무는 한 <b>일정한 오프셋은 표준편차를 바꾸지 않으므로</b> 어느 쪽으로
 * 맞추든 결과는 같고, 위의 접기 덕분에 UTC의 자정 위치도 문제가 되지 않는다.
 */
public final class BedtimeRegularity {

    private static final int MINUTES_PER_DAY = (int) Duration.ofDays(1).toMinutes();
    private static final int HALF_DAY_MINUTES = MINUTES_PER_DAY / 2;

    private BedtimeRegularity() {
    }

    /**
     * 표준편차(분). <b>기록이 {@link ScoringPolicy#BEDTIME_REGULARITY_MIN_DAYS}일 미만이면
     * {@code null}</b>이며, 스코어링은 그 항을 빼고 재정규화한다.
     *
     * <p>모표준편차({@code ÷n})다 — 모집단을 추정하는 게 아니라 관측한 7일을 그대로 기술하는
     * 값이기 때문이다. 표본표준편차({@code ÷(n−1)})를 쓰면 3일치에서 22% 부풀려진다.
     */
    public static Double standardDeviationMinutes(List<OffsetDateTime> sleepOnsetTimes) {
        if (sleepOnsetTimes.size() < ScoringPolicy.BEDTIME_REGULARITY_MIN_DAYS) {
            return null;
        }

        int base = minutesOfDayInUtc(sleepOnsetTimes.getFirst());
        double[] offsets = sleepOnsetTimes.stream()
                .mapToDouble(onset -> foldToHalfDay(minutesOfDayInUtc(onset) - base))
                .toArray();

        double mean = java.util.Arrays.stream(offsets).average().orElseThrow();
        double variance = java.util.Arrays.stream(offsets)
                .map(offset -> Math.pow(offset - mean, 2))
                .average().orElseThrow();
        return Math.sqrt(variance);
    }

    private static int minutesOfDayInUtc(OffsetDateTime onset) {
        return onset.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toSecondOfDay() / 60;
    }

    /**
     * 기준 시각과의 차이를 {@code −720 ~ +719}분으로 접는다.
     *
     * <p>{@code +1400분}은 실제로는 <b>40분 이른</b> 것이다 — 하루가 순환하므로 반대편으로 도는
     * 쪽이 더 가깝다.
     */
    private static double foldToHalfDay(int difference) {
        return Math.floorMod(difference + HALF_DAY_MINUTES, MINUTES_PER_DAY) - HALF_DAY_MINUTES;
    }

}
