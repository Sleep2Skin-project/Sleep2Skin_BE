# 2026-08-17 프론트 피드백 반영 — 일간 리포트 필드 추가 · REP-07 그룹핑 · TODO threshold 상향

> prd.md / erd.md / api.md 본문은 수정하지 않는다. 이 문서가 변경 근거와 최종 결정, 실제 구현 내용을 담는다.
> 브랜치: `feature/report-todo-tuning`

## 배경

프론트에서 전달받은 이슈 3건 (2026-08-17):
1. 일간 리포트에 REM 수면 데이터가 아예 안 들어옴 (프론트가 임시 하드코딩 중)
2. HRV — 보내는 건 있는데 받는(조회하는) API가 없음
3. 주간/월간 "영향이 컸던 요인"이 7줄로 나열돼 있어 3개(다크서클/장벽/혈색) 카드로 묶어달라는 요청

+ TODO 탭이 종종 빈 화면으로 보이는 문제(효정 진단): threshold(30~70)보다 지표 점수가 높은
정상적인 날엔 추천 후보가 0개가 되는 것 — 버그 아니고 설계대로 동작. 다만 좋은 컨디션에서도
일부 추천이 뜨도록 threshold를 상향하기로 함.

## 1. REM 수면 필드 추가

**문제**: `GET /api/v1/report/daily`의 `sleepSummary`에 `remSleepMinutes`가 없었음.
세션 엔티티엔 이미 있고 `POST /sleep/sessions` 응답에는 나가고 있었는데, 리포트 조회
DTO에서만 누락돼 있었음.

**구현**: `SleepSummary`에 `remSleepMinutes`(int) 추가, `SleepSession.remSleepMinutes`를
그대로 매핑.

## 2. HRV / 안정시심박 노출

**문제**: `POST /sleep/sessions`가 `hrv`, `restingHeartRate`를 받아 저장하지만
되돌려주는 GET 응답이 없었음.

**구현**: `SleepSummary`에 `hrv`(Double, nullable), `restingHeartRate`(Integer, nullable)
추가. `hrv`는 엔티티에 `BigDecimal`로 저장돼 있어 `Double`로 변환해서 매핑.
워치 미착용 등으로 세션에 값이 없으면 `null` 그대로 전파 (§10.6 결측 처리와 동일 원칙 —
값이 없다고 섹션 전체를 감추지 않는다). 워치 미착용 케이스 테스트 추가.

## 3. REP-07 주간·월간 영향 요인 그룹핑

**문제**: `correlations`가 7개 flat 배열로 나가 화면에 항목이 너무 많음.

**결정 및 구현**: 7쌍 매핑(prd.md §10.3)과 상관계수 계산 로직(`CorrelationCalculator`)은
그대로 두고, **응답 DTO 구조를 교체**했다 (필드 추가 아님 — 하위호환 유지할 소비자가
프론트 하나뿐이라 필드 병행 없이 바로 교체).

- 신규 `CorrelationGroup` DTO: `{ skinMetric, correlations: List<FeatureCorrelation> }`
- `WeeklyReportResponse.correlations` / `MonthlyReportResponse.correlations`의 타입을
  `List<FeatureCorrelation>` → `List<CorrelationGroup>`로 교체
- `groupBySkinMetric()` 정적 팩토리로 flat 7개를 **DTO 레벨에서만** 재배열
  (계산은 그대로 flat 7개로 나온 뒤 조립 단계에서만 묶음)
- 그룹 구성: `DARK_CIRCLE`(2개) · `BARRIER`(2개) · `COMPLEXION`(3개, 축소 안 함 — 효정 확정)

## 4. TODO 추천 threshold 상향

**결정**: `action_master` 24행 전체 threshold **+20, 상한 90으로 클램프**
(`LEAST(threshold + 20, 90)`).

**구현 결과**: 기존 threshold 최댓값이 70이라 실제로 90을 넘어 클램프가 걸린 행은
0건 — 24행 전부 정확히 +20씩만 올라감 (예: 30→50, 70→90). 클램프 공식 자체는
안전장치로 코드/SQL에 그대로 남겨둠 (추후 threshold 원본값이 올라가도 안전).

- `db/seed/action_master.sql` — 24행 threshold 값 직접 +20 (재삽입 아님, 값만 수정)
- `db/seed/action_master_raise_threshold.sql` — 운영 RDS용 신규 파일.
  `UPDATE action_master SET threshold = LEAST(threshold + 20, 90);` 한 줄.
  `action_master_fix_encoding.sql`과 같은 스타일로 UPDATE 방식 사용
  (DELETE + 재INSERT 금지 — `daily_todo.action_master_id`가 기존 id를 참조 중이라 위험).
  파일 상단에 `--default-character-set=utf8mb4` 포함 실행 커맨드 주석 포함.

  ⚠️ **운영 RDS 반영은 아직 수동 실행 전** — 배포 후 별도로 실행 필요.

## 갱신된 테스트 / 문서

`DailyReportServiceTest` · `WeeklyReportServiceTest` · `MonthlyReportServiceTest` ·
`ReportControllerTest` · `ReportApiDocsTest` · `ReportControllerSpec`(Swagger).
전체 테스트 스위트 통과 확인.

---

## 부록 — 터미널 Claude(Claude Code)에 전달한 작업 프롬프트 (그대로 보존)

```
Spring Boot 프로젝트에서 브랜치 feature/report-todo-tuning 을 새로 만들고 아래 4가지 변경을 해줘.

0. git checkout -b feature/report-todo-tuning (dev 브랜치 기준)

1. 일간 리포트 응답(GET /api/v1/report/daily)의 수면 요약 DTO에
   remSleepMinutes(int), hrv(Double, nullable), restingHeartRate(Integer, nullable)
   필드를 추가하고, SleepSession 엔티티 값을 그대로 매핑하는 로직을 추가해줘.
   hrv/restingHeartRate는 세션에 값이 없으면 null로 내려가야 해.
   관련 테스트도 갱신해줘.

2. 주간/월간 리포트 응답의 correlations 필드를,
   기존 flat 배열(List<FeatureCorrelation> 7개)에서
   skinMetric 기준 그룹 배열(List<{skinMetric, correlations: List<FeatureCorrelation>}>,
   3그룹 - DARK_CIRCLE 2개/BARRIER 2개/COMPLEXION 3개)로 교체해줘.
   기존 correlations 필드를 다른 곳(예: REP-06 등)에서 참조하고 있는지 먼저 grep으로
   확인하고 진행해줘. 상관계수 계산 로직 자체는 건드리지 마.
   관련 테스트도 갱신해줘.

3. action_master 시드 SQL(db/seed/action_master.sql)을 찾아서
   전체 24행의 threshold 컬럼 값을 +20 하되 90을 넘지 않도록 클램프해줘.
   title/reason/impact_score는 건드리지 마.

4. 위와 별개로, 운영 RDS에 이미 들어간 action_master 데이터를 맞추기 위한
   UPDATE SQL 파일(action_master_raise_threshold.sql)을 db/seed/ 밑에 새로 만들어줘.
   내용: UPDATE action_master SET threshold = LEAST(threshold + 20, 90);
   재삽입 방식(DELETE + INSERT)은 쓰지 마 — daily_todo가 id를 참조하고 있어서 위험함.
   실행 예시 커맨드(--default-character-set=utf8mb4 포함)도 파일 맨 위 주석으로 남겨줘.

작업 전에 관련 파일들을 먼저 찾아서 현재 구조를 보여주고, 계획을 확인받은 뒤 진행해줘.
```
