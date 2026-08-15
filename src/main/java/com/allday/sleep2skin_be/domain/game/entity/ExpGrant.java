package com.allday.sleep2skin_be.domain.game.entity;

import com.allday.sleep2skin_be.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 하루 1회 보상 지급 이력 (erd.md §3.10).
 *
 * <h2>유니크 {@code (user_id, base_date, reason)}이 이 테이블의 존재 이유다</h2>
 *
 * <p>하루 1회를 애플리케이션 코드가 아니라 <b>DB가 보장한다.</b> 앱이 시작할 때마다 호출하는
 * API가 둘({@code POST /users/me/attendance} · {@code POST /sleep/sessions})이라 동시 요청이
 * 실제로 발생한다 — 검사만 두면 스플래시와 홈에서 각각 호출할 때 조용히 뚫린다.
 *
 * <p><b>{@code ATTENDANCE}는 상태로 환원할 방법이 아예 없다.</b> "오늘 앱을 켰다"는 사실을 담은
 * 다른 행이 어디에도 없어서, 이 테이블 없이는 앱을 재실행할 때마다 {@code +10}이 붙는다 —
 * TODO exp에서 한 번 막았던 무한 적립이 다른 자리에서 되살아나는 형태다.
 *
 * <h2>4종만 들어온다</h2>
 *
 * <p>{@code TODO_DONE}·{@code TODO_ALL_DONE}은 되돌릴 수 있어 여기 오지 않는다
 * ({@link ExpReason#recordable()}). 지키는 자리는 {@code ExpService}이며 <b>타입은 막아주지
 * 않는다</b> — 컬럼이 enum 전체를 받는다.
 *
 * <h2>{@code amount}를 저장하는 이유 — 사유만으로는 복원되지 않는다</h2>
 *
 * <p>{@code ATTENDANCE}는 언제나 {@code +10}이지만 나머지 셋은 그날의 상태에 따라 값이 달라진다.
 * 연속 일수는 나중에 다시 계산할 수 있어도 <b>수면 점수 증가폭은 그렇지 않다</b> — 수면 점수는
 * 저장하지 않고(prd.md §10.8), 그 사이 세션이 갱신되면 값이 달라진다. 백필이 불가능한 것은
 * 지금 넣는다.
 *
 * <p>{@code updated_at}이 없다({@link BaseCreatedEntity} 상속). {@code consent_history}와 같은
 * <b>append-only 이력</b>이며 지급된 행은 수정도 삭제도 되지 않는다 — 사용자 전체 삭제(MY-04)만
 * 예외다.
 *
 * <p><b>{@code user_id}는 연관관계가 아니라 단순 컬럼이다</b>(architecture.md §4). 그래서 FK도
 * CASCADE도 없고, MY-04 삭제에서 {@code UserService}가 손으로 지운다(erd.md §5).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "exp_grant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exp_grant_user_base_date_reason",
                columnNames = {"user_id", "base_date", "reason"}
        )
)
public class ExpGrant extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /**
     * 클라이언트가 보낸 기준일. <b>서버 시각으로 대신하지 않는다</b> — {@code users}에
     * {@code time_zone}이 없어 한국 시간 오전 9시 이전에 하루 밀린다.
     */
    @Column(nullable = false)
    private LocalDate baseDate;

    /** {@code ORDINAL}이 아니라 {@code STRING}이다 — enum 순서가 바뀌면 옛 행의 뜻이 달라진다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpReason reason;

    /** 실제 지급량. 이 넷은 회수되지 않으므로 언제나 양수다. */
    @Column(nullable = false)
    private int amount;

    @Builder
    private ExpGrant(Long userId, LocalDate baseDate, ExpReason reason, int amount) {
        this.userId = userId;
        this.baseDate = baseDate;
        this.reason = reason;
        this.amount = amount;
    }

}
