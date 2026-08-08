package com.allday.sleep2skin_be.domain.sleep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BedtimeRegularityTest {

    @Test
    @DisplayName("기록이 3일 미만이면 계산하지 않는다 — 2점으로 낸 표준편차는 규칙성이 아니다")
    void 삼일_미만이면_결측이다() {
        assertThat(BedtimeRegularity.standardDeviationMinutes(List.of())).isNull();
        assertThat(BedtimeRegularity.standardDeviationMinutes(onsets("23:40"))).isNull();
        assertThat(BedtimeRegularity.standardDeviationMinutes(onsets("23:40", "23:50"))).isNull();
        assertThat(BedtimeRegularity.standardDeviationMinutes(onsets("23:40", "23:50", "00:00")))
                .isNotNull();
    }

    @Test
    @DisplayName("매일 같은 시각에 자면 표준편차가 0이다")
    void 같은_시각이면_0이다() {
        assertThat(BedtimeRegularity.standardDeviationMinutes(onsets("23:40", "23:40", "23:40")))
                .isCloseTo(0.0, within(1e-9));
    }

    /**
     * 이 테스트가 없으면 <b>자정 전후로 자는 대부분의 사용자에게 혈색 예보가 늘 최악으로 나간다.</b>
     * 값 범위는 정상이라 어떤 제약에도 걸리지 않는다.
     */
    @Test
    @DisplayName("자정을 넘나들어도 실제 간격으로 계산한다 — 23:50과 00:10은 1420분이 아니라 20분 차이다")
    void 자정을_넘나들어도_실제_간격으로_계산한다() {
        double crossingMidnight = BedtimeRegularity.standardDeviationMinutes(
                onsets("23:50", "00:10", "00:00"));
        double sameSpreadAtNoon = BedtimeRegularity.standardDeviationMinutes(
                onsets("11:50", "12:10", "12:00"));

        assertThat(crossingMidnight).isCloseTo(sameSpreadAtNoon, within(1e-9));
        assertThat(crossingMidnight).isLessThan(30);   // 곡선상 30분 이하 = 100점
    }

    @Test
    @DisplayName("타임존이 섞여 들어와도 같은 순간이면 결과가 같다 — 이번 밤은 요청 오프셋, 지난 밤은 UTC로 온다")
    void 오프셋_표기가_섞여도_결과가_같다() {
        double kst = BedtimeRegularity.standardDeviationMinutes(List.of(
                OffsetDateTime.parse("2026-08-06T23:50:00+09:00"),
                OffsetDateTime.parse("2026-08-05T00:10:00+09:00"),
                OffsetDateTime.parse("2026-08-04T00:00:00+09:00")));

        double mixed = BedtimeRegularity.standardDeviationMinutes(List.of(
                OffsetDateTime.parse("2026-08-06T23:50:00+09:00"),   // 이번 밤 — 요청 오프셋
                OffsetDateTime.parse("2026-08-04T15:10:00Z"),        // 지난 밤 — DB에서 UTC로
                OffsetDateTime.parse("2026-08-03T15:00:00Z")));

        assertThat(mixed).isCloseTo(kst, within(1e-9));
    }

    @Test
    @DisplayName("불규칙하게 자면 표준편차가 커진다")
    void 불규칙하면_편차가_크다() {
        double regular = BedtimeRegularity.standardDeviationMinutes(
                onsets("23:30", "23:40", "23:50", "23:45"));
        double irregular = BedtimeRegularity.standardDeviationMinutes(
                onsets("21:00", "01:30", "23:00", "03:00"));

        assertThat(irregular).isGreaterThan(regular);
    }

    @Test
    @DisplayName("모표준편차다 — 관측한 날들을 그대로 기술하지 모집단을 추정하지 않는다")
    void 모표준편차를_쓴다() {
        // 23:00 · 00:00 · 01:00 → 기준(23:00) 대비 0 · 60 · 120, 평균 60
        // 모집단: sqrt((3600+0+3600)/3) = 48.99   표본: sqrt(7200/2) = 60.0
        assertThat(BedtimeRegularity.standardDeviationMinutes(onsets("23:00", "00:00", "01:00")))
                .isCloseTo(48.9898, within(0.001));
    }

    /** 날짜는 규칙성에 영향을 주지 않으므로 시각만 지정한다. */
    private static List<OffsetDateTime> onsets(String... localTimes) {
        return java.util.Arrays.stream(localTimes)
                .map(time -> OffsetDateTime.parse("2026-08-06T" + time + ":00+09:00"))
                .toList();
    }

}
