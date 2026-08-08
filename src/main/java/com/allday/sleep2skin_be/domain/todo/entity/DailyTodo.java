package com.allday.sleep2skin_be.domain.todo.entity;

import com.allday.sleep2skin_be.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 일자별 TODO (TODO-01~05).
 *
 * <p><b>목록은 첫 조회 시 생성하고 고정한다.</b> {@code GET /api/v1/todo}가 그날 행이 없으면
 * 추천 엔진을 돌려 행을 만들고, 있으면 그대로 반환한다. 매 조회마다 계산하면 오전에 본 목록과
 * 오후에 본 목록이 달라지고, REP-10이 "그날 무엇이 추천됐는가"를 재현할 수 없다.
 * 덕분에 나중에 정렬식이나 {@code impactScore}를 바꿔도 과거 목록은 바뀌지 않는다.
 *
 * <p><b>자정 리셋 로직이 필요 없다.</b> {@code baseDate}가 있으면 날짜가 바뀔 때 새 행이 생기고
 * 어제 행은 그대로 남아 집계에 쓰인다. 지울 것도 초기화할 것도 없다.
 *
 * <p><b>{@code completed_at}이 없다.</b> {@code status}와 이중 상태가 되기 때문이다 —
 * {@code PENDING}인데 {@code completed_at}이 차 있는 행이 생기면 어느 쪽이 진실인지 알 수 없다.
 * 이 테이블은 {@code status} 말고 바뀌는 게 없어서 {@code updated_at}이 곧 마지막 체크 시각이다.
 * <b>⚠️ 나중에 메모처럼 수정 가능한 컬럼이 붙으면 이 대체가 깨지므로 그때 되살린다.</b>
 *
 * <p>{@code source}({@code RECOMMENDED}/{@code USER_ADDED})도 두지 않는다. 쓰는 기능인 TODO-06이
 * MVP에서 제외되어 <b>모든 행이 추천이므로 값이 하나뿐인 상수 컬럼</b>이 된다. 되살릴 때
 * {@code DEFAULT 'RECOMMENDED'}로 추가하면 기존 행의 값이 실제로 맞다(백필이 정확하다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "daily_todo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_todo_user_date_action",
                columnNames = {"user_id", "base_date", "action_master_id"}
        ),
        indexes = @Index(name = "idx_daily_todo_user_base_date", columnList = "user_id, base_date")
)
public class DailyTodo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 예보·실측과 같은 기준일. */
    @Column(nullable = false)
    private LocalDate baseDate;

    /** 마스터에서 골라 추가하는 방식이므로 항상 존재한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_master_id", nullable = false)
    private ActionMaster actionMaster;

    /**
     * {@code @JdbcTypeCode(VARCHAR)}가 없으면 Hibernate 6.2+가 MySQL 네이티브 {@code ENUM} 컬럼을
     * 만든다. erd.md 명세는 {@code VARCHAR(20)}이다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TodoStatus status;

    @Builder
    private DailyTodo(Long userId, LocalDate baseDate, ActionMaster actionMaster) {
        this.userId = userId;
        this.baseDate = baseDate;
        this.actionMaster = actionMaster;
        this.status = TodoStatus.PENDING;
    }

    /** 완료 체크와 되돌리기 (TODO-05). 상태가 2종뿐이라 한 메서드가 양방향을 처리한다. */
    public void changeStatus(TodoStatus status) {
        this.status = status;
    }

}
