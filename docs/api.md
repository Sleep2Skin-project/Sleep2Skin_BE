# API 명세

**이 문서가 엔드포인트의 유일한 출처다.** 경로가 바뀌면 여기를 먼저 고치고 코드를 맞춘다.

코드 작성 규칙은 [conventions.md](conventions.md), 플로우는 [architecture.md](architecture.md) §3, 기능 정의는 [prd.md](prd.md) §4를 본다.

> **작성 기준일** 2026-08-07 · **도메인 API 18개 + 헬스체크 1개**
>
> **최종 갱신** 2026-08-14 — `todo` 2개 상세 규격 반영 (§2.4)

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
| 3 | **온보딩·동의 상태 조회** (+ 프로필) | `GET` | `/api/v1/users/me` |
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

**3. 온보딩·동의 상태 조회** (ONB-01 진입 분기 + MY-01) — **앱이 시작될 때 가장 먼저 호출한다.** 온보딩 화면을 띄울지, 동의 화면을 띄울지, 바로 홈으로 갈지를 이 한 번의 응답으로 결정한다.

```jsonc
GET /api/v1/users/me
X-User-Id: 1

{ "success": true,
  "data": {
    "userId": 1,
    "nickname": "테스트유저1",
    "onboardingCompleted": true,
    "consentAgreed": true,
    "currentTermsVersion": "1.0",
    "agreedTermsVersion": "1.0",     // 동의 이력이 없으면 null
    "agreedAt": "2026-08-08T00:12:33Z"
  } }
```

**앱은 두 불리언만 보면 된다.**

| `consentAgreed` | `onboardingCompleted` | 앱이 띄울 화면 |
|---|---|---|
| `false` | — | 동의 화면(ONB-02)부터 |
| `true` | `false` | 온보딩 이어서(ONB-03~05) |
| `true` | `true` | 온보딩 전체를 건너뛰고 홈으로 |

**`consentAgreed`는 "동의한 적이 있는가"가 아니라 "현재 약관 버전에 동의했는가"다.** 이게 이 API의 핵심이다. 약관이 개정돼 `ConsentPolicy.CURRENT_TERMS_VERSION`이 올라가면 기존 사용자도 `false`가 되어 자연스럽게 재동의 화면으로 간다. **로컬 플래그로는 이걸 알 방법이 없다** — 앱은 "동의 완료"만 기억하고 있어서 버전이 올라간 것을 영원히 모른다. `consent_history`를 이력 테이블로 유지한 이유가 여기서 실현된다([erd.md](erd.md) §3.2).

`agreedTermsVersion`이 `null`이면 첫 사용자이고, 값이 있는데 `currentTermsVersion`과 다르면 재동의 상황이다. 앱이 화면 문구를 나누고 싶을 때만 쓰면 된다 — **분기 자체는 `consentAgreed` 하나로 충분하다.**

**`baseDate`를 받지 않는다.** 이 응답에는 날짜에 따라 달라지는 값이 없다.

> ⚠️ **MY-01의 `verificationCount`·`streakCount`는 아직 이 응답에 없다.** 연속 검증 횟수는 HOME-09 배너와 **같은 계산을 써야 하고**([prd.md](prd.md) §4.2), 그 계산이 `skin_measurement`에 있어 아직 만들지 않았다. 두 필드가 붙을 때 **`baseDate`가 필수 쿼리 파라미터로 함께 생긴다**(연속 횟수에 "오늘"이 필요하다). 앱 팀은 이 변경을 미리 알고 있어야 한다.

**MY-01은 등급이 아니라 숫자를 반환한다.** 신뢰도 해석은 클라이언트가 한다. 등급만 내려주면 원본 숫자가 가려져 REP-12와 어긋나도 알아채기 어렵다.

**빈 상태가 없어서 `{status, message}`를 쓰지 않는다.** 다른 조회 API와 달리 이 응답은 사용자가 존재하면 언제나 완전하다 — 신규 사용자도 `onboardingCompleted: false`라는 **정상적인 값**을 받는다. 사용자가 없으면 그건 진짜 오류이므로 `404 USER_NOT_FOUND`다.

**4. 수면 데이터 연결 상태** (MY-02) — **마지막 수면 수신 시각**만 반환한다. 서버 배치가 없으므로 그 이상 알 수 있는 게 없다.

**5. 전체 삭제** (MY-04) — 복구 불가 영구 삭제. soft delete가 아니다. 2단계 확인 다이얼로그는 클라이언트 몫이고 서버는 CASCADE로 지운다.

### 2.2 `sleep` — 수면 수집·해석

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | ★ **수면 세션 업로드 → 예보 응답** | `POST` | `/api/v1/sleep/sessions` |
| 2 | **어젯밤 수면 통역 카드** | `GET` | `/api/v1/sleep/interpretation?baseDate=` |

**1. 수면 세션 업로드** (ONB-03 + HOME-03) — **업로드인데 예보를 돌려준다.** 앱이 켜질 때마다 호출하고 서버가 그 자리에서 스코어링까지 끝낸다. 앱은 이 한 번의 호출로 홈 화면을 그린다. → [상세](#3-post-apiv1sleepsessions-상세)

**2. 어젯밤 수면 통역 카드** (HOME-02) — 기준치 대비 편차를 계산해 **가장 부족한 지표 1개**를 골라 헤드라인 문장으로 만든다.

```jsonc
{ "success": true,
  "data": {
    "status": "AVAILABLE",            // 또는 NO_SLEEP_DATA
    "message": null,
    "baseDate": "2026-08-07",
    "interpretation": {               // 빈 상태면 null
      "tone": "IMPROVE",              // 또는 PRAISE
      "headline": "밤중에 3번 깼어요. 다크서클 회복이 더뎌질 수 있어요.",
      "focus": { "feature": "AWAKE_COUNT", "label": "야간 각성", "score": 50 }
    } } }
```

**기준치를 따로 두지 않는다** (2026-08-09 확정). §10.5의 정규화 곡선을 그대로 쓰고, **부분점수가 가장 낮은 피처**가 곧 기준치에서 가장 멀어진 지표다. 기준을 따로 두면 카드는 "충분히 주무셨어요"라고 하는데 예보는 총 수면을 감점한 상태가 될 수 있고, **같은 화면에서 두 문장이 서로 반박한다.** `focus.score`는 예보 산출에 쓰인 것과 같은 값이다.

덕분에 **B6(수면 목표값)을 기다리지 않는다.** B6은 리포트의 "목표 달성" 판정에 여전히 필요하다.

**모든 지표가 안정 구간(76점 이상)이면 `tone: PRAISE`이고 `focus`는 `null`이다.** 분기가 없으면 전부 100점인 밤에도 무언가를 "부족하다"고 지목하게 된다 — 잘 잔 사용자에게 없는 문제를 알려주는 셈이다. 컷은 §10.1의 등급 컷오프를 재사용해 새 임시값을 만들지 않았다.

**앱은 `headline` 문자열이 아니라 `tone`·`focus.feature`로 분기한다.** 문구는 다듬어질 수 있다.

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

**2. 셀피 분석·검증·학습** (HOME-06→07→08) — 멀티파트 이미지를 받아 **세 가지를 한 트랜잭션에서** 처리한다. **구현 완료** (2026-08-10).

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

응답이다 (2026-08-10 확정).

```jsonc
{ "success": true,
  "data": {
    "baseDate": "2026-08-07",
    "analyzedAt": "2026-08-07T12:33:12Z",   // 서버 시각. 운영은 UTC 라 오프셋이 Z 다
    "verifications": [                  // 예보와 대조한 지표만
      { "metric": "DARK_CIRCLE",
        "forecast": { "score": 67, "grade": "NORMAL" },
        "measured": { "score": 61, "grade": "NORMAL" },
        "difference": 6,                // 예보 − 실측
        "verdict": "CLOSE" },
      { "metric": "BARRIER",
        "forecast": { "score": 81, "grade": "STABLE" },
        "measured": { "score": 78, "grade": "STABLE" },
        "difference": 3,
        "verdict": "HIT" }
    ],
    "skipped": [                        // 예보가 없어 대조하지 못한 지표
      { "metric": "COMPLEXION",
        "measured": { "score": 55, "grade": "NORMAL" },
        "reason": "MISSING_FEATURES" }
    ],
    "hitRate": 50,                      // 대조한 지표 중 `HIT` 비율(%)
    "model": {                          // 개인 가중치 학습 결과 (HOME-08)
      "updated": true,
      "message": "야간 각성을(를) 조금 더 중요하게 보도록 학습했어요.",
      "changes": [
        { "feature": "AWAKE_COUNT", "metric": "DARK_CIRCLE", "label": "야간 각성",
          "before": 1.0000, "after": 1.0110 },
        { "feature": "TOTAL_SLEEP", "metric": "DARK_CIRCLE", "label": "총 수면 시간",
          "before": 1.0000, "after": 0.9890 }
      ]
    }
  } }
```

**`changes`는 값이 실제로 바뀐 행만 담는다.** 그날 참여하지 않은 피처와, 참여했지만 보정량이 0이던 피처는 빠진다. **보정량 0은 버그가 아니다** — 두 피처의 부분점수가 같으면 오차를 어느 쪽 탓으로 돌릴 근거가 없어 `Δw = 0`이 된다(§10.7).

**첫 검증은 `changes`가 비어 있어도 `updated: true`다.** 그때 7행이 `1.0`으로 만들어지며, **행의 존재 자체가 "개인화가 시작됐다"는 뜻**이기 때문이다([erd.md](erd.md) §3.7).

**한 피처가 올라가면 같은 지표의 다른 피처는 반드시 내려간다** (§10.7 "합이 0이다"). `message`가 올라간 쪽만 말하는 이유이며, 내려간 쪽까지 말하면 같은 사실을 두 번 말하는 셈이다.

**`difference`는 `예보 − 실측`이다.** 판정 구간(§10.2)이 이 방향으로 정의돼 있다. `verdict`는 `HIT`(±5) · `CLOSE`(±6~15) · `UNDERESTIMATED`(−16 이하) · `OVERESTIMATED`(+16 이상)이며, **`UNDERESTIMATED`는 점수를 낮게 예측한 것 = 피부 위험을 과대평가한 것**이다. 두 축이 반대라 문구에서 뒤집히기 쉽다.

**실측 3종은 항상 나온다.** LLM은 예보와 무관하게 셋을 모두 산출하고 `skin_measurement`도 셋 다 `NOT NULL`이다. **갈리는 것은 실측이 아니라 대조 가능 여부**이며, 그래서 `skipped`에도 `measured`가 실린다 — 예보가 없어 판정만 못 한 것이지 사진을 못 읽은 것이 아니다.

**`hitRate`의 분모는 `verifications`의 길이다 — 3이 아니다.** 빈 지표를 0점으로 취급하면 존재하지 않는 오차가 적중률에 섞이고, 같은 값이 HOME-08의 학습 입력으로 들어가 **없던 값이 개인 가중치를 움직인다.** `verifications`는 비지 않는다 — `DARK_CIRCLE`은 예보가 빈 상태가 될 수 없기 때문이다([erd.md](erd.md) §3.5).

**`skipped[].reason`은 예보 조회 API의 `unavailable[].reason`과 같은 집합**이며 같은 코드(`ScoringPolicy.reasonFor`)에서 나온다. 두 화면이 같은 상황에 다른 문구를 띄우지 않게 하는 것이 요점이다.

**한 트랜잭션이라는 것은 저장·검증·학습 셋을 말한다.** LLM 호출은 그 앞이고 **트랜잭션 밖이다** — 최대 30초 걸리는 외부 호출이 DB 커넥션을 잡고 있으면 셀피가 몰릴 때 커넥션 풀이 고갈되어 **수면 업로드까지 함께 막힌다.**

**중복·예보 부재 검사는 LLM 호출보다 먼저 한다.** 순서가 뒤바뀌면 어차피 `409`/`404`로 끝날 요청에 분석 비용을 쓴다.

| 코드 | `ErrorCode` | 언제 | 앱이 할 일 |
|---|---|---|---|
| `400` | `SELFIE_IMAGE_INVALID` | `image` 파트가 없거나 비었음 · 이미지가 아닌 타입 | 다시 촬영 |
| `400` | `INVALID_INPUT` | `baseDate` 누락·형식 오류 · 파일이 상한(10MB) 초과 | 요청 버그 또는 리사이즈 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 | — |
| `404` | `SKIN_FORECAST_NOT_FOUND` | 그날 예보가 없음 | **먼저 수면을 업로드해야 한다** |
| `409` | `VERIFICATION_ALREADY_DONE` | 그날 이미 검증함 (하루 1회) | 결과 화면으로 |
| `502` | `SELFIE_ANALYSIS_FAILED` | LLM 호출·파싱 실패 | 재시도 |
| `504` | `SELFIE_ANALYSIS_TIMEOUT` | LLM 응답 지연(30초 초과) | 재시도 |

**여기서만 `404`가 에러다.** 조회 API였다면 예보 부재는 `200` + 빈 상태이지만, 이 API는 **대조할 기준이 없으면 동작 자체가 성립하지 않는다**([conventions.md](conventions.md) §2).

**실패하면 `skin_measurement` 행이 생기지 않는다.** 분석 상태 컬럼을 두지 않은 이유이며([erd.md](erd.md) §3.6), 그래서 재시도가 안전하다 — 하루 1회 제약에 걸리지 않는다.

**3. 적중률 · 연속 검증 배너** (HOME-09) — 최근 검증 1건 + 적중률 + 연속 검증 횟수.

```jsonc
{ "success": true,
  "data": {
    "status": "AVAILABLE",          // 또는 NO_VERIFICATION
    "message": null,
    "baseDate": "2026-08-10",
    "summary": {                    // 빈 상태면 null
      "hitRate": 58,                // 누적 — 지금까지 모든 판정 중 `HIT` 비율(%)
      "verificationCount": 5,       // COUNT(skin_measurement)
      "streakCount": 3,
      "latest": {                   // 최근 검증 1건
        "baseDate": "2026-08-09",
        "hitRate": 67,              // 그날치
        "verifications": [ /* POST /skin/selfie 와 같은 모양 */ ],
        "skipped": [ ]
      } } } }
```

**`hitRate`는 누적이다** (2026-08-10 확정). 최근 1건만 쓰면 분모가 최대 3이라 숫자가 `0`·`33`·`67`·`100`으로만 튀고 하루마다 요동친다. 배너가 말하려는 것은 *"예보가 얼마나 믿을 만한가"* 이므로 표본이 쌓일수록 안정되는 쪽이 맞다. **그날치는 `latest.hitRate`에 따로 있다.**

**누적 분모에서도 빈 지표는 빠진다.** 그날 예보가 없던 지표는 판정 자체가 없었으므로 세지 않는다 — `POST /skin/selfie`와 같은 규칙이다.

**`latest`는 셀피 응답과 같은 DTO를 쓴다.** 앱이 파싱 코드를 한 번만 쓰면 된다.

**`streakCount`는 오늘 또는 어제부터 하루도 빠짐없이 이어진 `base_date`의 개수다** ([prd.md](prd.md) §4.2). **오늘 미검증이 연속을 끊지 않는다** — 저녁에 검증하는 사용자가 아침에 앱을 열었을 때 어제까지 쌓은 연속이 `0`으로 보이면 **아직 하지 않은 일로 사용자를 벌주는 것**처럼 읽힌다. `baseDate`가 필수인 이유이며, 없이 계산하면 연속이 하루 밀린다.

> ⚠️ **MY-01 프로필이 `verificationCount`·`streakCount`에 같은 숫자를 써야 한다.** 계산은 한 곳에 두고 두 API가 같은 Service를 호출한다 — 각자 계산하면 어긋난다.

**4. 내 모델** (REP-12) — `personal_weight`를 일반 가중치와 비교해 "각성에 1.6배 민감" 같은 문장을 만든다. **`personal_weight`가 유일한 출처다.**

```jsonc
{ "success": true,
  "data": {
    "status": "AVAILABLE",          // 또는 NO_VERIFICATION (개인화 전)
    "message": null,
    "model": {                      // 빈 상태면 null
      "verificationCount": 5,
      "headline": "야간 각성에 1.6배 민감해요",
      "metrics": [
        { "metric": "DARK_CIRCLE",
          "features": [
            { "feature": "AWAKE_COUNT", "label": "야간 각성",
              "generalShare": 0.5, "personalShare": 0.615, "ratio": 1.23 },
            { "feature": "TOTAL_SLEEP", "label": "총 수면 시간",
              "generalShare": 0.5, "personalShare": 0.385, "ratio": 0.77 }
          ] }
      ] } } }
```

**비율은 같은 지표 안에서만 의미를 갖는다.** `weight`는 일반 가중치에 곱하는 배수이고 곱한 뒤 **지표 내 합이 1로 재정규화**되므로 절댓값 자체에는 의미가 없다([erd.md](erd.md) §3.7). `headline`은 **지표 안에서 최대/최소 비가 가장 큰 지표**를 골라 만든다.

- **`personalShare`는 재정규화된 비중** — 예보가 실제로 쓰는 숫자와 같은 것이라야 화면과 계산이 어긋나지 않는다
- **`generalShare`는 지표 내 균등**(`1/n`). 개인화 전 기준선이다
- **`ratio`가 전부 `1.0`인 지표는 아직 배울 게 없었던 것**이다 — 신규 사용자에게 정상이며 오류가 아니다

**신뢰도 등급을 서버가 만들지 않는다** ([prd.md](prd.md) §4.5 · L8 해결). `verificationCount`를 그대로 주고 등급 해석은 클라이언트가 한다 — 컷오프를 서버에 두면 바꿀 때마다 배포해야 한다.

**`baseDate`를 받지 않는다.** 누적 검증 횟수와 가중치는 "오늘"이 필요 없다. **`personal_weight` 행이 0개면 `NO_VERIFICATION`이다** — 행의 존재 자체가 개인화 시작 여부다.

### 2.4 `todo` — 행동 처방

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | **오늘의 TODO 목록** | `GET` | `/api/v1/todo?baseDate=` |
| 2 | **TODO 완료 체크** | `PATCH` | `/api/v1/todo/{id}` |

**둘 다 구현 완료** (2026-08-13).

**1. 오늘의 TODO 목록** (TODO-02~04) — **`피하세요`와 `이렇게`를 한 응답에 담는다.** 원천 로직(TODO-02)이 하나라 나누면 같은 계산을 두 번 한다.

```jsonc
GET /api/v1/todo?baseDate=2026-08-13
X-User-Id: 1

{ "success": true,
  "data": {
    "baseDate": "2026-08-13",
    "avoidItems": [                   // 최대 3개
      { "id": 41, "category": "AVOID", "title": "강한 각질 제거",
        "causeLabel": "장벽 약화의 원인",
        "reason": "과도한 각질 제거는 피부를 자극하고 장벽을 약하게 만들 수 있어요",
        "status": null }
    ],
    "checklistItems": [               // 최대 5개
      { "id": 44, "category": "DO", "title": "보습 크림 바르기",
        "causeLabel": null, "reason": null, "status": "PENDING" }
    ]
  } }
```

**두 섹션이 서로 다른 필드를 채운다.** `AVOID`는 `causeLabel`(원인 태그) + `reason`(카드 롱프레스 시 노출되는 긴 설명)을 갖고 `status`가 **항상 `null`** 이다 — 체크 대상이 아니기 때문이다. `DO`는 반대로 `status`만 갖고 `causeLabel`·`reason`이 `null`이다.

**`causeLabel`은 DB 컬럼이 아니다.** `target_metric`에서 1:1로 고정 매핑된다(`CauseLabelMapper`) — 24개 행마다 같은 문구를 중복 저장할 이유가 없고, 문구를 바꿀 때 코드 한 줄만 고치면 된다.

| `target_metric` | `causeLabel` |
|---|---|
| `DARK_CIRCLE` | 다크서클의 원인 |
| `COMPLEXION` | 혈색 저하의 원인 |
| `BARRIER` | 장벽 약화의 원인 |

**진행도("n/5")를 서버가 내려주지 않는다.** 클라이언트가 `checklistItems`의 `status`를 세어 계산한다. 서버가 세면 항목 배열과 계산된 숫자 두 곳이 같은 사실을 말하게 되고, 체크 직후 화면에서 둘이 어긋난다.

**절단 개수는 `AVOID` 3 + `DO` 5다.** 하루 최대 8행이며, 후보가 부족하면(그날 모든 지표가 임계값보다 좋으면) 그보다 적거나 빈 배열이다. 카테고리별로 정렬을 따로 돌린다([erd.md](erd.md) §3.8). 후보 풀은 카테고리당 12개(지표 3종 × 4)다.

**`checklistItems`가 화면에서는 "오늘 밤 체크리스트"로 불린다.** **세 번째 카테고리가 아니라 `DO` 상위 5개를 부르는 이름이다** — `ActionCategory`는 `AVOID`/`DO` 2종 고정이고 `NIGHT_CHECK`는 여전히 없다([erd.md](erd.md) §3.8).

**`AVOID`도 `daily_todo`에 저장된다.** 체크 대상이 아닌데도 행을 남기는 것은 **REP가 "그날 무엇을 피하라고 했는지"를 되짚어야 하기 때문**이다. 나중에 다시 계산해도 그날의 답은 나오지 않는다 — 예보·`action_master`·직전 검증이 함께 정하는 값이다.

**목록은 그날 첫 조회 시 만들어져 고정된다.**

| 상황 | 동작 |
|---|---|
| 그날 `daily_todo` 행이 없음 | 추천 엔진을 돌려 행을 만들어 저장하고 반환 |
| 이미 있음 | **다시 계산하지 않고** 그대로 반환 |

**`GET`인데 행을 만든다.** 하루 안에서 임계값·`impact_score`·개인 가중치가 바뀌어도 그날 이미 만든 목록은 바뀌지 않아야 하기 때문이다 — 매 조회마다 계산하면 오전에 본 목록과 오후에 본 목록이 달라지고, REP-10이 "그날 무엇이 추천됐는가"를 재현할 수 없다([erd.md](erd.md) §3.9). 동시 요청 대비로 생성 직전에 한 번 더 확인한다.

**우선순위** — `impact_score × (100 − 그날 예보 점수) + verdictBonus`

- **후보에 뜰지 말지는 예보 점수만 본다.** `예보 점수 ≤ action_master.threshold`인 항목만 남는다
- **그날 예보가 없는 지표를 겨냥한 항목은 후보에서 빠진다.** 비교할 점수가 없다 — `complexion`·`barrier`는 `null`일 수 있다
- **`verdictBonus`는 가장 최근 검증이 `OVERESTIMATED`인 지표에만 붙는다** (`impact_score × 10`). **`OVERESTIMATED`는 예보 점수를 실제보다 높게 낸 것 = 피부 위험을 과소평가한 것**이므로, 그 지표의 액션을 위로 올린다. `UNDERESTIMATED`(예상보다 좋았음)는 위험 신호가 아니라 보너스가 없다
- **동점은 `id` 오름차순**으로 끊는다

> **점수 축은 예보 하나로 통일돼 있다** (2026-08-13 확정). 후보 추출도 우선순위 계산도 `skin_forecast`의 점수만 쓴다. `skin_measurement`에서 가져오는 것은 **판정 결과(`verdict`)뿐이고 실측 점수 자체는 계산에 들어가지 않는다.**
>
> 실측 점수가 매칭에까지 관여하면 "오늘 예보는 좋은데 어제 실측이 나빴다"는 이유로 오늘과 무관한 항목이 뜬다.

**그날 예보가 없으면 `200` + 빈 상태다** (2026-08-14 수정). §1의 규칙 그대로다.

```jsonc
{ "success": true,
  "data": {
    "status": "NO_SLEEP_DATA",
    "message": "수면 데이터가 없어 오늘의 처방이 없습니다.",
    "baseDate": "2026-08-14",
    "avoidItems": [],
    "checklistItems": [] } }
```

> 처음에는 `404 SKIN_FORECAST_NOT_FOUND`였다. 이 엔드포인트가 목록 생성을 겸한다는 것이 이유였지만, **수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면 일상적으로 받는 응답**이라 규칙이 경고하던 상황("4xx로 내면 진짜 문제가 묻힌다")에 정확히 해당했다.

**빈 상태에서 두 배열은 `null`이 아니라 `[]`다.** 예보 객체 하나를 통째로 비우는 `GET /skin/forecast`와 다른데, 여기 페이로드는 배열이라 목록을 그리는 코드가 null 검사 없이 그대로 돌아가는 편이 낫다. 앱은 `status`로만 분기한다.

**`AVAILABLE`인데 배열이 빈 날이 따로 있다.** 그날 모든 지표가 임계값보다 좋아 후보가 0개인 경우다. **"예보가 없다"와 "예보는 있는데 처방할 것이 없다"는 다른 상태**이므로 앱이 띄울 문구도 달라야 한다 — 페이로드만으로는 구분되지 않으니 `status`를 본다.

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `baseDate` 누락·형식 오류 |
| `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더 없음·숫자 아님 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 |

**TODO-01(요약 멘트)은 서버가 만들지 않는다.** 응답에 있는 것은 `baseDate`뿐이다. "가장 취약한 지표 기준 테마 문구"는 `avoidItems[0]`의 `causeLabel`로 클라이언트가 만들 수 있고, 서버에 두면 문구 하나 바꾸는 데 배포가 필요하다.

**2. TODO 완료 체크** (TODO-05) — 본문 `{ "status": "DONE" }`. 경로에 동사를 넣지 않는다. 상태가 `PENDING`/`DONE` 2종뿐이라 **되돌리기도 같은 엔드포인트**로 처리된다.

```jsonc
PATCH /api/v1/todo/44
X-User-Id: 1

{ "status": "DONE" }

{ "success": true,
  "data": { "id": 44, "status": "DONE", "expGained": 10, "totalExp": 120 } }
```

**`AVOID` 항목은 체크할 수 없다.** "오늘은 피하세요" 카드에는 완료 개념이 없다 — 응답에서 `status`가 `null`인 것과 같은 사실이며, 해당 `id`로 요청하면 **`400 ACTION_NOT_CHECKABLE`** 이다.

**조용히 `200`으로 무시하지 않는다** (2026-08-13 확정). 무시하면 그 요청을 보낸 **앱 버그가 드러나지 않는다** — 체크가 안 먹는 이유를 아무도 모르는 상태로 남는다. 명시적 에러라야 개발 중에 바로 잡힌다.

**exp는 상태가 실제로 바뀔 때만 움직인다.**

| 요청 | `expGained` |
|---|---|
| `PENDING` → `DONE` | `+10` |
| `DONE` → `PENDING` (되돌리기) | `-10` |
| 같은 상태로 재요청 | `0` |

**되돌리면 회수한다** (2026-08-14 수정). 회수하지 않으면 **체크를 껐다 켜는 것만으로 exp가 계속 붙는다** — 판정이 "이번에 `DONE`이 됐는가"뿐이라 중복 호출(`DONE → DONE`)만 막히고 토글은 막히지 않는다. 대칭이 아니면 닫히지 않는 자리다.

**`expGained`는 요청한 양이 아니라 실제 증감이다.** 누적 exp는 0 밑으로 내려가지 않으므로, 0인 상태에서 되돌리면 `-10`이 아니라 그보다 작은 값(보통 `0`)이 담긴다. `totalExp`는 조정 이후 `users.exp`의 값이다.

> **앱은 부호를 그대로 반영해야 한다.** `expGained`를 양수로 가정하고 더하면 서버가 막은 무한 적립이 화면에서 되살아난다.

> **게이미피케이션(HOME-04) 중 적립 트리거 하나만 들어왔다.** 레벨 테이블·캐릭터 반응은 여전히 없다([prd.md](prd.md) §8).

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `status` 누락 · 알 수 없는 값 |
| `400` | `ACTION_NOT_CHECKABLE` | `AVOID` 항목을 체크하려 함 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 |
| `404` | `TODO_NOT_FOUND` | 그 `id`가 없거나 **다른 사용자의 항목** |

**남의 항목을 짚으면 `403`이 아니라 `404`다.** 인증이 없는 상태에서 `403`을 주면 "그 id는 존재한다"는 사실이 새어 나간다.

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

**1단계 6개는 전부 끝났다.** 이어서 만든 것은 `GET /skin/verification/summary`(HOME-09) · `GET /skin/model`(REP-12) · **`todo` 2개**(§2.4)다. 남은 것은 `report` 5개와 `user` 3~5번이다.

---

## 5. MVP에서 만들지 않는 것

**아래 엔드포인트를 추가하지 말 것.**

| 기능 | 원래 경로 | 제외 사유 |
|---|---|---|
| TODO 항목 직접 추가 (TODO-06) | `POST /api/v1/todo` | MVP 제외 (2026-08-07) |
| 저녁 수면 가이드 (TODO-07) | `GET /api/v1/todo/sleep-guide` | MVP 제외 — 시뮬레이션 로직 전체가 빠진다 |
| 기록 내보내기 (MY-04 절반) | `GET /api/v1/users/me/export` | MVP 제외 — 형식(JSON/CSV)도 미정이었다 |
| 알림 설정 (MY-03) | — | MVP 제외. `notification_setting` 테이블도 없다 |
| 게이미피케이션 (HOME-04) — 레벨·캐릭터 | — | 방향 미확정. **exp 적립만 `PATCH /todo/{id}`에 들어왔다**(§2.4) — 레벨 테이블·전용 조회 API는 만들지 않는다 |

**백엔드 구현 대상이 아닌 것** (클라이언트 전용): ONB-01, ONB-04, HOME-01, HOME-05, REP-01, MY-05

> **ONB-01은 화면만 클라이언트 전용이다.** 진입 분기(온보딩을 띄울지 건너뛸지)의 근거는 `GET /api/v1/users/me`가 준다(§2.1). 원래는 앱의 로컬 플래그로만 판단할 계획이었으나, **로컬 플래그로는 약관 개정에 따른 재동의를 감지할 수 없어** 서버 조회로 바꿨다 (2026-08-10).
