package com.allday.sleep2skin_be.domain.skin.entity;

import com.allday.sleep2skin_be.global.entity.BaseTimeEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 개인 가중치 (HOME-08). <b>사용자 한 명당 7행</b>이며 prd.md §10.3이 인정한 쌍만 행이 된다
 * (전체 조합 7×3=21이 아니다).
 *
 * <p><b>행은 첫 검증 때 7행을 한꺼번에 만든다.</b> 전부 {@code 1.0000}으로 만든 뒤 그날 참여한
 * 피처에만 보정값을 적용한다. 검증하지 않은 사용자는 행이 0개이고 예보 산출 시
 * {@code ScoringPolicy}의 일반 가중치를 쓴다 — <b>행의 존재 자체가 "개인화가 시작됐다"는 뜻</b>이
 * 되어 REP-12의 판단이 단순해진다.
 *
 * <p><b>일반(기본) 가중치 테이블을 만들지 않는다.</b> 일반 가중치는 매핑과 함께
 * {@code ScoringPolicy}(코드)에 둔다 — 한 파일에서 같이 바뀌어야 둘이 어긋나지 않는다.
 *
 * <p><b>누적 검증 횟수·피처별 학습 횟수·사용자별 절편(bias) 컬럼이 없다.</b> 앞의 둘은 파생값이고,
 * 절편은 계통 편차(늘 N점씩 높음) 보정용인데 넣지 않기로 하고 한계로 문서화했다(prd.md §10.7).
 * <b>이 테이블에 컬럼을 추가하지 말 것.</b>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "personal_weight",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_weight_user_feature_metric",
                columnNames = {"user_id", "sleep_feature", "skin_metric"}
        )
)
public class PersonalWeight extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /**
     * {@code @JdbcTypeCode(VARCHAR)}가 없으면 Hibernate 6.2+가 MySQL 네이티브 {@code ENUM} 컬럼을
     * 만든다. 그러면 <b>피처가 하나 늘 때마다 {@code ALTER TABLE}이 필요해져</b>, 매핑을 컬럼이
     * 아니라 행으로 편 이유(erd.md §2 원칙 ②)가 무의미해진다 — 실제로 2026-08-06에 5쌍 → 7쌍이
     * 됐을 때 스키마는 그대로였다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private SleepFeature sleepFeature;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SkinMetric skinMetric;

    /**
     * 일반 가중치에 곱하는 배수. 곱한 뒤 <b>지표 내 합이 1이 되도록 재정규화</b>되므로
     * 절댓값 자체에는 의미가 없고 <b>같은 지표의 다른 행과의 비율만이 의미를 갖는다.</b>
     * REP-12의 "1.7배 민감"도 그 비율을 읽은 것이다.
     *
     * <p>범위는 {@code 0.5000} ~ {@code 2.0000}으로 클램프된다(prd.md §10.7).
     * 범위를 벗어난 값이 들어 있다면 학습 코드의 클램프가 빠진 것이다.
     */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal weight;

    @Builder
    private PersonalWeight(Long userId, SleepFeature sleepFeature, SkinMetric skinMetric, BigDecimal weight) {
        this.userId = userId;
        this.sleepFeature = sleepFeature;
        this.skinMetric = skinMetric;
        this.weight = weight;
    }

    /**
     * 학습 반영 (prd.md §10.7).
     *
     * <p>클램프는 호출하는 쪽({@code ScoringPolicy})의 책임이다 — 범위가 조정될 수 있는
     * 정책값이라 엔티티에 굳히지 않는다.
     *
     * <p><b>그날 스코어링에 참여한 피처만 호출한다.</b> 결측 밤의 오차를 {@code HRV} 탓으로
     * 돌리면 존재하지 않은 값이 학습에 반영된다.
     */
    public void updateWeight(BigDecimal weight) {
        this.weight = weight;
    }

}
