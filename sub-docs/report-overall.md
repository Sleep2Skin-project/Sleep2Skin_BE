# 종합 리포트(REP-09~11) 구현 결정사항

- 작성일: 2026-08-16
- 관련 원본 문서: prd.md §4.4, §7 L6·L9 / api.md / erd.md (이 파일이 결정 근거, 원본 문서 자체는 미수정)
- 브랜치: feature/report-overall

## 배경

prd.md §7에 L6(트리아지 발동 조건 미정)·L9(클리닉 필요 지표가 예보 3종 밖) 두 가지가 보류 상태로 남아 있었음. 아래 결정으로 둘 다 해소, REP-09~11 구현 착수.

## 1. L6 해결 — 트리아지 발동 조건

기존 "수면 목표 달성 & 특정 지표 정체"에서 목표 달성 판정이 §7 B6(2026-08-14)로 MVP 제외된 상태였음.

**결정**: 목표 달성 대신 **수면 점수 추세**로 대체.

- 관찰 창: 최근 **3주(21일)**, baseDate 상한
- 전반부/후반부 분할: 앞 10일 / 가운데 1일(11일차) 제외 / 뒤 10일
- 판정: `STABLE` / `RISING` / `FALLING` / `VOLATILE` / `INSUFFICIENT_DATA`
  - 전체 유효 표본 < 5 → `INSUFFICIENT_DATA`
  - 전반부 또는 후반부 중 한쪽 유효 표본이 0개 → `INSUFFICIENT_DATA`
  - 유효 표본 표준편차 ≥ 15 → `VOLATILE`
  - 후반부 평균 − 전반부 평균 ≥ +5 → `RISING`, ≤ −5 → `FALLING`, 그 외 `STABLE`
- 피부 지표 정체 판정(예보 3종 각각): 유효 표본 ≥ 5 AND 평균 점수 < 50 AND (최댓값−최솟값) ≤ 5 → 정체. 평균 점수 ≥ 50이면 변동폭과 무관하게 정체 아님(점수가 이미 좋은데 변동 없는 걸 정체로 오판 방지)
- **`stagnantMetrics`는 배열** — 3종 중 여러 개 동시 정체 가능
- 트리아지 발동 = 수면 추세 `STABLE` 또는 `RISING` AND `stagnantMetrics` 1개 이상
- 문장은 서버가 만들지 않음(§4.4 ⑧ 원칙 유지) — 판정 라벨·근거 데이터만 반환

## 2. L9 해결 — 클리닉 필요 지표 3종

선택지 ②(셀피 실측 전용 지표로 추가) 채택, 3종 전체 적용.

**결정**:
- 색소침착·여드름흉터·구조적노화 3종을 `skin_measurement`에 boolean 컬럼으로 추가 (`pigmentationDetected`/`acneScarDetected`/`agingDetected`)
- 0~100 점수화 안 함 — 감지 여부(boolean)만
- 예보 3종(darkCircle/complexion/barrier)과 분리 — 검증(HOME-07)·개인 가중치 학습(HOME-08)에 미관여
- 화면 표시는 **최신 실측값 그대로** (추세/트렌드 계산 없음 — 개선/유지/악화 방식은 검토했으나 단순화 결정)
- **최신 실측 조회는 baseDate 이하 상한** — 과거 리포트 조회 시 미래 실측이 섞이지 않도록
- 실측 이력이 전혀 없는 사용자는 `clinicNeeded`가 `null` — "감지 안 됨(false)"과 "측정한 적 없음"을 구분

## 3. skin 담당 부재로 인한 직접 구현

domain/skin은 원래 팀원 담당이며 prd.md/CLAUDE.md에 "합의 없이 넣지 말 것" 경고가 있음. 그러나 팀원이 부재 중이라 효정님이 이번 기능(SkinMeasurement 컬럼 추가, SkinVisionPrompt 확장)을 직접 구현.

구현 전 `git diff dev -- domain/skin global/infra/openai`로 라인 단위 검증 완료 — 기존 darkCircle/complexion/barrier 선언·로직·HOME-07 대조·HOME-08 학습 경로 변경 없음, 순수 필드 추가만 확인.

## 4. API 스펙 (참고용 — 정식 반영은 api.md 갱신 시)

```
GET /api/v1/report/overall?baseDate=...
```

```jsonc
{
  "status": "FULL",              // OverallReportStatus: FULL | INSUFFICIENT_DATA
  "periodStart": "...",
  "periodEnd": "...",
  "triage": {
    "triggered": true,
    "sleepTrend": "RISING",
    "stagnantMetrics": ["COMPLEXION"]
  },
  "appManaged": ["DARK_CIRCLE", "COMPLEXION", "BARRIER"],
  "clinicNeeded": {
    "pigmentationDetected": false,
    "acneScarDetected": false,
    "agingDetected": true
  },
  "clinicLink": "https://amredclinic.com/ko"
}
```

**OverallReportStatus 판정**: 수면 추세가 `INSUFFICIENT_DATA`일 때만 전체 `INSUFFICIENT_DATA`. 피부 지표별 표본 부족은 `stagnantMetrics`에서 해당 지표만 제외할 뿐 전체 status에 영향 없음.

**appManaged는 계산하지 않음** — SkinMetric enum 선언 순서 그대로의 고정 라벨 배열.

**clinicLink는 상수 고정값**, 클릭 이벤트 기록은 이번 범위에 미포함(prd.md REP-11 "필요 시"로 남아있던 항목, 향후 검토).

## 5. TriagePolicy 상수는 확정값으로 운영 (2026-08-16, 최초 임시값에서 전환)

아래 값을 확정값으로 운영. 문헌 근거는 없고 이 대화에서 정한 실무 판단이며, 값 자체보다 "일관된 기준 하나"를 갖는 것이 목적임을 감안해 사용.

- `SLEEP_TREND_WINDOW_WEEKS = 3` — REP-06/08 등 다른 리포트들이 이미 7일/28일 단위를 쓰고 있어, 그 사이 값으로 3주(21일) 선택
- `STAGNANT_SCORE_THRESHOLD = 50` — §10.1 등급 컷오프(25/50/75)의 "위험/주의" 경계값 재사용. 이미 있는 기준을 새로 만들지 않고 그대로 가져다 씀
- `TREND_DIFF_THRESHOLD = 5`, `STAGNANT_RANGE_MAX = 5` — §10.2 판정 오차 구간의 "±5 적중" 폭을 재사용. 예보-실측 판정에서 이미 쓰던 폭을 추세 판정에도 동일 적용
- 표본 하한(`MIN_SAMPLE_SIZE`)은 `CorrelationPolicy`를 그대로 재사용 — 별도 상수 안 둠. `CorrelationPolicy` 자체는 여전히 임시값 상태(재확정 대상)이며 이번 전환 대상에서 제외.

코드 반영: `TriagePolicy.java` Javadoc 및 상수별 주석에 "확정값 — sub-docs/report-overall.md §5 근거"로 명시 완료.

## 6. 후속 필요 작업

- ~~api.md에 `GET /report/overall` 정식 반영~~ — **완료 (2026-08-18)** → api.md §2.5 5번
- ~~erd.md에 `skin_measurement` 신규 컬럼 3개 반영~~ — **완료 (2026-08-18)** → erd.md §3.6
- REP-11 클릭 이벤트 기록 필요 여부 팀 논의
- `CorrelationPolicy`(상관 강도 구간·표본 하한)는 여전히 임시값 — 별도로 재확정 필요.
  **트리아지 표본 하한이 이 값을 참조하므로 바꾸면 함께 움직인다**(의도한 연결)

## 7. `docs/` 반영 (2026-08-18)

이 문서의 결정이 명세 본문에 옮겨졌다. **이제 규격의 출처는 `docs/`이고 이 파일은 근거 기록이다.**

| 무엇 | 어디 |
|---|---|
| 응답 규격·발동 조건·`clinicNeeded` 의미 | api.md §2.5 5번 |
| 트리아지 판정 기준값 (§1·§5의 상수) | **prd.md §10.10** (신설) |
| L6·L9 해소 표기 | prd.md §7 · §4.4 |
| `skin_measurement` 컬럼 3개 | erd.md §3.6 · 관계도 |
| Vision 스키마·프롬프트 방침 | architecture.md §7 |
| `OverallReportStatus`를 따로 두는 이유 | conventions.md §2 |
