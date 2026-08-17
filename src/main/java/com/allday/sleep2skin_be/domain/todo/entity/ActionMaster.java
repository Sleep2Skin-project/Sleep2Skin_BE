package com.allday.sleep2skin_be.domain.todo.entity;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 액션 마스터 (TODO-02의 후보 원본).
 *
 * <p><b>사용자가 만드는 게 아니라 팀이 채워 넣는 콘텐츠다.</b> 시드 SQL로 관리하고 Git에 커밋한다.
 * 데모 직전에 데이터가 없어 TODO 탭이 비는 사고가 가장 흔하다. 지표 3종 × 카테고리 2종 =
 * 최소 6가지 조합에 각각 3개 이상 필요하므로 최소 18~20개를 잡는다.
 *
 * <p><b>임계값을 코드가 아니라 DB에 둔다.</b> 스코어링 파라미터는 {@code ScoringPolicy}(코드)에
 * 뒀는데 여기는 반대다 — 이건 콘텐츠이기 때문이다. 기획자가 문구와 함께 조정하고 항목이
 * 수십 개로 늘어난다. 코드에 두면 문구 하나 고치는 데 배포가 필요하다.
 *
 * <p><b>우선순위 값은 저장하지 않는다.</b> {@code impactScore × (100 − 해당 지표 점수)}로 언제든
 * 재계산되며, 계산식은 {@code ScoringPolicy}에 둔다(DB 없이 단위 테스트가 돌아야 한다).
 * 후보 추출까지만 SQL로 하고 가중·정렬·절단은 Java에서 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "action_master",
        indexes = @Index(
                name = "idx_action_master_metric_category_active",
                columnList = "target_metric, category, active"
        )
)
public class ActionMaster extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code @JdbcTypeCode(VARCHAR)}가 없으면 Hibernate 6.2+가 MySQL 네이티브 {@code ENUM} 컬럼을
     * 만든다. erd.md 명세는 {@code VARCHAR(20)}이다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ActionCategory category;

    /** 예: "수분 세럼 2회 레이어링" */
    @Column(nullable = false, length = 100)
    private String title;

    /** 예: "한 번에 두껍게보다 얇게 두 번" */
    @Column(nullable = false, length = 200)
    private String reason;

    /** 어느 지표가 나쁠 때 뜨는가. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SkinMetric targetMetric;

    /**
     * 해당 지표가 <b>이 값 이하</b>면 우선 선발.
     *
     * <p><b>탈락 조건이 아니다</b>(2026-08-18). 만족하는 후보가 절단 개수에 모자라면 미만족
     * 후보가 뒤를 채운다 — {@link com.allday.sleep2skin_be.domain.todo.TodoScoringPolicy}.
     * 걸러 내던 시절에는 컨디션이 좋은 날 목록이 4개·0개로 내려갔다.
     *
     * <p>심각도를 {@code threshold − 점수}가 아니라 {@code 100 − 점수}로 잡은 이유가 여기 있다 —
     * 그러면 임계값이 선발 조건과 우선순위를 겸하게 되어, 기획자가 "이 항목이 잘 안 떠요" 하고
     * 임계값을 올리는 순간 그 항목의 우선순위까지 조용히 올라간다.
     * <b>임계값은 어느 갈래에 설지만, 심각도는 지표 점수만 결정한다.</b>
     */
    @Column(nullable = false)
    private int threshold;

    /**
     * 기본 영향도. <b>범위는 1~10, 기본 5</b> — DB 제약이 아니라 시드 작성 규칙이다
     * (정렬식과 함께 조정될 값이라 스키마에 굳히지 않는다).
     *
     * <p>우선순위가 곱셈이라 두 항의 상대적 폭이 승부를 가른다. 심각도({@code 100 − 점수})는
     * 실제 예보가 30~80에 몰려 최대 3.5배 차이인데, 누군가 90을 넣는 순간 그 항목은 지표 상태와
     * 무관하게 항상 1위가 되어 <b>가중을 넣은 의미가 값 하나로 무력화된다.</b>
     */
    @Column(nullable = false)
    private int impactScore;

    /** 콘텐츠 온/오프 (소프트 삭제). */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private ActionMaster(ActionCategory category, String title, String reason,
                         SkinMetric targetMetric, int threshold, int impactScore, boolean active) {
        this.category = category;
        this.title = title;
        this.reason = reason;
        this.targetMetric = targetMetric;
        this.threshold = threshold;
        this.impactScore = impactScore;
        this.active = active;
    }

}
