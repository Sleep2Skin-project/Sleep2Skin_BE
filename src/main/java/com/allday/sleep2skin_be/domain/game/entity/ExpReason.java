package com.allday.sleep2skin_be.domain.game.entity;

/**
 * exp 적립 사유 6종 (HOME-04 — prd.md §10.9).
 *
 * <h2>여섯인데 {@code exp_grant}에 저장되는 것은 넷뿐이다</h2>
 *
 * <p>"하루 1회"를 무엇이 보장하는가가 트리거마다 다르다(erd.md §3.10).
 *
 * <table border="1">
 *   <caption>중복 적립을 막는 장치</caption>
 *   <tr><th>보장 장치</th><th>해당 사유</th></tr>
 *   <tr><td>{@code exp_grant}의 {@code (user_id, base_date, reason)} 유니크</td>
 *       <td>{@link #ATTENDANCE} · {@link #VERIFICATION_STREAK}
 *           · {@link #SLEEP_SCORE_IMPROVED} · {@link #SLEEP_SCORE_HIGH}</td></tr>
 *   <tr><td>{@code daily_todo.status} — 상태 전이가 곧 지급 여부</td>
 *       <td>{@link #TODO_DONE} · {@link #TODO_ALL_DONE}</td></tr>
 * </table>
 *
 * <p><b>TODO 둘에 이력 행을 만들지 않는 이유는 되돌릴 수 있기 때문이다.</b> 행을 만들면 회수할 때
 * 지워야 하고, 지우는 순간 그 테이블은 이력이 아니라 <b>현재 상태의 사본</b>이 된다 —
 * {@code status}가 이미 말하고 있는 것을 두 곳에서 말하게 된다.
 *
 * <p>반대로 나머지 넷은 <b>회수되지 않는다.</b> 되돌릴 사용자 행동이 없다 — 출석을 취소하거나
 * 잠을 다시 잘 수는 없다.
 *
 * <p>⚠️ <b>{@link ExpGrant}에 TODO 사유를 넣지 말 것.</b> 컴파일러가 막아주지 않는다 —
 * 컬럼 타입이 이 enum 전체를 받는다. 지키는 자리는 {@code ExpService}이며,
 * 거기서 {@link #recordable()}로 갈린다.
 */
public enum ExpReason {

    /** 앱 시작 시 출석 체크인. {@code POST /users/me/attendance} — 하루 1회 {@code +10}. */
    ATTENDANCE(true),

    /** 셀피 검증 성공. 연속 일수 구간값({@code +5}~{@code +25})이며 1일차는 지급하지 않는다. */
    VERIFICATION_STREAK(true),

    /** 수면 점수가 전날보다 올랐다. {@code (오늘 − 어제) × 2}이며 상한이 없다. */
    SLEEP_SCORE_IMPROVED(true),

    /** 수면 점수 90점 이상. 증가 여부와 무관하게 {@code +10} — 잘 자던 사람이 손해 보지 않는다. */
    SLEEP_SCORE_HIGH(true),

    /** {@code DO} 항목 하나 완료. <b>되돌리면 회수된다</b> — 이력 행을 만들지 않는다. */
    TODO_DONE(false),

    /** 그날 {@code DO} 전부 완료. <b>하나라도 풀리면 회수된다</b> — 이력 행을 만들지 않는다. */
    TODO_ALL_DONE(false);

    private final boolean recordable;

    ExpReason(boolean recordable) {
        this.recordable = recordable;
    }

    /**
     * {@code exp_grant}에 이력 행을 남기는 사유인가.
     *
     * <p><b>{@code false}면 되돌릴 수 있는 적립이다.</b> 하루 1회 판정을 이력이 아니라
     * {@code daily_todo.status}가 한다.
     */
    public boolean recordable() {
        return recordable;
    }

}
