package com.allday.sleep2skin_be.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 확정값 검증 (PRD §10.9). <b>DB도 스프링 컨텍스트도 없이 돈다</b> — 레벨 테이블을 만들지 않은
 * 이유가 이것이다(erd.md §3.10).
 *
 * <p>여기 적힌 숫자는 제품 판단이라 밸런스를 보고 조정될 수 있다. <b>바뀌면 이 테스트가 먼저
 * 깨져야 한다</b> — 값이 조용히 달라지면 화면에서만 드러난다.
 */
class LevelPolicyTest {

    @Nested
    @DisplayName("레벨 컷오프")
    class 레벨_컷오프 {

        /** "100 / 150 / 200 / 250씩"은 단계별 요구량이고 컷오프는 그 누적이다. */
        @ParameterizedTest(name = "exp {0} → 레벨 {1}")
        @CsvSource({
                "0, 1", "99, 1",
                "100, 2", "249, 2",
                "250, 3", "449, 3",
                "450, 4", "699, 4",
                "700, 5"
        })
        @DisplayName("경계값에서 레벨이 정확히 갈린다")
        void 컷오프_경계가_정확하다(int exp, int expected) {
            assertThat(LevelPolicy.levelOf(exp)).isEqualTo(expected);
        }

        /**
         * <b>만렙에 도달해도 적립은 멈추지 않는다.</b> 멈추면 나중에 6레벨을 늘렸을 때 그 기간의
         * 활동이 사라지고, 모든 적립 지점에 상한 검사가 붙는다.
         */
        @Test
        @DisplayName("만렙을 넘어서도 레벨은 5에서 멈추고 exp는 계속 유효하다")
        void 만렙_이후에도_5다() {
            assertThat(LevelPolicy.levelOf(9_999)).isEqualTo(LevelPolicy.MAX_LEVEL);
            assertThat(LevelPolicy.nextLevelExp(9_999)).isNull();
        }

        /** 컷오프 <b>절대값</b>이다 — "남은 exp"는 앱이 뺀다(api.md §1). */
        @ParameterizedTest(name = "exp {0} → 다음 컷오프 {1}")
        @CsvSource({"0, 100", "99, 100", "100, 250", "249, 250", "250, 450", "450, 700", "699, 700"})
        @DisplayName("nextLevelExp는 남은 양이 아니라 다음 컷오프 절대값이다")
        void 다음_컷오프는_절대값이다(int exp, int expected) {
            assertThat(LevelPolicy.nextLevelExp(exp)).isEqualTo(expected);
        }

        @Test
        @DisplayName("만렙이면 nextLevelExp가 null이다")
        void 만렙은_null이다() {
            assertThat(LevelPolicy.nextLevelExp(700)).isNull();
        }
    }

    @Nested
    @DisplayName("연속 검증 보상")
    class 연속_검증_보상 {

        /**
         * <b>보상 구간이 2일부터다.</b> 1일차에 주면 매일 검증을 끊었다 이어도 보상이 나간다.
         */
        @ParameterizedTest(name = "연속 {0}일 → +{1}")
        @CsvSource({"0, 0", "1, 0", "2, 5", "3, 10", "4, 15", "5, 25"})
        @DisplayName("1일차는 0이고 2일부터 구간값을 준다")
        void 구간값을_준다(int streak, int expected) {
            assertThat(LevelPolicy.verificationStreakExp(streak)).isEqualTo(expected);
        }

        /** 5일 이상은 상한 구간이라 매일 같은 값이다 — 연속이 길수록 계속 커지지 않는다. */
        @Test
        @DisplayName("5일 이상은 상한 구간이라 매일 +25다")
        void 상한_구간은_매일_25다() {
            assertThat(LevelPolicy.verificationStreakExp(5)).isEqualTo(25);
            assertThat(LevelPolicy.verificationStreakExp(30)).isEqualTo(25);
            assertThat(LevelPolicy.verificationStreakExp(365)).isEqualTo(25);
        }

        @Test
        @DisplayName("음수 연속 일수는 프로그래밍 오류다")
        void 음수는_거부한다() {
            assertThatThrownBy(() -> LevelPolicy.verificationStreakExp(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("수면 점수 증가 보상")
    class 수면_점수_증가_보상 {

        @Test
        @DisplayName("증가폭의 2배를 준다")
        void 증가폭의_두_배다() {
            assertThat(LevelPolicy.sleepScoreImprovedExp(78, 65)).isEqualTo(26);
        }

        /**
         * <b>이 테스트가 붙들고 있는 자리다.</b> 전날이 없는 것을 0점으로 치면 신규 사용자의
         * 첫날이 90점을 받았을 때 {@code +180}을 받는다.
         */
        @Test
        @DisplayName("전날 점수가 없으면 지급하지 않는다 — 0에서 오른 것이 아니다")
        void 전날이_없으면_주지_않는다() {
            assertThat(LevelPolicy.sleepScoreImprovedExp(90, null)).isZero();
        }

        @Test
        @DisplayName("오늘 점수가 없으면 지급하지 않는다")
        void 오늘이_없으면_주지_않는다() {
            assertThat(LevelPolicy.sleepScoreImprovedExp(null, 65)).isZero();
        }

        @Test
        @DisplayName("같거나 내려간 날은 0이다")
        void 오르지_않으면_0이다() {
            assertThat(LevelPolicy.sleepScoreImprovedExp(65, 65)).isZero();
            assertThat(LevelPolicy.sleepScoreImprovedExp(50, 65)).isZero();
        }

        /**
         * <b>상한을 두지 않는 것이 확정 사항이다.</b> 큰 증가는 수면이 실제로 크게 개선된 날이고
         * 하루 1회라 반복 적립되지 않는다. §10.8의 분모 변동 때문에 실제 변화보다 크게 잡힐 수
         * 있다는 것은 알고 감수한다.
         */
        @Test
        @DisplayName("증가폭에 상한이 없다 — 20점에서 90점이면 +140이다")
        void 상한이_없다() {
            assertThat(LevelPolicy.sleepScoreImprovedExp(90, 20)).isEqualTo(140);
        }
    }

    /**
     * <b>TODO 상수도 여기 있다.</b> 적립 트리거가 네 도메인에 흩어져 있어 어느 한 도메인의 정책
     * 클래스에 두면 나머지 셋이 그것을 참조하게 된다(§10.9).
     *
     * <p>{@code TodoService}가 이 상수를 직접 쓴다(2026-08-15). 한때 거기 {@code 10}이 따로
     * 있었고 <b>어긋난 동안 실제 지급은 {@code 10}이었다</b> — 이 단언만으로는 잡히지 않았다.
     * 지급값을 지키는 것은 {@code TodoServiceTest} 쪽이다.
     */
    @Test
    @DisplayName("적립량 확정값이 §10.9와 같다")
    void 적립량이_확정값과_같다() {
        assertThat(LevelPolicy.ATTENDANCE_EXP).isEqualTo(10);
        assertThat(LevelPolicy.SLEEP_SCORE_HIGH_EXP).isEqualTo(10);
        assertThat(LevelPolicy.SLEEP_SCORE_HIGH_THRESHOLD).isEqualTo(90);
        assertThat(LevelPolicy.SLEEP_SCORE_IMPROVED_MULTIPLIER).isEqualTo(2);
        assertThat(LevelPolicy.TODO_DONE_EXP).isEqualTo(5);
        assertThat(LevelPolicy.TODO_ALL_DONE_EXP).isEqualTo(30);
    }

}
