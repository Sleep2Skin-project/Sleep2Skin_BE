package com.allday.sleep2skin_be.domain.sleep.entity;

import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationResult;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 수면 세션. <b>행 하나가 하룻밤이고, 첫 기상까지다</b>(prd.md §4.1).
 *
 * <p>연속 {@code AWAKE} 60분 이상이면 기상으로 보고 거기서 세션을 끊는다. 그 뒤에 다시 잠들어도
 * 낮잠이므로 이 행에 들어가지 않는다. <b>60분 임계값이 없으면 {@code awakeCount}가 구조적으로
 * 항상 0이 된다</b> — "첫 기상까지"를 문자 그대로 구현하면 5분짜리 각성에서도 세션이 끊겨
 * 5분 이상 각성이 세션 안에 존재할 수 없다. 값 범위는 정상이라 아무 제약에도 안 걸리고
 * 다크서클 적중률만 무너진다.
 *
 * <p><b>집계값은 전부 서버가 {@link SleepStageSegment}에서 계산한다.</b> 앱이 보고한 총합이나
 * 세션 분할을 그대로 믿지 않는다 — 서버가 첫 기상에서 자르므로 앱의 총합에는 그 뒤의 낮잠이
 * 섞여 있을 수 있고, 기기·OS별로 쪼개는 기준도 다르다. 각성 5분·기상 60분 두 임계값은
 * {@code domain/sleep}의 정규화 정책 상수이며 {@code ScoringPolicy}가 아니다.
 *
 * <p>시각 필드는 {@link OffsetDateTime}이다. {@code LocalDateTime}으로 받으면 요청의 오프셋이
 * 컨트롤러 단계에서 이미 버려져 {@code NORMALIZE_UTC} 설정이 개입할 여지가 없다(erd.md §3.1).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "sleep_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sleep_session_user_sleep_date",
                columnNames = {"user_id", "sleep_date"}
        )
)
public class SleepSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /**
     * <b>기상일 기준이다.</b> {@code 23:40 잠듦 → 07:10 기상}이면 07:10의 날짜다.
     * 이래야 {@code skin_forecast.base_date}와 같은 값이 되어 변환 없이 조인된다.
     * 취침일 기준으로 잡으면 모든 조인에 +1일 보정이 붙고 언젠가 한 군데를 빠뜨린다.
     */
    @Column(nullable = false)
    private LocalDate sleepDate;

    /**
     * 잠든 시각 — 첫 {@code asleep} 구간의 시작. {@code asleep}이 있어야 수면 세션이므로 항상 존재한다.
     *
     * <p>"누운 시각"이 아니다. 누운 시각은 HealthKit {@code inBed}에서만 나오는데 사용자가
     * 수면 스케줄을 쓰지 않으면 없는 밤이 생겨, 같은 컬럼이 밤마다 다른 것을 가리키게 된다.
     * 그러면 규칙적으로 자는 사용자도 편차가 크게 계산되어 혈색 예보만 나빠진다.
     *
     * <p>취침 규칙성(prd.md §10.3 → {@code COMPLEXION})은 이 값의 최근 7일 표준편차로 계산한다.
     * 하루치 행에 담을 수 없는 값이라 규칙성 컬럼을 따로 두지 않는다.
     */
    @Column(nullable = false)
    private OffsetDateTime sleepOnsetTime;

    /** 기상 시각. 60분 이상 {@code AWAKE}가 시작되는 시점과 마지막 {@code asleep}이 끝나는 시점 중 앞선 쪽. */
    @Column(nullable = false)
    private OffsetDateTime wakeTime;

    /**
     * 총 수면 시간 — {@code asleep} 구간의 합({@code UNSPECIFIED} 포함).
     *
     * <p>{@code deep + rem + core}로 구할 수 있어 보이지만 단계 미상 구간이 있어 셋의 합보다 클 수 있다.
     * 전제가 깨지는 순간 그것을 확인할 수 있는 유일한 수단이고, {@code total − (deep+rem+core)}가
     * 곧 미상 구간의 길이가 된다.
     *
     * <p><b>단, 스코어링의 비율 분모는 이 값이 아니라 {@code deep + rem + core}다</b>(prd.md §10.5).
     */
    @Column(nullable = false)
    private int totalSleepMinutes;

    @Column(nullable = false)
    private int deepSleepMinutes;

    @Column(nullable = false)
    private int remSleepMinutes;

    /** 얕은 수면 (HealthKit {@code asleepCore}). 저장하지만 스코어링 피처는 아니다. */
    @Column(nullable = false)
    private int coreSleepMinutes;

    /**
     * 야간 각성 횟수 — 다크서클 예측의 핵심 변수.
     *
     * <p><b>5분 이상 지속된 {@code AWAKE} 구간만</b> 1회로 센다. 임계값이 없으면 30초짜리
     * 뒤척임까지 잡혀 사람마다 20회씩 나온다. <b>마지막 {@code asleep} 이후의 {@code AWAKE}는
     * 세지 않는다</b> — 그건 야간 각성이 아니라 기상이고, 포함하면 모든 사용자가 매일 최소 1회를
     * 깔고 시작해 다크서클 예보가 전반적으로 눌린다.
     */
    @Column(nullable = false)
    private int awakeCount;

    /**
     * 각성 총 시간 — {@code awakeCount}와 <b>같은 구간들만</b> 합산한다.
     *
     * <p>횟수에만 임계값을 걸면 {@code awakeCount=0} · {@code awakeMinutes=12}처럼 REP-04 화면에서
     * 모순되는 조합이 나온다.
     *
     * <p><b>표시 전용이며 스코어링 피처가 아니다</b>(prd.md §10.3). 각성 횟수와 같은 임계값·같은
     * 구간 집합에서 나와 중복 상관이 강해 넣지 않기로 확정된 것이다 — {@code SleepFeature}에
     * 추가하지 말 것.
     */
    @Column(nullable = false)
    private int awakeMinutes;

    /**
     * 심박변이도(ms). 워치 미착용 시 {@code null}.
     *
     * <p>{@code restingHeartRate}와 함께 결측되며, 그 밤의 {@code COMPLEXION}은 피처 3개 중
     * 취침 규칙성 하나만 남는다. <b>결측 피처는 빼고 지표 내 가중치를 재정규화한다</b> —
     * 기본값을 대입하지 않는다(prd.md §10.6).
     */
    @Column(precision = 6, scale = 2)
    private BigDecimal hrv;

    /** 안정시 심박(bpm). 워치 미착용 시 {@code null}. */
    private Integer restingHeartRate;

    /**
     * 정규화된 페이로드의 SHA-256 hex.
     *
     * <p>앱은 시작될 때마다 업로드하므로 새 수면 데이터가 생기기 전까지 같은 세션이 계속 온다.
     * 해시를 비교해 <b>저장·스코어링을 시작하기 전에 중단</b>한다. upsert로 덮어쓰지 않는다.
     */
    @Column(nullable = false, columnDefinition = "char(64)")
    private String payloadHash;

    @Builder
    private SleepSession(Long userId, LocalDate sleepDate, OffsetDateTime sleepOnsetTime,
                         OffsetDateTime wakeTime, int totalSleepMinutes, int deepSleepMinutes,
                         int remSleepMinutes, int coreSleepMinutes, int awakeCount, int awakeMinutes,
                         BigDecimal hrv, Integer restingHeartRate, String payloadHash) {
        this.userId = userId;
        this.sleepDate = sleepDate;
        this.sleepOnsetTime = sleepOnsetTime;
        this.wakeTime = wakeTime;
        this.totalSleepMinutes = totalSleepMinutes;
        this.deepSleepMinutes = deepSleepMinutes;
        this.remSleepMinutes = remSleepMinutes;
        this.coreSleepMinutes = coreSleepMinutes;
        this.awakeCount = awakeCount;
        this.awakeMinutes = awakeMinutes;
        this.hrv = hrv;
        this.restingHeartRate = restingHeartRate;
        this.payloadHash = payloadHash;
    }

    /**
     * 스코어링의 비율 분모 — <b>총 수면이 아니라 단계 합이다</b>(prd.md §10.5).
     *
     * <p>총 수면에는 단계 미상 구간이 섞일 수 있는데 그걸 분모에 넣으면 <b>측정하지 못한 시간이
     * "깊은 수면이 아니었던 시간"으로 계산된다.</b> {@code 0}이면 단계가 하나도 안 잡힌 밤이며
     * {@code BARRIER}가 빈 상태가 된다.
     */
    public int stagedSleepMinutes() {
        return deepSleepMinutes + remSleepMinutes + coreSleepMinutes;
    }

    /** 워치를 차지 않고 잔 밤인가. 두 값은 같은 착용 여부에 의존해 함께 결측된다. */
    public boolean isWatchDataMissing() {
        return hrv == null && restingHeartRate == null;
    }

    /**
     * 같은 날짜에 <b>내용이 다른</b> 수면 데이터가 다시 왔을 때의 갱신.
     *
     * <p><b>그날 셀피 검증을 마쳤으면 호출하면 안 된다.</b> 예보를 재산출하게 되고, 이미 끝난
     * 검증의 대조 기준이 사후에 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다.
     *
     * <p>{@code sleepDate}와 {@code userId}는 바꾸지 않는다 — 그 둘이 이 행을 찾아낸 키다.
     */
    public void applyNormalization(SleepNormalizationResult normalized) {
        this.sleepOnsetTime = normalized.sleepOnsetTime();
        this.wakeTime = normalized.wakeTime();
        this.totalSleepMinutes = normalized.totalSleepMinutes();
        this.deepSleepMinutes = normalized.deepSleepMinutes();
        this.remSleepMinutes = normalized.remSleepMinutes();
        this.coreSleepMinutes = normalized.coreSleepMinutes();
        this.awakeCount = normalized.awakeCount();
        this.awakeMinutes = normalized.awakeMinutes();
        this.hrv = normalized.hrv();
        this.restingHeartRate = normalized.restingHeartRate();
        this.payloadHash = normalized.payloadHash();
    }

}
