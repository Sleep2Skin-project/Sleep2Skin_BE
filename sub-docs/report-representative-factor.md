# REP-07 `correlations` — 그룹당 대표 1개 응답으로 변경

> 브랜치: `feature/report-representative-factor`
> 관련 원본 문서: [api.md](../api.md) §REP-07(주간·월간 공통 `correlations`), [prd.md](../prd.md) §10.3
> **이 문서가 최신 스펙이다.** `api.md`의 `correlations` 예시(그룹 내 배열)는 이 변경 이전 스냅샷이며 수정하지 않는다.

## 배경

주간/월간 리포트 화면에서 `다크서클`·`혈색`·`장벽` 그룹마다 상관계수 항목이 2~3줄씩 나와, 프론트에서 그룹당 한 줄만 보이도록 요청했다 (2026-08-19).

## 변경 내용

### 1. 응답 구조: 그룹당 배열 → 단일 객체

```jsonc
// 변경 전
{ "skinMetric": "DARK_CIRCLE",
  "correlations": [
    { "sleepFeature": "AWAKE_COUNT", ... , "strength": "VERY_STRONG", ... },
    { "sleepFeature": "TOTAL_SLEEP", ... , "strength": "MODERATE", ... }
  ] }

// 변경 후
{ "skinMetric": "DARK_CIRCLE",
  "topCorrelation": {
    "sleepFeature": "AWAKE_COUNT", "featureLabel": "야간 각성",
    "skinMetric": "DARK_CIRCLE", "metricLabel": "다크서클 회복",
    "strength": "VERY_STRONG", "sampleSize": 6, "insufficientSample": false
  } }
```

`FeatureCorrelation`(개별 항목 7개 필드)은 변경 없음. `CorrelationGroup.correlations`(배열) 필드가 `CorrelationGroup.topCorrelation`(단일 객체)로 바뀌었다.

### 2. 대표 선정 = 정렬 1순위

그룹 내 정렬(`|r|` 내림차순, `insufficientSample: true` 뒤로 — api.md §REP-07 원본 규칙과 동일)에서 **1위 항목**을 그대로 노출한다. 별도의 "대표성" 판단 로직은 없다 — 기존 정렬 결과를 그대로 쓴다.

### 3. 동률 tie-break 추가 (`CorrelationCalculator`)

`|r|`이 완전히 같은 경우를 대비해 2차 정렬 기준을 추가했다: `sleep_feature`의 **`SleepFeature` enum 선언 순서**로 고정한다.

```
AWAKE_COUNT → TOTAL_SLEEP → DEEP_SLEEP → REM_SLEEP → BEDTIME_REGULARITY → HRV → RESTING_HEART_RATE
```

이 순서는 prd.md §10.3 표와 정확히 일치함을 확인했고(선언 순서 자체가 이미 일치), 별도 우선순위 맵 없이 `enum.ordinal()` 자연 순서로 처리한다. 이 tie-break가 없으면 동률일 때 어떤 항목이 대표로 뽑힐지 요청마다 흔들릴 수 있어 추가했다.

### 4. `insufficientSample`뿐인 그룹도 숨기지 않는다

그룹 내 모든 항목이 `insufficientSample: true`여도 필터링·그룹 숨김 없이 1위 항목을 그대로 `topCorrelation`에 담는다. 프론트에서 `insufficientSample` 값을 보고 "표본 부족" 문구로 처리한다.

## 왜 이렇게 했나

- **`CorrelationCalculator`(계산·정렬)는 그대로 두고 `CorrelationGroup.groupBySkinMetric()`만 바꿨다.** 정렬은 이미 이 한 곳에서만 일어나고 그룹핑은 필터링만 하는 구조였어서(§조사 결과), 가장 국소적인 변경 지점이 여기였다.
- **`orElseThrow` 방어 코드를 넣었다.** `FEATURE_METRIC_PAIRS`가 7쌍 고정이라 지표당 최소 2개 항목이 보장되므로 정상 흐름에서는 절대 발생하지 않아야 하는 상황이다. 프로젝트 전역에서 이미 쓰는 `IllegalStateException` + 한국어 메시지 패턴을 그대로 따랐다(새 예외 클래스 추가 안 함).

## 알아둘 것 — 테스트 파급

`orElseThrow`를 추가하면서 `WeeklyReportServiceTest`/`MonthlyReportServiceTest`의 **상관계수와 무관한** 테스트들(평균 계산 등)이 연쇄로 깨졌다. Mockito가 스텁 안 된 `correlationCalculator.calculate(...)` 호출에 빈 리스트를 기본 반환하면서 `orElseThrow`가 터졌기 때문이다. `@BeforeEach`에 3개 지표를 전부 채운 기본 스텁을 추가해 해결했고, 상관계수를 직접 검증하는 테스트만 그 안에서 재스텁한다. **이 도메인에서 mock 응답에 방어 코드(`orElseThrow` 등)를 추가할 땐 관련 없어 보이는 테스트까지 영향받을 수 있다는 걸 기억해둘 것.**

## 변경된 파일

**프로덕션**
- `domain/report/CorrelationCalculator.java` — tie-break 정렬 추가
- `domain/report/dto/response/CorrelationGroup.java` — 배열 → 단일 객체
- `domain/report/ReportControllerSpec.java` — Swagger 예시/설명 텍스트 동기화

**테스트**
- `CorrelationCalculatorTest.java` — 동률 정렬 테스트 추가
- `WeeklyReportServiceTest.java` / `MonthlyReportServiceTest.java` — 그룹핑 테스트 갱신 + 기본 스텁 추가
- `ReportControllerTest.java` — jsonPath 단언을 `topCorrelation` 기준으로 변경

빌드/테스트: `./gradlew build` — 전체 그린.

## 영향받지 않는 것

- 주간·월간 리포트는 같은 `CorrelationCalculator`/`CorrelationGroup`을 공유하므로 **두 리포트 응답이 동시에 함께 바뀐다.** 한쪽만 다르게 처리하는 분기는 없다(의도한 대로).
- `api.md`, `prd.md`, `erd.md`, `architecture.md` 원본은 수정하지 않았다.