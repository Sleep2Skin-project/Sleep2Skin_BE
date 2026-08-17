package com.allday.sleep2skin_be.domain.skin.entity;

import com.allday.sleep2skin_be.global.entity.BaseCreatedEntity;
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
import java.time.OffsetDateTime;

/**
 * 셀피 실측 (HOME-06). LLM Vision이 산출한 지표 3종이며 하루 1건이다.
 *
 * <p>{@link SkinForecast}와 컬럼이 거의 같은 것은 <b>의도한 것이다</b> — 같은 세트여야
 * HOME-07 대조가 성립한다. <b>점수 방향도 예보와 같다</b>(셋 다 높을수록 좋음).
 *
 * <p><b>⚠️ 이미지 경로·URL 컬럼을 두지 않는다 — 앞으로도.</b> 셀피 원본을 보관하지 않는다는
 * 정책을 구조로 증명하는 가장 강한 장치다. 이미지는 멀티파트 → 메모리 → LLM으로 흐르며
 * 스토리지를 거치지 않으므로 <b>애초에 컬럼에 넣을 값이 존재하지 않는다.</b>
 * "디버깅용으로 잠깐만"이라는 요청이 와도 추가하지 않는다 — 컴파일러도 테스트도 막아주지 않아
 * 코드 리뷰가 유일한 방어선이다.
 *
 * <p><b>분석 성공/실패 상태 컬럼도 없다.</b> 실패하면 애초에 행이 안 생긴다 —
 * {@code SELFIE_ANALYSIS_FAILED} 에러로 끝나고 앱이 재시도한다. 항상 {@code SUCCESS}인 컬럼은
 * 의미가 없다.
 *
 * <p><b>{@code pigmentationDetected}·{@code acneScarDetected}·{@code agingDetected}는 예보
 * 지표 3종({@code darkCircle}·{@code complexion}·{@code barrier})과 성격이 다르다.</b> 매 실측마다
 * LLM Vision이 함께 판정하는 <b>클리닉 트리아지 전용 boolean 플래그</b>다 — 0~100 점수화하지
 * 않고(감지 여부만), {@code SkinForecast}에 대응하는 예보 값이 없어 HOME-07 대조·HOME-08 개인
 * 가중치 학습 어느 쪽에도 관여하지 않는다. 종합 리포트(REP-10)가 "baseDate 이하 가장 최근 실측"
 * 1건에서 그대로 읽어 클리닉 필요 여부를 보여줄 뿐이다.
 *
 * <p><b>세 필드는 {@code boolean}이 아니라 {@code Boolean}(nullable)이다.</b> 이 컬럼이 생기기
 * 전에 만들어진 행과의 구분을 위해서다 — {@code NULL}은 이 기능 도입 이전 데이터(미측정),
 * {@code false}는 실제 미검출이다. {@code boolean}으로 두면 {@code ddl-auto: update}가 컬럼을
 * 추가할 때 기존 행에 {@code NOT NULL} 기본값(0)을 채워 넣어 "미측정"과 "미검출"이 DB상 구분되지
 * 않는다.
 *
 * <p>{@code updated_at}이 없다({@link BaseCreatedEntity} 상속). 실측값은 갱신되지 않는다 —
 * 셀피를 다시 찍는 건 다시 분석하는 것이고 하루 1회 제약에 걸린다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "skin_measurement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skin_measurement_user_base_date",
                columnNames = {"user_id", "base_date"}
        )
)
@Check(
        name = "ck_skin_measurement_score_range",
        constraints = "dark_circle BETWEEN 0 AND 100"
                + " AND complexion BETWEEN 0 AND 100"
                + " AND barrier BETWEEN 0 AND 100"
)
public class SkinMeasurement extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 대조할 예보와 같은 기준일. 앱이 {@code baseDate} 쿼리 파라미터로 알려준다. */
    @Column(nullable = false)
    private LocalDate baseDate;

    /** 다크서클 회복 — 높을수록 맑음. 예보와 같은 방향이어야 한다. */
    @Column(nullable = false)
    private int darkCircle;

    /** 혈색 — 높을수록 생기 있음. */
    @Column(nullable = false)
    private int complexion;

    /** 장벽 — 높을수록 튼튼함. */
    @Column(nullable = false)
    private int barrier;

    /** 색소침착 감지 여부 — 클리닉 트리아지 전용. 심각도 점수 없음. null이면 이 컬럼 도입 이전 데이터. */
    @Column(nullable = true)
    private Boolean pigmentationDetected;

    /** 여드름 흉터 감지 여부 — 클리닉 트리아지 전용. 심각도 점수 없음. null이면 이 컬럼 도입 이전 데이터. */
    @Column(nullable = true)
    private Boolean acneScarDetected;

    /** 구조적 노화 징후 감지 여부 — 클리닉 트리아지 전용. 심각도 점수 없음. null이면 이 컬럼 도입 이전 데이터. */
    @Column(nullable = true)
    private Boolean agingDetected;

    /**
     * 분석 완료 시각. {@code created_at}과 따로 두는 이유는 <b>LLM 호출이 최대 30초 걸려
     * 실제 시차가 있기</b> 때문이다. 응답 지연 측정에 쓴다.
     */
    @Column(nullable = false)
    private OffsetDateTime analyzedAt;

    @Builder
    private SkinMeasurement(Long userId, LocalDate baseDate, int darkCircle, int complexion,
                            int barrier, Boolean pigmentationDetected, Boolean acneScarDetected,
                            Boolean agingDetected, OffsetDateTime analyzedAt) {
        this.userId = userId;
        this.baseDate = baseDate;
        this.darkCircle = darkCircle;
        this.complexion = complexion;
        this.barrier = barrier;
        this.pigmentationDetected = pigmentationDetected;
        this.acneScarDetected = acneScarDetected;
        this.agingDetected = agingDetected;
        this.analyzedAt = analyzedAt;
    }

}
