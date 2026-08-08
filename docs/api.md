# API 명세

**이 문서가 엔드포인트의 유일한 출처다.** 경로가 바뀌면 여기를 먼저 고치고 코드를 맞춘다.

코드 작성 규칙은 [conventions.md](conventions.md), 플로우는 [architecture.md](architecture.md) §3, 기능 정의는 [prd.md](prd.md) §4를 본다.

> **작성 기준일** 2026-08-07 · **도메인 API 18개 + 헬스체크 1개**

---

## 1. 공통 규약

### 요청

```
X-User-Id: 1                       ← 모든 API 필수
?baseDate=2026-08-07               ← 날짜가 필요한 API (조회·동작 무관)
```

**`X-User-Id` 헤더로 사용자를 식별한다.** 인증이 없으므로 클라이언트가 직접 알려준다. 경로 변수나 쿼리 파라미터가 아니라 헤더인 이유는 **JWT를 붙일 때 헤더를 읽던 자리 한 곳만 바뀌기 때문**이다. 쿼리 파라미터면 API마다 지워야 하고, 경로 변수면 전 경로를 갈아야 한다.

헤더가 없거나 숫자가 아니면 **`400 USER_ID_HEADER_INVALID`**다. 사용자가 실제로 존재하는지는 각 API가 확인해 `404 USER_NOT_FOUND`를 낸다 — 헤더를 읽는 계층은 DB를 보지 않는다(의존 방향이 `domain → global` 한쪽이라 그 반대가 안 된다).

**`baseDate`는 날짜가 필요한 API가 전부 받는다.** 서버는 "오늘"이 언제인지 모른다 — `users`에 `time_zone`을 두지 않기로 했다([erd.md](erd.md) §3.1). 서버 시각(UTC)으로 계산하면 한국 시간 오전 9시 이전에 날짜가 하루 밀린다. 형식은 `YYYY-MM-DD`.

**조회 API만의 규칙이 아니다.** 동작 API도 날짜가 필요하면 같은 자리에서 받는다 — `POST /skin/selfie`가 유일한 예다(§2.3). 멀티파트 요청이라도 폼 필드로 내리지 않는다.

**모든 시각은 ISO 8601 오프셋 포함**(`2026-08-07T07:10:00+09:00`)으로 주고받는다. 오프셋이 없으면 서버가 UTC로 해석해 `sleepDate`가 밀리고, 그 날짜로 조인되는 예보·검증이 전부 어긋난다.

### 응답

```jsonc
{ "success": true,  "data":  { ... } }     // 성공
{ "success": false, "error": { "code": "...", "message": "..." } }   // 실패
```

**비어 있는 쪽은 응답에 나오지 않는다.** 성공 응답에 `error` 키가, 실패 응답에 `data` 키가 없다 — `success`가 이미 같은 정보를 담고 있어 중복이다. 클라이언트는 `success`로 분기한다.

**단, `data` 안쪽의 `null`은 그대로 나온다.** 페이로드의 `null`은 의미 있는 값이다 — `"complexion": null`은 "그 지표를 산출할 수 없었다"는 뜻이고 `unavailable`의 사유와 짝을 이룬다(§3 지표가 빈 경우). 생략되는 것은 래퍼의 두 필드뿐이다.

**빈 상태 처리가 이 서비스의 핵심 규칙이다.**

| 종류 | 빈 상태 처리 | 이유 |
|---|---|---|
| **조회 API** | `200` + 상태 필드 | 신규 사용자에게 **일상적으로** 발생한다. 4xx로 내면 진짜 문제가 묻힌다 |
| **동작 API** | `4xx` + `ErrorCode` | 필요한 것이 없으면 그건 진짜 에러다 |

---

## 2. 엔드포인트 전체

★ = 핵심 루프 · **굵은 것이 1단계 구현 대상**

### 2.1 `user` — 사용자·동의·설정

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | **개인정보 동의 저장** | `POST` | `/api/v1/users/me/consents` |
| 2 | **온보딩 완료 처리** | `PATCH` | `/api/v1/users/me/onboarding` |
| 3 | 프로필 · 검증 횟수 · 연속 횟수 | `GET` | `/api/v1/users/me?baseDate=` |
| 4 | 수면 데이터 연결 상태 | `GET` | `/api/v1/users/me/data-status` |
| 5 | 전체 삭제 (영구) | `DELETE` | `/api/v1/users/me` |

**1. 개인정보 동의 저장** (ONB-02) — 약관 버전과 동의 시각을 `consent_history`에 **append**한다. 재동의는 UPDATE가 아니라 **새 행**이다. 그래야 "언제 어느 버전에 동의했는가"가 남는다.

**요청 본문이 없다.** 약관 버전은 서버 상수(`ConsentPolicy.CURRENT_TERMS_VERSION`, 현재 `"1.0"`)다 — 클라이언트가 임의 문자열을 보내면 이력에 섞여 재동의 판정(`WHERE terms_version <> ?`)이 무의미해진다. 약관이 개정되면 서버가 상수를 올리고, 그 다음 호출부터 새 버전으로 새 행이 쌓인다.

**같은 버전에 대해 멱등하다.** 앱은 재설치·재실행으로 온보딩을 다시 밟으며 같은 호출을 반복하는데, 그때마다 append하면 이력에 의미 없는 행이 쌓인다.

| 상황 | 코드 | `newlyAgreed` | 동작 |
|---|---|---|---|
| 이 버전에 첫 동의 | `201` | `true` | 새 이력 저장 |
| 같은 버전에 이미 동의 | `200` | `false` | **저장하지 않고** 기존 이력 반환 |

```jsonc
{ "success": true,
  "data": { "consentId": 1, "termsVersion": "1.0",
            "agreedAt": "2026-08-08T11:28:19Z", "newlyAgreed": true } }
```

`agreedAt`은 `consent_history.created_at`이다. 행이 생기는 순간이 곧 동의하는 순간이라 별도 컬럼을 두지 않았다([erd.md](erd.md) §3.2).

**2. 온보딩 완료 처리** (ONB-05) — `users.onboarding_completed`를 `true`로. 상태 하나만 바꾸므로 `PATCH`이고 **요청 본문이 없다.**

**멱등하다 — 이미 완료된 사용자도 `200`이다.** 되돌리는 경로가 없어 다시 불러도 상태가 달라질 여지가 없다. 이번 요청으로 바뀌었는지는 `newlyCompleted`로 알린다.

```jsonc
{ "success": true,
  "data": { "userId": 2, "onboardingCompleted": true, "newlyCompleted": true } }
```

**동의 이력이 있는지 서버가 확인하지 않는다.** ONB-02 → ONB-05 순서를 지키는 것은 클라이언트 몫이다. 서버가 막으면 시연용 데이터를 파이프라인에 주입하는 경로가 좁아진다.

**3. 프로필 · 검증 횟수 · 연속 횟수** (MY-01) — **등급이 아니라 숫자를 반환한다.** 신뢰도 해석은 클라이언트가 한다. 등급만 내려주면 원본 숫자가 가려져 REP-12와 어긋나도 알아채기 어렵다. 연속 횟수 계산에 "오늘"이 필요하므로 `baseDate`를 받는다.

**4. 수면 데이터 연결 상태** (MY-02) — **마지막 수면 수신 시각**만 반환한다. 서버 배치가 없으므로 그 이상 알 수 있는 게 없다.

**5. 전체 삭제** (MY-04) — 복구 불가 영구 삭제. soft delete가 아니다. 2단계 확인 다이얼로그는 클라이언트 몫이고 서버는 CASCADE로 지운다.

### 2.2 `sleep` — 수면 수집·해석

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | ★ **수면 세션 업로드 → 예보 응답** | `POST` | `/api/v1/sleep/sessions` |
| 2 | **어젯밤 수면 통역 카드** | `GET` | `/api/v1/sleep/interpretation?baseDate=` |

**1. 수면 세션 업로드** (ONB-03 + HOME-03) — **업로드인데 예보를 돌려준다.** 앱이 켜질 때마다 호출하고 서버가 그 자리에서 스코어링까지 끝낸다. 앱은 이 한 번의 호출로 홈 화면을 그린다. → [상세](#3-post-apiv1sleepsessions-상세)

**2. 어젯밤 수면 통역 카드** (HOME-02) — 기준치 대비 편차를 계산해 **가장 부족한 지표 1개**를 골라 헤드라인 문장으로 만든다.

### 2.3 `skin` — 예보·검증·개인 모델

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | ★ **오늘의 피부 예보** | `GET` | `/api/v1/skin/forecast?baseDate=` |
| 2 | ★ **셀피 분석·검증·학습** | `POST` | `/api/v1/skin/selfie?baseDate=` |
| 3 | 적중률 · 연속 검증 배너 | `GET` | `/api/v1/skin/verification/summary?baseDate=` |
| 4 | 내 모델 (일반 vs 개인화) | `GET` | `/api/v1/skin/model` |

**1. 오늘의 피부 예보** (HOME-03) — 저장된 그날 예보를 조회한다. 산출은 업로드 시점에 이미 끝나 있다. 수면 데이터가 없으면 **200 + 빈 상태**다.

**앱 시작 직후에는 이 API가 필요 없다** — `POST /sleep/sessions`가 업로드 응답에 같은 예보를 실어 보낸다. 여기는 날짜를 바꿔 다시 볼 때 쓴다.

```jsonc
{ "success": true,
  "data": {
    "status": "AVAILABLE",          // 또는 NO_SLEEP_DATA
    "message": null,                // 빈 상태일 때만 안내 문구
    "baseDate": "2026-08-07",
    "forecast": {                   // 빈 상태면 null
      "darkCircle": { "score": 67, "grade": "NORMAL" },
      "complexion": { "score": 62, "grade": "NORMAL" },
      "barrier":    { "score": 81, "grade": "STABLE" },
      "unavailable": []
    } } }
```

**빈 상태와 빈 지표는 층위가 다르다.** `status: NO_SLEEP_DATA`는 예보 자체가 없는 것이고, `forecast.unavailable`은 예보는 있는데 일부 지표만 못 낸 것이다. 후자는 `status`가 `AVAILABLE`이다.

**`forecast` 객체 모양은 `POST /sleep/sessions` 응답의 `forecast`와 같다.** 앱이 파싱 코드를 한 번만 쓰면 된다.

> **이 응답이 조회 API 공용 형태의 기준이다** (2026-08-09 확정). `{status, message, 페이로드}`는 [conventions.md](conventions.md) §2가 정한 것이고, 이 엔드포인트가 첫 구현이다. 리포트·배너 등 이후 조회 API가 같은 모양을 복제한다 — 화면마다 다른 스키마가 생기지 않게 하는 것이 이 규칙의 핵심이다.
>
> `status` 값은 `global/response/QueryStatus`에 모은다: `AVAILABLE` · `NO_SLEEP_DATA` · `INSUFFICIENT_HISTORY` · `NO_VERIFICATION`.

**2. 셀피 분석·검증·학습** (HOME-06→07→08) — 멀티파트 이미지를 받아 **세 가지를 한 트랜잭션에서** 처리한다.

```
멀티파트 수신 → 메모리에서 OpenAI Vision 호출        (HOME-06)
→ 지표 3종 실측 저장 → 예보와 대조·판정               (HOME-07)
→ 개인 가중치 보정                                    (HOME-08)
```

```
POST /api/v1/skin/selfie?baseDate=2026-08-07
X-User-Id: 1
Content-Type: multipart/form-data

image: (파일)
```

| 파라미터 | 위치 | 필수 | 비고 |
|---|---|---|---|
| `baseDate` | 쿼리 | ✅ | 대조할 예보의 기준일. `LocalDate` |
| `image` | 멀티파트 | ✅ | 메모리에서 바로 LLM으로. 저장하지 않는다 |

**동작 API인데도 `baseDate`를 받는다.** 다른 동작 API와 달리 이 요청은 **어느 날짜의 예보와 대조할지**를 알아야 하는데, 서버는 "오늘"을 모른다([conventions.md](conventions.md) §8) — `users`에 `time_zone`이 없어 서버 시각으로 정하면 한국 시간 오전 9시 이전에 하루 밀린 예보와 대조하게 된다. **값 범위는 정상이라 아무 제약에도 안 걸리고 적중률만 무너진다.**

**과거 날짜도 받는다.** 시연용 데이터를 파이프라인을 통과시켜 쌓으려면 이 API로 지난 날짜의 검증을 만들 수 있어야 한다. 서버 시각으로 "오늘"을 고정하면 그 경로가 사라진다.

**이미지는 어디에도 쓰지 않는다** — 엔티티에 이미지 컬럼 자체가 없어 저장할 곳이 없다. **동작 API이므로 그날 예보가 없으면 `404 SKIN_FORECAST_NOT_FOUND`다.** 대조할 기준이 없으면 검증이 성립하지 않는다.

**3. 적중률 · 연속 검증 배너** (HOME-09) — 최근 검증 1건 + 적중률 + 연속 검증 횟수.

**4. 내 모델** (REP-12) — `personal_weight`를 일반 가중치와 비교해 "각성에 1.6배 민감" 같은 문장을 만든다. **`personal_weight`가 유일한 출처다.**

### 2.4 `todo` — 행동 처방

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | 오늘의 TODO 목록 | `GET` | `/api/v1/todo?baseDate=` |
| 2 | TODO 완료 체크 | `PATCH` | `/api/v1/todo/{id}` |

**1. 오늘의 TODO 목록** (TODO-01~04) — **`피하세요`와 `이렇게`를 한 응답에 담는다.** 원천 로직(TODO-02)이 하나라 나누면 같은 계산을 두 번 한다.

**2. TODO 완료 체크** (TODO-05) — 본문 `{ "status": "DONE" }`. 경로에 동사를 넣지 않는다. 상태가 `PENDING`/`DONE` 2종뿐이라 **되돌리기도 같은 엔드포인트**로 처리된다.

### 2.5 `report` — 누적 분석

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | 일간 리포트 | `GET` | `/api/v1/report/daily?baseDate=` |
| 2 | 수면 단계 타임라인 | `GET` | `/api/v1/report/daily/timeline?baseDate=` |
| 3 | 주간 리포트 | `GET` | `/api/v1/report/weekly?baseDate=` |
| 4 | 월간 리포트 | `GET` | `/api/v1/report/monthly?yearMonth=` |
| 5 | 종합 리포트 (트리아지) | `GET` | `/api/v1/report/overall` |

**1. 일간 리포트** (REP-02·04·05) — **화면 3개를 한 응답에 담는다.** 한 화면에서 같이 보이고 전부 같은 날짜의 `sleep_session` + `skin_forecast` 조인이라, 나누면 같은 쿼리를 세 번 돈다.

**2. 수면 단계 타임라인** (REP-03) — **일간에서 분리했다.** `sleep_stage_segment`를 수백 행 읽어야 해 응답이 크고, 이 테이블을 쓰는 기능이 여기 하나뿐이다.

**3~5.** 주간(REP-06/07) · 월간(REP-08) · 종합(REP-09/10/11).

### 2.6 `health`

| 기능 | 메서드 | 경로 |
|---|---|---|
| 헬스체크 | `GET` | `/api/v1/health` |

**구현 완료.** 응답 래퍼·DTO 정적 팩토리·Swagger 어노테이션의 **기준 패턴**이다.

---

## 3. `POST /api/v1/sleep/sessions` 상세

**앱 팀에 넘기는 계약이다.**

### 요청

```jsonc
POST /api/v1/sleep/sessions
X-User-Id: 1
Content-Type: application/json

{
  "segments": [
    { "stage": "AWAKE", "startTime": "2026-08-06T23:35:00+09:00", "endTime": "2026-08-06T23:40:00+09:00" },
    { "stage": "CORE",  "startTime": "2026-08-06T23:40:00+09:00", "endTime": "2026-08-07T00:55:00+09:00" },
    { "stage": "DEEP",  "startTime": "2026-08-07T00:55:00+09:00", "endTime": "2026-08-07T01:32:00+09:00" }
  ],
  "hrv": 41.2,
  "restingHeartRate": 63
}
```

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `segments[]` | 배열 | ✅ | 시간순 정렬. 겹치면 400 |
| `segments[].stage` | enum | ✅ | `AWAKE` · `CORE` · `DEEP` · `REM` · `UNSPECIFIED` |
| `segments[].startTime` · `endTime` | ISO 8601 | ✅ | **오프셋 필수** |
| `hrv` | number | ❌ | ms. 워치 미착용 시 `null` |
| `restingHeartRate` | number | ❌ | bpm. 워치 미착용 시 `null` |

**집계값(총 수면·단계별 분·각성 횟수)을 앱이 보내지 않는다.** 서버가 연속 `AWAKE` 60분에서 세션을 자르기 때문에([prd.md](prd.md) §4.1), 앱이 보고한 총합에는 그 뒤의 낮잠이 섞여 있을 수 있다. **서버가 자를 거면 서버가 세는 것이 맞다.**

### 앱 팀에 반드시 전달할 세 가지

| # | 내용 | 어기면 |
|---|---|---|
| 1 | **`UNSPECIFIED`를 `CORE`로 바꿔 보내지 말 것** | 비율 분모가 오염되어 **장벽 점수만 조용히 틀린다** ([prd.md](prd.md) §10.5) |
| 2 | **시각에 오프셋을 반드시 포함할 것** | `sleepDate`가 하루 밀리고 예보·검증 조인이 전부 어긋난다 |
| 3 | **`inBed`는 보내지 말 것** | 서버가 무시한다. `inBed` 의존 지표는 명세에서 제외됐다 |

### 서버 처리

```
1. 시간순 정렬 · 구간 겹침 검사
2. 세션 경계 자르기    연속 AWAKE 60분 이상 → 첫 기상. 이후 구간 전부 버림
3. 집계               총 수면 = asleep 구간 합 (UNSPECIFIED 포함)
                      deep/rem/core = 단계별 합
                      각성 = 5분~60분 구간의 개수와 합
4. sleepDate 결정      wake_time의 날짜 (오프셋 기준)
5. 해시 계산·비교      ← 저장 전에
6. 저장 → 즉시 스코어링 → 예보 응답
```

### 응답

```jsonc
201 Created

{
  "success": true,
  "data": {
    "processed": true,
    "sleepDate": "2026-08-07",
    "sleep": {
      "sleepOnsetTime": "2026-08-06T14:40:00Z",
      "wakeTime": "2026-08-06T22:10:00Z",
      "totalSleepMinutes": 402,
      "deepSleepMinutes": 54,
      "remSleepMinutes": 71,
      "coreSleepMinutes": 277,
      "awakeCount": 3,
      "awakeMinutes": 21
    },
    "forecast": {
      "darkCircle": { "score": 68, "grade": "NORMAL" },
      "complexion": { "score": 69, "grade": "NORMAL" },
      "barrier":    { "score": 98, "grade": "STABLE" },
      "unavailable": []
    }
  }
}
```

**응답의 시각은 UTC(`Z`)로 나간다** (2026-08-09 확정). 요청은 어떤 오프셋으로 보내도 되지만 응답 표기는 한 가지로 고정한다 — 저장 직후에는 요청 오프셋이, 재수신 조회에서는 UTC가 나와 **같은 API가 경로마다 다른 표기를 내보내는** 것을 막기 위해서다. 가리키는 순간은 같으므로 앱이 자기 타임존으로 변환해 표시한다.

**앱은 시작할 때마다 호출한다.** 하루에 다섯 번 켜면 같은 데이터가 다섯 번 온다.

| 상황 | 코드 | `processed` | 동작 |
|---|---|---|---|
| 그날 첫 수신 | `201` | `true` | 저장 + 스코어링 |
| 해시 동일 | `200` | `false` | **아무것도 하지 않고** 기존 예보 반환 |
| 해시 다름 + 검증 완료 | `200` | `false` | 갱신하지 않고 기존 예보 반환 |
| 해시 다름 + 검증 전 | `200` | `true` | 갱신 + 재산출 |

**검증을 마친 날의 예보는 절대 바뀌지 않는다.** 바뀌면 이미 끝난 셀피 검증의 대조 기준이 사후에 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다.

### 지표가 빈 경우

```jsonc
"complexion": null,
"unavailable": [ { "metric": "COMPLEXION", "reason": "INSUFFICIENT_HISTORY" } ]
```

| `reason` | 언제 |
|---|---|
| `MISSING_FEATURES` | 워치 미착용 — HRV·안정시 심박 없음 |
| `INSUFFICIENT_HISTORY` | 취침 규칙성 이력 3일 미만 (신규 사용자) |
| `NO_SLEEP_STAGES` | 단계 합 0 — 장벽 산출 불가 |

**`null`만 주면 앱이 문구를 고를 수 없어** 이유를 함께 준다. **에러가 아니라 정상 응답이다.**

### 에러

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `segments`가 비어 있음 · **알 수 없는 `stage`** · **시각에 오프셋 누락** · 깨진 JSON |
| `400` | `SLEEP_TIME_INVALID` | `startTime >= endTime` |
| `400` | `SLEEP_STAGE_INVALID` | 구간 겹침 |
| `404` | `USER_NOT_FOUND` | `X-User-Id`가 없는 사용자 |

> **알 수 없는 `stage`와 오프셋 누락이 `SLEEP_STAGE_INVALID`가 아닌 이유** (2026-08-09 수정) — 둘 다 **역직렬화 단계에서 실패**해 컨트롤러에 도달하지 못한다. 어느 필드가 문제였는지로 코드를 나누려면 전역 예외 처리기가 수면 단계 enum 같은 도메인 타입을 알아야 하는데, 의존 방향이 `domain → global` 한쪽이라 그 반대가 성립하지 않는다. 상세 사유는 로그로 남긴다.
>
> `SLEEP_STAGE_INVALID`는 **서버가 직접 판정하는 구간 겹침**에만 쓴다.

---

## 4. 구현 순서

**1단계에서 만드는 것은 6개다.** 나머지는 이 위에 얹히는 조회 API라 상대적으로 단순하다.

```
1  POST   /api/v1/users/me/consents      동의 저장
2  PATCH  /api/v1/users/me/onboarding    온보딩 완료
3  POST   /api/v1/sleep/sessions       ★ 수면 업로드 → 예보 응답
4  GET    /api/v1/skin/forecast        ★ 오늘의 예보
5  GET    /api/v1/sleep/interpretation   수면 통역 카드
6  POST   /api/v1/skin/selfie?baseDate= ★ 셀피 분석·검증·학습
```

★ 셋이 핵심 루프를 관통한다. 전체 우선순위는 [prd.md](prd.md) §8.

---

## 5. MVP에서 만들지 않는 것

**아래 엔드포인트를 추가하지 말 것.**

| 기능 | 원래 경로 | 제외 사유 |
|---|---|---|
| TODO 항목 직접 추가 (TODO-06) | `POST /api/v1/todo` | MVP 제외 (2026-08-07) |
| 저녁 수면 가이드 (TODO-07) | `GET /api/v1/todo/sleep-guide` | MVP 제외 — 시뮬레이션 로직 전체가 빠진다 |
| 기록 내보내기 (MY-04 절반) | `GET /api/v1/users/me/export` | MVP 제외 — 형식(JSON/CSV)도 미정이었다 |
| 알림 설정 (MY-03) | — | MVP 제외. `notification_setting` 테이블도 없다 |
| 게이미피케이션 (HOME-04) | — | 방향 미확정 |

**백엔드 구현 대상이 아닌 것** (클라이언트 전용): ONB-01, ONB-04, HOME-01, HOME-05, REP-01, MY-05
