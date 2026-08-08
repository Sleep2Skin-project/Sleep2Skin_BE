package com.allday.sleep2skin_be.domain.sleep.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 수면 단계 구간. 앱이 보낸 구간을 그대로 보관하며 <b>타임라인 렌더링(REP-03) 전용</b>이다.
 *
 * <p>집계는 {@link SleepSession}이 이미 들고 있으므로 {@code duration_minutes}를 두지 않았다.
 * {@code end_time - start_time}으로 즉시 나온다.
 *
 * <p>타임스탬프 컬럼도 없다. 세션에 종속된 상세 데이터고 세션이 갱신되면 <b>전량 삭제 후
 * 재삽입</b>되므로 개별 행의 생성 시각은 의미가 없다.
 *
 * <p>{@code user_id}를 넣지 않았다 — {@link SleepSession}을 거쳐 가면 된다. 자식 테이블에
 * 부모의 부모 FK를 중복해 두면 둘이 어긋날 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "sleep_stage_segment",
        indexes = @Index(name = "idx_sleep_stage_segment_session_start", columnList = "sleep_session_id, start_time")
)
public class SleepStageSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sleep_session_id", nullable = false)
    private SleepSession sleepSession;

    /**
     * {@code UNSPECIFIED}도 그대로 저장한다 — 버리면 미상 구간의 위치를 복원할 수 없다.
     *
     * <p>{@code @JdbcTypeCode(VARCHAR)}가 붙은 이유는 Hibernate 6.2+가 {@code EnumType.STRING}을
     * MySQL 네이티브 {@code ENUM} 컬럼으로 만들기 때문이다. erd.md는 {@code VARCHAR(20)}으로
     * 명시돼 있고, 네이티브 {@code ENUM}이면 값이 하나 늘 때마다 {@code ALTER TABLE}이 필요해진다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SleepStage stage;

    @Column(nullable = false)
    private OffsetDateTime startTime;

    @Column(nullable = false)
    private OffsetDateTime endTime;

    @Builder
    private SleepStageSegment(SleepSession sleepSession, SleepStage stage,
                              OffsetDateTime startTime, OffsetDateTime endTime) {
        this.sleepSession = sleepSession;
        this.stage = stage;
        this.startTime = startTime;
        this.endTime = endTime;
    }

}
