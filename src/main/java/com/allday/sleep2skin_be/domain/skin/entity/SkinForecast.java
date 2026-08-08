package com.allday.sleep2skin_be.domain.skin.entity;

import com.allday.sleep2skin_be.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDate;

/**
 * 피부 예보 (HOME-03). 수면 데이터에서 산출한 예측값이며 하루 1건이다.
 *
 * <p><b>등급 라벨 컬럼이 없다.</b> 점수 → 컷오프 매핑으로 나오는 파생값이라 저장하면
 * 컷오프(prd.md §10.1)를 조정했을 때 과거 데이터만 옛 기준으로 남는다 — 같은 78점이
 * 어제는 "좋음" 오늘은 "보통"으로 보인다.
 *
 * <p><b>{@code sleep_session_id} FK가 없다.</b> {@code baseDate}가 곧 {@code sleepDate}라
 * {@code (userId, baseDate)}로 바로 조인된다.
 *
 * <p><b>예보 이력 테이블을 만들지 않는다.</b> "검증을 마친 날의 예보는 재산출하지 않는다"는
 * 정책 덕분이다 — 검증에 쓰인 예보는 절대 바뀌지 않으므로 이력이 필요 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "skin_forecast",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skin_forecast_user_base_date",
                columnNames = {"user_id", "base_date"}
        )
)
@Check(
        name = "ck_skin_forecast_score_range",
        constraints = "dark_circle BETWEEN 0 AND 100"
                + " AND complexion BETWEEN 0 AND 100"
                + " AND barrier BETWEEN 0 AND 100"
)
public class SkinForecast extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** {@code sleep_session.sleep_date}와 같은 값이다(둘 다 기상일 기준). */
    @Column(nullable = false)
    private LocalDate baseDate;

    /** 다크서클 회복 — 높을수록 맑음. */
    @Column(nullable = false)
    private int darkCircle;

    /** 혈색 — 높을수록 생기 있음. */
    @Column(nullable = false)
    private int complexion;

    /** 장벽 — 높을수록 튼튼함. */
    @Column(nullable = false)
    private int barrier;

    @Builder
    private SkinForecast(Long userId, LocalDate baseDate, int darkCircle, int complexion, int barrier) {
        this.userId = userId;
        this.baseDate = baseDate;
        this.darkCircle = darkCircle;
        this.complexion = complexion;
        this.barrier = barrier;
    }

    /**
     * 재산출 (수면 데이터가 갱신됐을 때).
     *
     * <p><b>그날 셀피 검증을 마쳤으면 호출하면 안 된다.</b> 이미 끝난 검증의 대조 기준이 사후에
     * 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다. 성능이 아니라 정확성 문제다.
     */
    public void updateScores(int darkCircle, int complexion, int barrier) {
        this.darkCircle = darkCircle;
        this.complexion = complexion;
        this.barrier = barrier;
    }

}
