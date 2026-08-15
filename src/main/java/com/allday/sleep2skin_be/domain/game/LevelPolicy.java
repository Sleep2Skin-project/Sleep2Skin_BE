package com.allday.sleep2skin_be.domain.game;

/**
 * 레벨 컷오프 · 적립량 · 연속 보상 구간 (HOME-04 — 확정값 PRD §10.9).
 *
 * <h2>왜 {@code domain/game}에 모으나</h2>
 *
 * <p>적립 트리거가 {@code user}·{@code sleep}·{@code skin}·{@code todo} <b>네 도메인에 흩어져
 * 있다.</b> 어느 한 도메인의 정책 클래스에 두면 나머지 셋이 그것을 참조하게 되고, {@code user}에
 * 두면(거기 {@code users.exp}가 있으니 자연스러워 보이지만) 온보딩·동의와 아무 관계 없는
 * 레벨 컷오프·수면 점수 임계값까지 {@code user}가 갖게 된다(architecture.md §2).
 *
 * <p><b>{@code domain/skin/ScoringPolicy}도 {@code TodoScoringPolicy}도 아니다.</b> 예보
 * 스코어링·추천 정렬·보상 적립은 서로 다른 축이고, 섞으면 스코어링 단위 테스트가 게임 규칙
 * 변경에 흔들린다.
 *
 * <h2>§10의 다른 값들과 성격이 다르다</h2>
 *
 * <p>등급 컷오프(§10.1)·정규화 기준(§10.5)은 문헌을 근거로 잡았지만 <b>이 클래스의 숫자는 제품
 * 판단이다.</b> 근거 문헌이 없고 밸런스를 보고 조정될 수 있다 — 그래서 한 곳에 모으는 것이
 * 임시값 규칙(§9.3)과 같은 이유로 중요하다.
 *
 * <p><b>DB를 보지 않는 순수 상수·함수다.</b> 레벨 테이블·캐릭터 테이블을 만들지 않은 이유가
 * 이것이다 — 컷오프는 경계값 4개뿐이고, 테이블로 빼면 단위 테스트가 DB를 띄워야 한다(erd.md §3.10).
 */
public final class LevelPolicy {

    private LevelPolicy() {
    }

    // ===== 레벨 (확정값 PRD §10.9) =====

    /**
     * 레벨별 진입 누적 exp. 인덱스 {@code i}가 레벨 {@code i + 1}의 하한이다.
     *
     * <pre>
     * 1: 0~99   2: 100~249   3: 250~449   4: 450~699   5: 700~
     * </pre>
     *
     * <p><b>"100 / 150 / 200 / 250씩"은 단계별 요구량이고 여기 적힌 것은 그 누적이다.</b>
     * 레벨 업마다 캐릭터가 바뀐다.
     */
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 250, 450, 700};

    /** 만렙. <b>도달해도 적립은 멈추지 않는다</b> — 아래 {@link #levelOf} 주석 참조. */
    public static final int MAX_LEVEL = LEVEL_THRESHOLDS.length;

    // ===== 적립량 (확정값 PRD §10.9) =====

    /** 출석 체크인. 하루 1회 고정값이다. */
    public static final int ATTENDANCE_EXP = 10;

    /** 수면 점수 90점 이상 보상. <b>증가 여부와 무관하다</b> — 95점을 유지하는 사용자도 받는다. */
    public static final int SLEEP_SCORE_HIGH_EXP = 10;

    /** 90점 보상의 임계값. */
    public static final int SLEEP_SCORE_HIGH_THRESHOLD = 90;

    /** 수면 점수 증가 보상의 배율 — {@code (오늘 − 어제) × 2}. */
    public static final int SLEEP_SCORE_IMPROVED_MULTIPLIER = 2;

    /**
     * {@code DO} 항목 하나 완료 (되돌리면 {@code −5}).
     *
     * <p>{@code TodoService}가 이 상수를 쓴다 — 한때 거기 {@code EXP_PER_DONE = 10}이 따로
     * 있었고(HOME-04 확정 전의 값), <b>둘이 어긋난 동안 실제 지급은 {@code 10}이었다.</b>
     * 값 범위가 정상이라 아무 제약에도 걸리지 않았고 erd.md §3.10의 검산식으로만 드러났다.
     * <b>도메인 쪽에 적립량 상수를 다시 만들지 말 것.</b>
     */
    public static final int TODO_DONE_EXP = 5;

    /**
     * 그날 {@code DO} 전부 완료 보너스 (하나라도 풀리면 {@code −30}).
     *
     * <p><b>{@code DO}가 0개인 날은 달성이 아니다</b> — 아무것도 하지 않고 받는 경로가 생긴다.
     * {@code AVOID}는 체크 대상이 아니라 판정에서 제외된다.
     *
     * <p>⚠️ <b>회수({@code −30})가 빠지면 무한 적립이 된다.</b> 개별 완료와 정확히 같은 형태이며,
     * 마지막 항목 하나를 껐다 켜는 것만으로 계속 붙는다.
     */
    public static final int TODO_ALL_DONE_EXP = 30;

    /**
     * 연속 검증 보상 구간. 인덱스가 연속 일수이며 <b>1일차는 {@code 0}이다</b> — 보상 구간이
     * 2일부터 시작한다. 5일 이상은 상한 구간이라 매일 마지막 값을 준다.
     */
    private static final int[] VERIFICATION_STREAK_EXP = {0, 0, 5, 10, 15, 25};

    // ===== 계산 =====

    /**
     * 누적 exp로 레벨을 구한다. <b>{@code users.level} 컬럼은 없다</b> — 두 컬럼을 두면 이중
     * 상태가 되고 컷오프를 바꿀 때 전 행을 다시 계산해야 한다(erd.md §3.1).
     *
     * <p><b>만렙에 도달해도 {@code exp}는 계속 오르고 화면만 만렙으로 표시한다.</b> 적립을 멈추면
     * 나중에 6레벨을 늘렸을 때 그 기간의 활동이 사라지고, 모든 적립 지점에 상한 검사가 붙는다.
     */
    public static int levelOf(int exp) {
        int level = 1;
        for (int i = 1; i < LEVEL_THRESHOLDS.length; i++) {
            if (exp >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    /**
     * 다음 레벨의 컷오프 <b>절대값</b>. 만렙이면 {@code null}이다.
     *
     * <p><b>"다음 레벨까지 남은 exp"를 서버가 빼서 주지 않는다</b>(api.md §1). 앱이
     * {@code nextLevelExp − totalExp}로 계산한다 — 두 곳이 같은 사실을 말하면 어긋날 자리가 생긴다.
     */
    public static Integer nextLevelExp(int exp) {
        int level = levelOf(exp);
        return level >= MAX_LEVEL ? null : LEVEL_THRESHOLDS[level];
    }

    /**
     * 연속 검증 보상액.
     *
     * <p><b>1일차(첫 검증 또는 연속이 끊긴 뒤 첫 검증)는 {@code 0}이다.</b> 5일 이상은 상한
     * 구간이라 매일 {@code +25}이며, 연속이 끊기면 다시 1일차부터다.
     *
     * @param streakCount <b>이번 검증을 포함한</b> 연속 일수. 검증 전 값을 쓰면 화면 문구와
     *                    지급액이 하루씩 어긋난다
     */
    public static int verificationStreakExp(int streakCount) {
        if (streakCount < 0) {
            throw new IllegalArgumentException("연속 일수는 음수가 될 수 없습니다: " + streakCount);
        }
        int index = Math.min(streakCount, VERIFICATION_STREAK_EXP.length - 1);
        return VERIFICATION_STREAK_EXP[index];
    }

    /**
     * 수면 점수 증가 보상액. 오르지 않았거나 비교할 수 없으면 {@code 0}이다.
     *
     * <p><b>전날 점수가 없으면 지급하지 않는다.</b> 비교 대상이 없는 것이지 0점에서 오른 것이
     * 아니다 — 0에서 올랐다고 치면 <b>신규 사용자의 첫날이 {@code +180}을 받는다.</b>
     *
     * <p><b>상한을 두지 않는다.</b> 전날 20점 → 오늘 90점이면 {@code +140}이다. 큰 증가는 수면이
     * 실제로 크게 개선된 날이고, 하루 1회라 반복 적립되지 않는다. §10.8의 분모 변동 때문에 실제
     * 수면 변화보다 증감이 크게 잡힐 수 있다는 것은 <b>알고 감수하는 확정 사항이다.</b>
     *
     * @param today     오늘 수면 점수. 참여 피처가 0개인 날은 {@code null}
     * @param yesterday 전날 수면 점수. 세션이 없거나 참여 피처가 0개면 {@code null}
     */
    public static int sleepScoreImprovedExp(Integer today, Integer yesterday) {
        if (today == null || yesterday == null || today <= yesterday) {
            return 0;
        }
        return (today - yesterday) * SLEEP_SCORE_IMPROVED_MULTIPLIER;
    }

}
