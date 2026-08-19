# 종합 리포트(REP-09~11) 발동 조건 제거 및 추세 판정 도입

- 날짜: 2026-08-19
- 브랜치: `feature/report-overall-update`
- 관련 문서: [prd.md](../prd.md) §4.4, §7 L6·L9 (원본 md 미수정 — 이 문서가 최신 실제 동작을 반영)

## 배경

`prd.md`·`api.md` 상 REP-09~11은 "트리아지 발동 임계값 미정(§7 L6)"으로 보류 상태로 기록되어 있었으나,
`feature/report-overall` 브랜치에서 실제로는 `SleepTrend`(STABLE/RISING/FALLING/VOLATILE) 기반
발동 조건과 신규 3종 플래그(`pigmentationDetected` 등)까지 구현되어 있었음.

이번 작업은 그 구현을 다음 두 가지로 교체:
1. 발동 조건(트리거) 자체를 제거 — 조건 없이 항상 전체 내용 노출
2. "이렇게 판단했어요" 표시 로직을 수면점수 기반에서 **예보 지표 3종 자체의 3주 추세**로 교체

## 변경 범위

- **범위 한정**: REP-09의 "이렇게 판단했어요" 표시 섹션만 교체. REP-10(`clinicNeeded`,
  `pigmentationDetected` 등)의 판정 로직 자체는 변경하지 않음.
- 기존 발동 조건(`triggered`)이 있었기 때문에, 조건 제거로 REP-10도 자연히 항상 노출되도록 반영.

## 기존 → 변경

| 항목 | 기존 (제거 대상) | 변경 |
|---|---|---|
| 발동 조건 | `sleepTrend(STABLE\|RISING) && stagnantMetrics 존재` 시에만 응답 | **제거 — 항상 전체 응답** |
| 판정 대상 | 수면 점수(`SleepSession` 기반) 추세 | 예보 지표(`darkCircle`/`barrier`/`complexion`) 자체 값 |
| 판정 방식 | 3주를 반으로 나눠 평균 비교 + 표준편차로 VOLATILE 판정 | 3주를 W1/W2/W3로 나눠 W1↔W3 비교, W2로 방향 일관성만 체크 |
| 리포트 전체 부족 판정 | 수면 점수 표본 수 < `CorrelationPolicy.MIN_SAMPLE_SIZE`(5) | REP-06/08과 동일 — **가입일 기준 21일 미만** |
| 지표별 부족 판정 | 없음(수면점수 하나만 봤음) | 신규 — 지표별 W1 또는 W3 평균이 `null`이면 `INSUFFICIENT_SAMPLE` |

## 신규 판정 로직

### 대상 데이터
- 지표별(`darkCircle`/`barrier`/`complexion`)로 최근 21일치 일별 예보 점수
- W1 = baseDate−20 ~ baseDate−14 (가장 과거)
- W2 = baseDate−13 ~ baseDate−7 (중간 — 방향 일관성 체크 전용, 응답에는 미포함)
- W3 = baseDate−6 ~ baseDate (가장 최근)
- 평균 계산 시 결측일은 분모 제외, 그 주 전체 결측이면 평균 `null`

### 판정식

leg1 = W2평균 - W1평균
leg2 = W3평균 - W2평균
total = W3평균 - W1평균

W1 또는 W3 평균이 null → trend = INSUFFICIENT_SAMPLE, volatileDirection = null
leg1 > 0 && leg2 < 0 → trend = VOLATILE, volatileDirection = RISE_THEN_FALL
leg1 < 0 && leg2 > 0 → trend = VOLATILE, volatileDirection = FALL_THEN_RISE
(leg1 또는 leg2가 0이면 반대 부호로 취급하지 않음 — 방향 일관으로 처리)
(그 외, 방향 일관) total > 0 → trend = IMPROVED, volatileDirection = null
(그 외, 방향 일관) total < 0 → trend = WORSENED, volatileDirection = null
(그 외, 방향 일관) total = 0 → trend = MAINTAINED, volatileDirection = null

W2평균이 null인 경우: leg1·leg2 계산 불가 → 방향 일관성 체크(VOLATILE 판정) 생략,
total(W3-W1)만으로 IMPROVED/WORSENED/MAINTAINED 판정

### enum

```java
// domain/report/dto/MetricTrend.java
public enum MetricTrend {
    IMPROVED, WORSENED, VOLATILE, MAINTAINED, INSUFFICIENT_SAMPLE
}

// domain/report/dto/VolatileDirection.java
public enum VolatileDirection {
    RISE_THEN_FALL, FALL_THEN_RISE
}
```

`volatileDirection`은 `trend == VOLATILE`일 때만 값이 있고, 그 외에는 `null`.
서버는 판정값(enum)만 주고 화면 문구("상승했다가 감소했어요" 등)는 클라이언트가 조립 —
기존 TODO `causeLabel` 패턴과 동일.

## 리포트 전체 상태 (`status`)

기존 `SleepTrend.INSUFFICIENT_DATA`(수면 점수 표본 수 기준)를 폐기하고,
REP-06(주간)·REP-08(월간)과 동일한 **가입일 기준** 규칙으로 통일:

- 가입일 → baseDate 일수(가입 당일 1일차)가 **21일 미만** → `status: INSUFFICIENT_DATA`
- 21일 이상 → `status: FULL` (개별 지표 부족은 지표별 `INSUFFICIENT_SAMPLE`로 별도 표시,
  리포트 전체를 부족 처리하지 않음 — "데이터 품질 문제와 신규 사용자 문제를 같은 상태로
  묶지 않는다"는 기존 REP-06/08 원칙과 동일)

## 응답 예시

```jsonc
GET /api/v1/report/overall

{
  "success": true,
  "data": {
    "status": "FULL",
    "message": null,
    "trends": {
      "darkCircle": { "trend": "IMPROVED", "volatileDirection": null, "w1Average": 48, "w3Average": 79 },
      "complexion": { "trend": "VOLATILE", "volatileDirection": "RISE_THEN_FALL", "w1Average": 61, "w3Average": 58 },
      "barrier":    { "trend": "INSUFFICIENT_SAMPLE", "volatileDirection": null, "w1Average": null, "w3Average": 65 }
    },
    "clinicNeeded": { /* REP-10 — 변경 없음, 가입일 게이트와 무관하게 항상 계산 */ }
  }
}
```

## 삭제된 것

- `domain/report/dto/SleepTrend.java` (STABLE/RISING/FALLING/VOLATILE/INSUFFICIENT_DATA)
- `TriagePolicy.classifySleepTrend()`, `isStagnantMetric()` 및 관련 임시값 상수
  (`SLEEP_TREND_WINDOW_WEEKS`, `SLEEP_TREND_WINDOW_DAYS`, `SLEEP_TREND_HALF_DAYS`,
  `STAGNANT_SCORE_THRESHOLD`, `STAGNANT_RANGE_MAX`, `VOLATILE_STD_DEV_THRESHOLD`,
  `TREND_DIFF_THRESHOLD`)
- `OverallReportService`의 `SleepSessionRepository`·`DailySleepScoreCalculator` 의존성
  (새 판정이 수면 점수를 쓰지 않으므로 불필요해짐)
- `OverallReportResponse`의 `triggered`/`sleepTrend`/`stagnantMetrics` 필드

## 리네임

- `TriagePolicy.java` → `MetricTrendPolicy.java` (트리거 개념이 사라져 이름 변경, git mv로 이력 보존)
- `TriagePolicyTest.java` → `MetricTrendPolicyTest.java`

## 확인된 것

- `SleepTrend`·`classifySleepTrend`·`isStagnantMetric`은 `domain/report`(REP-09 전용) 밖에서
  전혀 참조되지 않음을 2회 재확인 후 삭제 (sleep/skin/todo/game/user, REP-06/08 전부 무관)
- `./gradlew test` 596개 전부 통과
- `ReportApiDocsTest` 등 API 문서화 테스트도 새 응답 구조에 맞춰 갱신 후 통과
- `prd.md`·`api.md`·`erd.md`·`architecture.md` 등 원본 md 파일 변경 없음

