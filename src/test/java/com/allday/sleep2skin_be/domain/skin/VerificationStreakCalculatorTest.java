package com.allday.sleep2skin_be.domain.skin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연속 검증 횟수 (prd.md §4.2 E5).
 *
 * <p><b>여기서 규칙이 어긋나면 HOME-09 배너와 MY-01 프로필이 동시에 틀린다.</b> 두 화면이 같은
 * 계산을 공유하기 때문이며, 값 범위는 정상이라 알아채기 어렵다.
 */
class VerificationStreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    private final VerificationStreakCalculator calculator = new VerificationStreakCalculator();

    @Test
    @DisplayName("오늘 ✅ · 어제 ✅ · 그제 ❌ → 2")
    void 오늘부터_이어지면_그대로_센다() {
        assertThat(calculate(TODAY, TODAY.minusDays(1), TODAY.minusDays(3))).isEqualTo(2);
    }

    /**
     * <b>이 규칙의 핵심이다.</b> 저녁에 검증하는 사용자가 아침에 앱을 열었을 때 어제까지 쌓은
     * 연속이 {@code 0}으로 보이면 <b>아직 하지 않은 일로 사용자를 벌주는 것</b>처럼 읽힌다.
     */
    @Test
    @DisplayName("오늘 ❌ · 어제 ✅ · 그제 ✅ → 2, 끊기지 않는다")
    void 오늘_미검증이_연속을_끊지_않는다() {
        assertThat(calculate(TODAY.minusDays(1), TODAY.minusDays(2))).isEqualTo(2);
    }

    @Test
    @DisplayName("오늘 ❌ · 어제 ❌ → 0")
    void 이틀_비면_끊긴다() {
        // 그제까지 아무리 이어져 있어도 "연속 중"이 아니다
        assertThat(calculate(TODAY.minusDays(2), TODAY.minusDays(3), TODAY.minusDays(4)))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("오늘 첫 검증 → 1")
    void 첫_검증은_1이다() {
        assertThat(calculate(TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("검증 이력이 없으면 0이다")
    void 이력이_없으면_0이다() {
        assertThat(calculator.calculate(TODAY, List.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("중간이 하루 비면 거기서 멈춘다")
    void 구멍에서_멈춘다() {
        assertThat(calculate(TODAY, TODAY.minusDays(1), TODAY.minusDays(3), TODAY.minusDays(4)))
                .isEqualTo(2);
    }

    /** 조회가 기준일 이하만 주므로 미래 날짜는 들어오지 않지만, 경계를 한 번 못 박아 둔다. */
    @Test
    @DisplayName("기준일보다 과거만 있어도 어제까지면 이어진 것으로 본다")
    void 어제까지만_있어도_이어진다() {
        assertThat(calculate(TODAY.minusDays(1))).isEqualTo(1);
    }

    private int calculate(LocalDate... verifiedDesc) {
        return calculator.calculate(TODAY, List.of(verifiedDesc));
    }

}
