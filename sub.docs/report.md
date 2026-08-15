# REPORT 도메인 — sub.docs

> 이 문서는 `docs/prd.md`·`docs/erd.md`·`docs/api.md` 등 원본 문서를 대체하지 않는다. REPORT 도메인 구현 과정에서 원본 문서에 없거나 원본과 다르게 확정된 결정사항만 별도로 기록한다. 원본 문서는 이 작업 중 수정하지 않았다.

## 1. 기간 계산 기준 — `baseDate` 역산 (가입일 고정 아님)

- 원래 논의: 주간은 가입일 기준 7일 단위, 월간은 가입일 기준 28일(7일×4주).
- 실제 구현: 가입일에 고정하지 않고 `baseDate`(조회 요청 시 넘기는 쿼리 파라미터)에서 역산한다.
  - 주간: `periodStart = baseDate - 6`, `periodEnd = baseDate`
  - 월간: `periodStart = baseDate - 27`, `periodEnd = baseDate`, `W1`(가장 과거)~`W4`(`baseDate` 포함 최근 7일)로 4등분
- 가입일(`users.created_at`)은 기간을 정하는 데 쓰이지 않고, "신규 사용자 데이터 부족" 판정에만 쓰인다.
- `baseDate`는 `daily`·`daily/timeline`·`weekly`·`monthly` 4개 엔드포인트 전부의 필수 쿼리 파라미터다. 서버가 "오늘"을 자체 판단하지 않으므로 프론트가 항상 명시적으로 넘겨야 한다.

## 2. 신규 사용자 데이터 부족 처리 (`INSUFFICIENT_DATA`)

- 기준: 가입 당일을 1일차로 계산. `ChronoUnit.DAYS.between(createdDate, baseDate) + 1`
- 주간: 이 값이 7 미만이면 `INSUFFICIENT_DATA`
- 월간: 이 값이 28 미만이면 `INSUFFICIENT_DATA`
- `INSUFFICIENT_DATA`면 `dailyScores`/`weeks`/`correlations`는 빈 배열, `summary`는 `null`.

## 3. 일간 리포트 — 화면에 없는 항목 제외

- 원본 CSV 기능명세엔 "인사이트 문장 2줄(REP-02)"과 "REM 수면 표시"가 있었으나, 실제 UI 스크린샷 재확인 결과 둘 다 화면에 없어 최종 구현에서 제외했다.
- `sleepSummary`는 6개 필드만 반환한다: `totalSleepMinutes`, `sleepScore`, `deepSleepMinutes`, `lightSleepMinutes`(`= coreSleepMinutes`), `awakeCount`, `awakeMinutes`.
- `awakeCount`/`awakeMinutes`는 `SleepStageSegment`를 리포트에서 다시 계산하지 않고, `SleepSession`에 이미 저장된 집계값을 그대로 사용한다(이중 계산 방지).
- `sleepSummary`와 `skinForecast`는 독립적으로 조회하고 독립적으로 빈 상태를 판정한다(섹션별 개별 `QueryStatus` 컨벤션 적용). 둘 다 비어도 서로 영향 없음.

## 4. 수면 점수(`sleepScore`) 계산 — 예보 점수와 다른 계산

- §10.8 정의: 그날 참여한 수면 피처 부분점수(`s(f)`)의 단순 평균. 예보(§10.4)처럼 지표별 가중치를 쓰지 않는다.
- 재사용 로직: `BedtimeRegularityCalculator.calculate` → `ScoringCommand.forFeatureScores` → `SkinScoringEngine.featureScores` → 평균/`round`.
- 참여 피처가 0개이거나 그날 `SleepSession` 자체가 없으면 `sleepScore = null`(0점 아님).

> ⚠️ **알려진 이슈 — 중복 구현**: `domain/sleep/SleepScoreCalculator`(게이미피케이션의 exp 지급 흐름에서 전날 점수 재계산용으로 사용)와 `domain/report/DailySleepScoreCalculator`(리포트 전용)가 §10.8을 각자 독립적으로 구현하고 있다. `SleepScoreCalculator`의 Javadoc에는 "REP-02·06·08도 이 값을 쓴다 — 리포트에서 계산을 다시 적지 말 것"이라고 돼 있으나 실제로는 리포트가 이를 참조하지 않는다. 지금은 같은 로직이라 값은 동일하지만, 한쪽만 수정되면 값이 갈릴 위험이 있다. 추후 하나로 통합하는 리팩터링 필요(별도 이슈로 등록됨).

## 5. 주간·월간에 수면 점수 포함 — 원래 논의와 다르게 확정

- 원래 논의: 주간·월간 화면엔 수면 점수를 별도 계산해 넣지 않는 것으로 정리돼 있었다.
- 실제 구현: `dailyScores[].sleepScore`, `weeks[].avgSleepScore`, `summary.avgSleepScore` 전부 포함했다.
- 사유: 주간·월간이 라인/막대 그래프 형태인데 그릴 값(y축) 자체가 없으면 화면이 성립하지 않는다고 판단해 추가함.
- 계산 방식은 4번의 "하루치 수면 점수"를 7일/28일 반복 적용 후 평균한다. `null`(그날 세션 없음)인 날짜는 평균에서 제외하고, 전부 `null`이면 평균도 `null`이다.
- 월간 `summary.avgSleepScore`는 28일 전체를 플랫하게 평균한 값이며, 주(`weeks`) 평균의 평균이 아니다(둘이 실제로 갈리는 케이스가 있어 구분함).
- 평균 깊은수면(`avgDeepSleepMinutes`)도 동일한 방식(같은 `average()` 헬퍼 재사용)으로 weekly `summary`, monthly `weeks`/`summary`에 추가했다. 원본 화면(평균 수면 점수·평균 깊은수면 나란히 표시)에 있던 항목인데 최초 구현에서 누락했다가 추가함.

## 6. 상관 강도(REP-07, `correlations`) — 실측값 기준

- weekly·monthly 응답에 `correlations` 배열을 추가했다(7쌍 고정).
- 7쌍 매핑(예보 산출과 동일): 야간각성→다크서클, 총수면→다크서클, 깊은수면비율→장벽, REM비율→장벽, 취침규칙성→혈색, HRV→혈색, 안정시심박→혈색.
- **예보값이 아니라 셀피 실측값(`skin_measurement`)을 사용한다.** 근거: `prd.md` §10.7 "REP-07은 `sleep_session` × `skin_measurement` 상관계수"로 명시돼 있음. 예보값은 이미 이 피처들로 계산해서 만든 값이라 상관관계를 재면 동어반복이 되므로, 예보와 독립적인 실측값을 써야 의미 있는 관측이 된다.
- 계산 대상: 기간 내 수면 세션과 셀피 실측이 둘 다 있는 날짜(검증일)만 짝지어 피어슨 상관계수를 계산한다(자체 구현, 외부 라이브러리 미사용).
- 유효 표본 5개 미만이면 계산하지 않고 `insufficientSample: true`, `strength: null`.
- 강도 라벨: `|r|` 0.7 이상 `VERY_STRONG`, 0.4~0.7 `STRONG`, 0.2~0.4 `MODERATE`, 0.2 미만 `WEAK`.

  > ⚠️ **이 구간값(0.7/0.4/0.2)과 최소 표본 수(5)는 임시값이다.** 문서(§7 L7)에 확정 기준이 없어 통계에서 흔히 쓰는 구간을 임시로 채택했다. `CorrelationPolicy` 클래스에 상수로 모아뒀으며 팀 재확인이 필요하다.

- 표본 부족이어도 7쌍 전부 항상 반환한다(일부만 숨기지 않음). 정렬은 절댓값 내림차순이고, 표본 부족은 배열 맨 뒤로 간다.
- 분산이 0인 경우(피처·지표 값이 표본 내내 동일) 에러 없이 `WEAK`로 처리한다 — 명세에 없던 엣지 케이스로, 나눗셈 에러(`NaN`) 방지 목적이다.

## 7. EXP — report 도메인은 관여하지 않음

- 초기 요구사항에 "수면 점수 상승 시 exp 지급(전날 대비 상승×2, 90점 이상 +10)"이 있었으나, 조사 결과 이미 `domain/game`(게이미피케이션)에 `SLEEP_SCORE_IMPROVED`·`SLEEP_SCORE_HIGH` 사유로 완전히 구현돼 있었다(`domain/sleep/SleepSessionService.grantSleepScoreExp`, `POST /sleep/sessions` 흐름에서 호출, `exp_grant` 테이블 유니크 제약으로 중복 방지, 테스트 존재).
- report 도메인은 이 흐름에 관여하지 않는다. `user.exp`를 읽기만 해야 하는 상황도 없다(report 응답 어디에도 `exp` 필드 없음).

## 8. `skin` 도메인 불가침 원칙

- REPORT 작업 전체에서 `skin` 도메인 파일(`SkinScoringEngine`, `SkinForecastRepository`, `SkinMeasurementRepository` 등)은 직접 수정하지 않고 참조만 했다.
- 상관계수 계산 로직이 `skin` 도메인(`SkinVerificationService` 등)과 일부 중복되지만, 공용 유틸로 리팩터링하지 않고 report 도메인 안에서 직접 계산하는 방식을 택했다(다른 팀원 작업 영역 최소 침범 원칙).

## 9. 종합/트리아지 화면(REP-09~11) — 보류 중, 미해결 사항

이번 REPORT 작업 범위에서 완전히 제외했다. 재개 시 아래를 확인해야 한다.

- "수면 목표: 달성" 문구를 그대로 못 쓴다 — 수면 목표값 자체가 MVP에서 빠져 있어 근거 없는 판정이 된다. 대체 조건(예: "수면 점수가 기간 내 안정적/상승/하락")이 미확정(§7 L6).
- 트리아지 발동 조건은 없애고 종합 탭을 무조건 노출하는 것으로 방향만 잡았다(확정).
- "클리닉이 필요한 것" 목록을 실측 데이터 기반으로 판단하려면 색소침착 등 새 피부 지표가 필요하다 — `skin_measurement` 컬럼 추가는 기술적으로 어렵지 않으나 LLM Vision 프롬프트 확장이 필요해 `skin` 도메인(셀피 분석) 담당자와 별도 협의가 필요하다.
- "여드름흉터"·"구조적노화"는 셀피로 측정 불가능한 영역이라 데이터 기반이 아닌 고정 문구로 남을 수밖에 없다.

## 10. 검증 완료 기록

- 로컬 DB(`user_id=2`)에 30일치 `sleep_session`/`skin_measurement`/`skin_forecast`/`sleep_stage_segment`를 의도적 상관관계 패턴으로 시딩하여, Swagger + 실제 API 호출로 `daily`/`daily/timeline`/`weekly`/`monthly` 4개 엔드포인트 전부 검증 완료(검증일: 2026-08-15). 운영 DB는 미접촉.
