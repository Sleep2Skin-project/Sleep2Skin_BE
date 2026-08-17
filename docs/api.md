# API 명세

**이 문서가 엔드포인트의 유일한 출처다.** 경로가 바뀌면 여기를 먼저 고치고 코드를 맞춘다.

코드 작성 규칙은 [conventions.md](conventions.md), 플로우는 [architecture.md](architecture.md) §3, 기능 정의는 [prd.md](prd.md) §4를 본다.

> **작성 기준일** 2026-08-07 · **도메인 API 19개 + 헬스체크 1개** — **19개 전부 구현 완료** (§2.5)
>
> **최종 갱신** 2026-08-18 — **종합 리포트(REP-09~11) 구현 반영** (§2.5 5번) — 보류가 풀렸다(§7 L6·L9 해소, 근거는 `sub-docs/report-overall.md`) · 일간 `sleepSummary`에 **REM·HRV·안정시 심박** 추가 · 주간·월간 `correlations`가 **지표별 3그룹 구조로 교체**됐다 (§2.5 3~4번)
>
> 2026-08-16 — 출석 체크인 응답에 **월~일 출석 도장판** 추가 (§2.1 6번) · **엔드포인트를 늘리지 않았다**(§5) · 엔드포인트 개수를 19개로 정정 (출석 체크인을 두 번 세고 있었다)
>
> 2026-08-15 — `report` 구현 반영: 일간·타임라인·주간·월간 4개 규격 확정 (§2.5) · **월간 파라미터가 `yearMonth`가 아니라 `baseDate`다** · 남은 정리 작업 (§4)

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

### exp 응답 — 적립이 일어나는 API가 모두 같은 모양을 쓴다

게이미피케이션(HOME-04) 확정으로 **exp가 붙는 자리가 4곳**이 됐다([prd.md](prd.md) §10.9). 넷이 같은 객체를 쓴다 — **앱이 파싱 코드와 레벨 업 연출을 한 번만 만들면 된다.**

> **넷이 전부 이 모양이 됐다** (2026-08-15). `PATCH /todo/{id}`가 마지막까지 옛 `expGained`·`totalExp` 두 필드를 쓰고 있었고, 그 둘은 각각 `exp.gained`·`exp.totalExp`로 들어갔다. **`TodoControllerTest`가 옛 필드가 없다는 것까지 단언한다** — 되살아나면 앱이 두 벌을 읽는 코드를 유지하게 된다.

```jsonc
"exp": {
  "gained": 25,               // 이번 요청으로 실제 증감한 양 (부호 포함, 0일 수 있다)
  "reasons": [                // 무엇으로 받았는지. 없으면 []
    { "reason": "VERIFICATION_STREAK", "amount": 25 }
  ],
  "totalExp": 320,            // 조정 이후 users.exp
  "level": 3,
  "levelUp": true,            // 이번 요청으로 레벨이 올라갔는가
  "nextLevelExp": 450         // 다음 레벨 컷오프. 만렙(5)이면 null
}
```

| 필드 | 규칙 |
|---|---|
| `gained` | **요청한 양이 아니라 실제 증감이다.** `reasons`의 합과 같지만, 회수가 0에서 멈추는 경우([erd.md](erd.md) §3.1)만 작아진다 |
| `reasons` | 한 요청에 **둘이 함께 실릴 수 있다** — 수면 업로드에서 증가 보상과 90점 보상이 겹치고, TODO 체크에서 개별 완료와 전체 완료 보너스가 겹친다 |
| `levelUp` | 앱이 캐릭터 변경 연출을 띄우는 신호. **서버는 어떤 캐릭터인지 모른다** |
| `nextLevelExp` | **컷오프 절대값이다.** "남은 exp"는 앱이 `nextLevelExp − totalExp`로 계산한다 |

**"다음 레벨까지 남은 exp"를 서버가 빼서 주지 않는다.** 진행도("n/5")를 서버가 세지 않는 것과 같은 판단이다(§2.4) — 두 곳이 같은 사실을 말하면 어긋날 자리가 생긴다.

**`gained: 0`은 정상이다.** 오늘 이미 받은 보상을 다시 요청한 경우이며 `reasons`가 `[]`다. **에러가 아니다** — 앱은 시작할 때마다 호출하므로 일상적으로 발생한다.

> ⚠️ **앱은 `gained`의 부호를 그대로 반영해야 한다.** 양수로 가정하고 더하면 서버가 막은 무한 적립이 화면에서 되살아난다.

---

## 2. 엔드포인트 전체

★ = 핵심 루프 · **굵은 것이 1단계 구현 대상**

### 2.1 `user` — 사용자·동의·설정

| # | 기능 | 메서드 | 경로 |
|---|---|---|---|
| 1 | **개인정보 동의 저장** | `POST` | `/api/v1/users/me/consents` |
| 2 | **온보딩 완료 처리** | `PATCH` | `/api/v1/users/me/onboarding` |
| 3 | **온보딩·동의 상태 조회** (+ 프로필) | `GET` | `/api/v1/users/me?baseDate=` |
| 4 | **수면 데이터 연결 상태** | `GET` | `/api/v1/users/me/data-status` |
| 5 | **전체 삭제 (영구)** | `DELETE` | `/api/v1/users/me` |
| 6 | **출석 체크인 (exp 적립)** | `POST` | `/api/v1/users/me/attendance?baseDate=` |

**1~5는 구현 완료** (3·4·5는 2026-08-14). **6은 미구현** — HOME-04 확정으로 2026-08-14에 추가됐다.

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
GET /api/v1/users/me?baseDate=2026-08-14
X-User-Id: 1

{ "success": true,
  "data": {
    "userId": 1,
    "nickname": "테스트유저1",
    "onboardingCompleted": true,
    "consentAgreed": true,
    "currentTermsVersion": "1.0",
    "agreedTermsVersion": "1.0",     // 동의 이력이 없으면 null
    "agreedAt": "2026-08-08T00:12:33Z",
    "verificationCount": 5,          // MY-01
    "streakCount": 3,                // MY-01
    "level": 3,                      // HOME-04 — 마이페이지 로드맵
    "totalExp": 320,
    "nextLevelExp": 450              // 만렙(5)이면 null
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

**`baseDate`가 필수다** (2026-08-14 추가). 원래는 받지 않았는데 **MY-01의 두 숫자가 붙으면서 생겼다** — 연속 검증 횟수는 "오늘"을 알아야 계산되고 서버는 모른다. 없이 계산하면 연속이 하루 밀린다.

**`streakCount`는 HOME-09 배너와 같은 계산에서 나온다.** 두 화면이 같은 숫자를 보여야 하므로([prd.md](prd.md) §4.2) `VerificationStreakCalculator` 한 곳을 두 API가 호출한다 — **각자 계산하면 어긋나고, 어긋나도 값 범위는 정상이라 알아채기 어렵다.**

**오늘 미검증이 연속을 끊지 않는다.** 오늘 또는 어제부터 이어져 있으면 유효하다 — 저녁에 검증하는 사용자가 아침에 `0`을 보면 아직 하지 않은 일로 벌주는 것처럼 읽힌다.

**MY-01은 등급이 아니라 숫자를 반환한다.** 신뢰도 해석은 클라이언트가 한다. 등급만 내려주면 원본 숫자가 가려져 REP-12와 어긋나도 알아채기 어렵다.

**레벨 3필드는 마이페이지 로드맵용이다** (2026-08-14 추가). 로드맵 화면이 현재 레벨과 다음 레벨까지 필요한 exp를 보여주는데([prd.md](prd.md) §4.2), **이 API가 이미 마이페이지의 프로필 출처**라 조회 엔드포인트를 새로 만들지 않았다. `level`은 `users.exp`에서 계산되며 저장된 컬럼이 아니다.

**캐릭터 이름·이미지는 응답에 없다.** 서버는 레벨 숫자만 알고, 로드맵 5단계의 캐릭터와 문구는 클라이언트 리소스다 — 문구를 고치는 데 배포가 필요 없고 이미지를 서버가 호스팅하지 않아도 된다.

**빈 상태가 없어서 `{status, message}`를 쓰지 않는다.** 다른 조회 API와 달리 이 응답은 사용자가 존재하면 언제나 완전하다 — 신규 사용자도 `onboardingCompleted: false`라는 **정상적인 값**을 받는다. 사용자가 없으면 그건 진짜 오류이므로 `404 USER_NOT_FOUND`다.

**4. 수면 데이터 연결 상태** (MY-02) — **마지막 수면 수신 시각**만 반환한다. 서버 배치가 없으므로 그 이상 알 수 있는 게 없다.

```jsonc
{ "success": true,
  "data": {
    "status": "AVAILABLE",              // 또는 NO_SLEEP_DATA
    "message": null,
    "lastReceivedAt": "2026-08-14T07:10:00Z"   // 빈 상태면 null
  } }
```

**"마지막으로 잔 날"이 아니라 "마지막으로 받은 시각"이다** (`MAX(sleep_session.created_at)`). 며칠 전 데이터를 방금 올린 경우에 잔 날짜를 쓰면 **"동기화가 며칠째 안 됐다"고 잘못 말하게 된다** — 화면이 말하려는 것은 연결 상태다.

**서버가 알 수 없는 것 두 가지** — HealthKit 권한이 살아 있는지(클라이언트 권한 상태다)와 다음 동기화가 언제인지(배치가 없다). 동기화 주기 표기는 앱의 업로드 정책(앱 시작 시)을 그대로 노출한다.

**`baseDate`를 받지 않는다.** 날짜에 따라 달라지는 값이 없다.

**5. 전체 삭제** (MY-04) — 복구 불가 영구 삭제. soft delete가 아니다.

```jsonc
{ "success": true, "data": { "userId": 1, "deleted": true } }
```

**본문 없는 `204`가 아니라 `200` + 공통 래퍼다.** 이 API만 규약을 비켜가면 앱이 여기서만 다르게 파싱해야 한다.

**2단계 확인 다이얼로그는 클라이언트 몫이고, 삭제 후 어느 화면으로 갈지도 서버가 정하지 않는다**([prd.md](prd.md) §7 P2). 서버는 요청을 받으면 즉시 지운다.

> ⚠️ **서버가 자식 테이블을 손으로 지운다 — `CASCADE`가 아니다.** DB에 `users` 외래키가 하나도 없기 때문이며, 근거와 유지 규칙은 [erd.md](erd.md) §5에 있다.

**멱등하지 않다.** 이미 지워진 사용자로 다시 호출하면 `404 USER_NOT_FOUND`다.

**6. 출석 체크인** (HOME-04) — 앱이 시작될 때 한 번 호출한다. **하루 첫 호출에만 `+10`**이고, 그 응답으로 홈 화면의 출석 완료·연속 검증 보상 팝업과 **월~일 출석 도장판**까지 그린다.

```jsonc
POST /api/v1/users/me/attendance?baseDate=2026-08-14
X-User-Id: 1

{ "success": true,
  "data": {
    "baseDate": "2026-08-14",
    "checkedIn": true,              // 이번 요청으로 출석이 기록됐는가
    "streakCount": 3,               // 팝업에 함께 뜨는 연속 검증 횟수
    "exp": { /* §1 exp 응답 */ },

    "weekStartDate": "2026-08-10",  // 기준일이 속한 주의 월요일
    "weekDays": [                   // 월~일 7칸 고정
      { "date": "2026-08-10", "dayOfWeek": "MONDAY",    "status": "ATTENDED" },
      { "date": "2026-08-11", "dayOfWeek": "TUESDAY",   "status": "MISSED"   },
      { "date": "2026-08-12", "dayOfWeek": "WEDNESDAY", "status": "ATTENDED" },
      { "date": "2026-08-13", "dayOfWeek": "THURSDAY",  "status": "ATTENDED" },
      { "date": "2026-08-14", "dayOfWeek": "FRIDAY",    "status": "ATTENDED" },
      { "date": "2026-08-15", "dayOfWeek": "SATURDAY",  "status": "UPCOMING" },
      { "date": "2026-08-16", "dayOfWeek": "SUNDAY",    "status": "UPCOMING" }
    ]
  } }
```

**`POST`이고 본문이 없다.** 상태를 만드는 동작 API라 `GET`이 아니며, 보낼 것이 `baseDate`뿐이라 본문이 필요 없다 — 온보딩 완료(`PATCH .../onboarding`)와 같은 모양이다.

**`baseDate`가 필수다.** 서버는 "오늘"을 모른다(§1). 없이 처리하면 한국 시간 오전 9시 이전에 **출석이 어제 날짜로 찍히고**, 그날 다시 호출할 때 오늘 몫이 또 지급된다.

**두 번째 호출부터 `checkedIn: false` · `exp.gained: 0`이다.**

| 상황 | 코드 | `checkedIn` | `exp.gained` |
|---|---|---|---|
| 그날 첫 호출 | `200` | `true` | `+10` |
| 같은 날 재호출 | `200` | `false` | `0` |

**재호출이 `409`가 아니라 `200`이다.** 앱은 시작할 때마다 호출하므로 하루에 다섯 번 켜면 네 번은 재호출이다 — **정상 흐름을 에러로 만들면 진짜 문제가 묻힌다.** 대신 `checkedIn`으로 팝업을 띄울지 정한다.

> **하루 1회는 `exp_grant`의 유니크가 보장한다**([erd.md](erd.md) §3.10). 애플리케이션 검사만 두면 앱이 두 번 연속 호출할 때(스플래시와 홈에서 각각) 동시 요청으로 조용히 뚫린다. **"오늘 앱을 켰다"는 사실을 담은 다른 행이 없어서** 상태로 환원할 방법도 없다.

**`streakCount`는 연속 검증 횟수이고 출석 연속이 아니다.** 출석 연속은 어디에도 쓰지 않으므로 세지 않는다 — 보상 구간이 걸려 있는 것은 셀피 검증 쪽이다([prd.md](prd.md) §10.9). 계산은 `VerificationStreakCalculator` 한 곳에서 나온다(HOME-09·MY-01과 같은 숫자).

> **연속 검증 보상 자체는 이 API가 주지 않는다.** 검증이 일어나는 `POST /skin/selfie`가 준다(§2.3) — 출석은 앱을 켠 사실에, 연속 보상은 검증한 사실에 붙는다. **여기서 함께 지급하면 셀피를 찍지 않아도 보상이 나간다.**

#### 출석 도장판 — `weekDays` (2026-08-16 추가)

**기준일이 속한 주의 월요일부터 일요일까지 항상 7칸**이고 첫 칸이 언제나 월요일이다. 기록이 없는 날도 배열에서 빠지지 않는다 — **빼면 도장판 칸 수가 주마다 달라진다**(리포트의 "기록 없는 날은 `null`로 남긴다"와 같은 이유다).

| `status` | 뜻 |
|---|---|
| `ATTENDED` | 그날 출석 기록이 있다 |
| `MISSED` | 이미 지난 날인데 기록이 없다 |
| `UPCOMING` | 기준일보다 **미래**라 아직 판정할 수 없다 |

> ⚠️ **`MISSED`와 `UPCOMING`을 같은 빈 칸으로 그리지 말 것.** 오늘이 화요일인데 수·목·금이 "빠뜨림"으로 보이면 사용자는 **하지도 않은 일로** 도장판이 비어 있는 것을 보게 된다. `attended: boolean` 하나로 두지 않은 이유가 이것이다.

**달력 주다 — 리포트 주간(REP-06)과 앵커가 다르다.** 리포트는 `baseDate − 6 ~ baseDate`인 롤링 7일이지만 도장판은 월요일에 고정된다. 도장판은 **요일 자리가 고정돼야 그릴 수 있기** 때문이다. **필드 이름에 `weekly`를 쓰지 않는 이유**이며, 두 기간을 같은 규칙으로 읽지 말 것.

**주 시작일도 `baseDate`에서 역산한다.** 서버 시각으로 계산하면 한국 시간 오전 9시 이전에 주가 통째로 밀려 **월요일 아침에 지난주 도장판이 뜬다.** 서버 로케일의 기본 주 시작일에도 기대지 않는다(미국은 일요일이다).

**재호출이어도 오늘 칸은 `ATTENDED`다.** 도장판은 `checkedIn`이 아니라 **적립 이력이 있는가**로 정해진다 — `checkedIn`을 근거로 삼으면 하루에 두 번째로 앱을 켠 사용자에게 오늘 도장이 사라진다.

> **저장소를 새로 만들지 않았다.** 출석은 이미 `exp_grant`에 `reason = ATTENDANCE` 행으로 남고 유니크 `(user_id, base_date, reason)`가 하루 1행을 보장한다([erd.md](erd.md) §3.10) — **행의 존재 자체가 그날 앱을 켰다는 기록**이라 새 테이블도 컬럼도 필요 없었다. 조회는 적립과 **같은 트랜잭션**에서 일어난다(방금 저장한 오늘 행이 보여야 한다).

**`dayOfWeek`는 `MONDAY` 같은 영어 상수다.** "월"·"화" 표시 문구는 클라이언트가 만든다 — 서버가 내려보내면 문구 하나 바꾸는 데 배포가 필요하다.

**전용 조회 엔드포인트를 만들지 않았다**(§5). 도장판을 그리는 화면(홈)이 이미 이 API를 부르고 있어, 따로 만들면 같은 사실을 말하는 자리가 둘이 된다.

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `baseDate` 누락·형식 오류 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 |

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

덕분에 **B6(수면 목표값)을 기다리지 않았다.** 그 뒤 B6은 **MVP에서 아예 빠졌고**(2026-08-14, [prd.md](prd.md) §4.4), 리포트도 목표 달성 판정 대신 관측값을 그대로 보여준다 — **같은 이유의 결정이다.** 근거 없는 기준선을 세우면 화면이 사용자에게 참/거짓을 단언하게 된다.

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
    },
    "streakCount": 3,                   // 이번 검증까지 반영한 연속 검증 횟수
    "exp": { /* §1 exp 응답 */ }
  } }
```

**`changes`는 값이 실제로 바뀐 행만 담는다.** 그날 참여하지 않은 피처와, 참여했지만 보정량이 0이던 피처는 빠진다. **보정량 0은 버그가 아니다** — 두 피처의 부분점수가 같으면 오차를 어느 쪽 탓으로 돌릴 근거가 없어 `Δw = 0`이 된다(§10.7).

**첫 검증은 `changes`가 비어 있어도 `updated: true`다.** 그때 7행이 `1.0`으로 만들어지며, **행의 존재 자체가 "개인화가 시작됐다"는 뜻**이기 때문이다([erd.md](erd.md) §3.7).

**한 피처가 올라가면 같은 지표의 다른 피처는 반드시 내려간다** (§10.7 "합이 0이다"). `message`가 올라간 쪽만 말하는 이유이며, 내려간 쪽까지 말하면 같은 사실을 두 번 말하는 셈이다.

**`difference`는 `예보 − 실측`이다.** 판정 구간(§10.2)이 이 방향으로 정의돼 있다. `verdict`는 `HIT`(±5) · `CLOSE`(±6~15) · `UNDERESTIMATED`(−16 이하) · `OVERESTIMATED`(+16 이상)이며, **`UNDERESTIMATED`는 점수를 낮게 예측한 것 = 피부 위험을 과대평가한 것**이다. 두 축이 반대라 문구에서 뒤집히기 쉽다.

**⚠️ LLM은 지표 3종 외에 감지 플래그 3종도 함께 산출하지만 이 응답에는 담지 않는다** (2026-08-16 추가). `pigmentationDetected`·`acneScarDetected`·`agingDetected`는 `skin_measurement`에 **저장만 되고** 종합 리포트(`GET /report/overall`)에서만 읽힌다(§2.5 5번). **검증(HOME-07)에도 개인 가중치 학습(HOME-08)에도 관여하지 않는다** — 대조할 예보값이 없기 때문이다. 화면이 이 셋을 요구하는 자리가 종합 리포트뿐이라 검증 응답에 실을 이유가 없다.

**실측 3종은 항상 나온다.** LLM은 예보와 무관하게 셋을 모두 산출하고 `skin_measurement`도 셋 다 `NOT NULL`이다. **갈리는 것은 실측이 아니라 대조 가능 여부**이며, 그래서 `skipped`에도 `measured`가 실린다 — 예보가 없어 판정만 못 한 것이지 사진을 못 읽은 것이 아니다.

**`hitRate`의 분모는 `verifications`의 길이다 — 3이 아니다.** 빈 지표를 0점으로 취급하면 존재하지 않는 오차가 적중률에 섞이고, 같은 값이 HOME-08의 학습 입력으로 들어가 **없던 값이 개인 가중치를 움직인다.** `verifications`는 비지 않는다 — `DARK_CIRCLE`은 예보가 빈 상태가 될 수 없기 때문이다([erd.md](erd.md) §3.5).

**`skipped[].reason`은 예보 조회 API의 `unavailable[].reason`과 같은 집합**이며 같은 코드(`ScoringPolicy.reasonFor`)에서 나온다. 두 화면이 같은 상황에 다른 문구를 띄우지 않게 하는 것이 요점이다.

**연속 검증 보상이 여기서 지급된다** (2026-08-14 추가 · [prd.md](prd.md) §10.9). `reason`은 `VERIFICATION_STREAK`이고 양은 **이번 검증까지 반영한 연속 일수**로 정한다 — 2일 `+5` · 3일 `+10` · 4일 `+15` · **5일 이상 `+25`**.

| `streakCount` | `exp.gained` |
|---|---|
| `1` (첫 검증 또는 연속이 끊긴 뒤 첫 검증) | `0` — **보상 구간이 2일부터다** |
| `2` / `3` / `4` | `+5` / `+10` / `+15` |
| `5` 이상 | `+25` (상한 구간, 매일) |

**`streakCount`는 이번 검증을 포함한 값이다.** 응답의 팝업 문구("3일 연속!")와 지급액이 같은 숫자에서 나와야 한다 — 검증 전 값을 쓰면 화면과 보상이 하루씩 어긋난다. 계산은 HOME-09·MY-01과 같은 `VerificationStreakCalculator`에서 나온다.

> **재시도가 이중 지급으로 이어지지 않는다.** 검증은 하루 1회(`409 VERIFICATION_ALREADY_DONE`)라 두 번째 요청은 여기까지 오지 않고, 그래도 `exp_grant`의 유니크가 마지막 방어선이다([erd.md](erd.md) §3.10). **분석이 실패하면 행도 exp도 생기지 않는다** — 적립은 저장·검증이 끝난 뒤다.

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

**절단 개수는 `AVOID` 3 + `DO` 5이고 고정 개수다** (2026-08-18). 하루 8행이며, **임계값을 만족하는 후보가 모자라도 개수는 채워진다** — 임계값은 후보를 거르지 않고 정렬 1순위로만 쓰인다(아래 "우선순위"). 카테고리별로 정렬을 따로 돌린다([erd.md](erd.md) §3.8). 후보 풀은 카테고리당 12개(지표 3종 × 4)다.

> **그보다 적어지는 경우가 하나 남아 있다** — 그날 예보가 산출된 지표를 겨냥한 후보 자체가 부족할 때다. `complexion`·`barrier`가 `null`인 날에는 `DARK_CIRCLE` 후보 4개만 남아 `DO`가 4개가 된다. **앱은 여전히 배열 길이로 그린다** — 8개를 가정하고 칸을 고정하지 말 것.

**`checklistItems`가 화면에서는 "오늘 밤 체크리스트"로 불린다.** **세 번째 카테고리가 아니라 `DO` 상위 5개를 부르는 이름이다** — `ActionCategory`는 `AVOID`/`DO` 2종 고정이고 `NIGHT_CHECK`는 여전히 없다([erd.md](erd.md) §3.8).

**`AVOID`도 `daily_todo`에 저장된다.** 체크 대상이 아닌데도 행을 남기는 것은 **REP가 "그날 무엇을 피하라고 했는지"를 되짚어야 하기 때문**이다. 나중에 다시 계산해도 그날의 답은 나오지 않는다 — 예보·`action_master`·직전 검증이 함께 정하는 값이다.

**목록은 그날 첫 조회 시 만들어져 고정된다.**

| 상황 | 동작 |
|---|---|
| 그날 `daily_todo` 행이 없음 | 추천 엔진을 돌려 행을 만들어 저장하고 반환 |
| 이미 있음 | **다시 계산하지 않고** 그대로 반환 |

**`GET`인데 행을 만든다.** 하루 안에서 임계값·`impact_score`·개인 가중치가 바뀌어도 그날 이미 만든 목록은 바뀌지 않아야 하기 때문이다 — 매 조회마다 계산하면 오전에 본 목록과 오후에 본 목록이 달라지고, REP-10이 "그날 무엇이 추천됐는가"를 재현할 수 없다([erd.md](erd.md) §3.9). 동시 요청 대비로 생성 직전에 한 번 더 확인한다.

**우선순위** — `impact_score × (100 − 그날 예보 점수) + verdictBonus`

- **임계값은 정렬 1순위다 — 후보를 거르지 않는다** (2026-08-18 변경). `예보 점수 ≤ action_master.threshold`인 항목을 우선순위순으로 먼저 담고, 절단 개수에 모자라면 **미만족 항목을 우선순위순으로 이어 붙인다.** 임계값을 만족하는 항목은 우선순위 점수가 낮아도 미만족 항목보다 항상 앞선다
- **그날 예보가 없는 지표를 겨냥한 항목은 후보에서 빠진다.** 비교할 점수가 없다 — `complexion`·`barrier`는 `null`일 수 있다. **여기서만 후보가 진짜로 줄어든다**
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

**`AVAILABLE`인데 배열이 빈 날이 따로 있다.** **"예보가 없다"와 "예보는 있는데 처방할 것이 없다"는 다른 상태**이므로 앱이 띄울 문구도 달라야 한다 — 페이로드만으로는 구분되지 않으니 `status`를 본다.

> **이 상태에 이르는 경로는 2026-08-18에 하나로 줄었다.** 임계값이 후보를 거르던 시절에는 컨디션이 좋은 날마다 발생했지만, 이제는 **예보가 산출된 지표를 겨냥한 액션이 하나도 없을 때**만 생긴다(액션 마스터가 24행이라 실질적으로는 예보가 `DARK_CIRCLE`만 있고 그 후보가 비활성인 경우다). 드물어졌을 뿐 사라진 것은 아니므로 **앱은 이 분기를 계속 갖고 있어야 한다.**

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `baseDate` 누락·형식 오류 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 |

**TODO-01(요약 멘트)은 서버가 만들지 않는다.** 응답에 있는 것은 `baseDate`뿐이다. "가장 취약한 지표 기준 테마 문구"는 `avoidItems[0]`의 `causeLabel`로 클라이언트가 만들 수 있고, 서버에 두면 문구 하나 바꾸는 데 배포가 필요하다.

**2. TODO 완료 체크** (TODO-05) — 본문 `{ "status": "DONE" }`. 경로에 동사를 넣지 않는다. 상태가 `PENDING`/`DONE` 2종뿐이라 **되돌리기도 같은 엔드포인트**로 처리된다.

```jsonc
PATCH /api/v1/todo/44
X-User-Id: 1

{ "status": "DONE" }

{ "success": true,
  "data": {
    "id": 44,
    "status": "DONE",
    "allCompleted": true,           // 이 요청으로 그날 DO가 전부 DONE이 됐는가
    "exp": { /* §1 exp 응답 */ }
  } }
```

**`AVOID` 항목은 체크할 수 없다.** "오늘은 피하세요" 카드에는 완료 개념이 없다 — 응답에서 `status`가 `null`인 것과 같은 사실이며, 해당 `id`로 요청하면 **`400 ACTION_NOT_CHECKABLE`** 이다.

**조용히 `200`으로 무시하지 않는다** (2026-08-13 확정). 무시하면 그 요청을 보낸 **앱 버그가 드러나지 않는다** — 체크가 안 먹는 이유를 아무도 모르는 상태로 남는다. 명시적 에러라야 개발 중에 바로 잡힌다.

**exp는 상태가 실제로 바뀔 때만 움직인다.** 값은 2026-08-14에 바뀌었다 — HOME-04이 확정되며 `+10`에서 `+5`가 됐고 **전체 완료 보너스가 생겼다**([prd.md](prd.md) §10.9).

**`allCompleted`는 전이가 아니라 현재 상태다** — "지금 그날 `DO`가 전부 `DONE`인가"이고 "이번 요청으로 그렇게 됐는가"가 아니다. 전이는 `exp.reasons`에 `TODO_ALL_DONE`이 실렸는지로 알 수 있어, **여기까지 전이를 담으면 같은 사실을 두 곳이 말하게 된다.** 상태로 두면 같은 요청을 다시 보내도(`gained: 0`) 값이 참으로 남는다. **`AVOID`는 판정에서 빠진다.**

| 요청 | `exp.reasons` | `exp.gained` |
|---|---|---|
| `PENDING` → `DONE` | `TODO_DONE` | `+5` |
| `PENDING` → `DONE` (마지막 하나) | `TODO_DONE` + `TODO_ALL_DONE` | `+35` |
| `DONE` → `PENDING` | `TODO_DONE` | `−5` |
| `DONE` → `PENDING` (전부 완료였다면) | `TODO_DONE` + `TODO_ALL_DONE` | `−35` |
| 같은 상태로 재요청 | `[]` | `0` |

| 요청 | `reasons` | `exp.gained` |
|---|---|---|
| `PENDING` → `DONE` | `TODO_DONE` | `+5` |
| `PENDING` → `DONE` (**이걸로 그날 `DO`가 전부 완료**) | `TODO_DONE` + `TODO_ALL_DONE` | `+35` |
| `DONE` → `PENDING` (되돌리기) | `TODO_DONE` | `−5` |
| `DONE` → `PENDING` (**전부 완료가 깨짐**) | `TODO_DONE` + `TODO_ALL_DONE` | `−35` |
| 같은 상태로 재요청 | `[]` | `0` |

**되돌리면 회수한다** (2026-08-14 수정). 회수하지 않으면 **체크를 껐다 켜는 것만으로 exp가 계속 붙는다** — 판정이 "이번에 `DONE`이 됐는가"뿐이라 중복 호출(`DONE → DONE`)만 막히고 토글은 막히지 않는다. 대칭이 아니면 닫히지 않는 자리다.

**보너스도 같은 대칭을 따른다.** 마지막 항목을 껐다 켜는 것만으로 `+30`이 반복되면 개별 항목에서 막은 구멍이 더 큰 값으로 되살아난다.

**`TODO_DONE`·`TODO_ALL_DONE`은 `exp_grant`에 기록하지 않는다.** 되돌릴 수 있는 적립이라 이력 행을 만들면 회수할 때 지워야 하고, 그러면 그 테이블은 이력이 아니라 상태의 사본이 된다. **`daily_todo.status`가 이미 지급 여부를 말한다** — 대칭이기만 하면 순증이 `+35`에서 멈춘다([erd.md](erd.md) §3.10).

**"모든 이행"의 분모는 그날 `DO` 행의 개수다.** `AVOID`는 체크 대상이 아니라 빠진다. 후보가 부족해 `DO`가 3개뿐인 날은 3개를 채우면 달성이고, **`DO`가 0개인 날은 달성이 아니다** — 아무것도 하지 않고 `+30`을 받는 경로가 생긴다.

**`exp.gained`는 요청한 양이 아니라 실제 증감이다.** 누적 exp는 0 밑으로 내려가지 않으므로, 0인 상태에서 되돌리면 `−5`가 아니라 그보다 작은 값(보통 `0`)이 담긴다.

| 코드 | `ErrorCode` | 언제 |
|---|---|---|
| `400` | `INVALID_INPUT` | `status` 누락 · 알 수 없는 값 |
| `400` | `ACTION_NOT_CHECKABLE` | `AVOID` 항목을 체크하려 함 |
| `404` | `USER_NOT_FOUND` | 없는 사용자 |
| `404` | `TODO_NOT_FOUND` | 그 `id`가 없거나 **다른 사용자의 항목** |

**남의 항목을 짚으면 `403`이 아니라 `404`다.** 인증이 없는 상태에서 `403`을 주면 "그 id는 존재한다"는 사실이 새어 나간다.

### 2.5 `report` — 누적 분석

| # | 기능 | 메서드 | 경로 | 상태 |
|---|---|---|---|---|
| 1 | 일간 리포트 | `GET` | `/api/v1/report/daily?baseDate=` | 완료 |
| 2 | 수면 단계 타임라인 | `GET` | `/api/v1/report/daily/timeline?baseDate=` | 완료 |
| 3 | 주간 리포트 | `GET` | `/api/v1/report/weekly?baseDate=` | 완료 |
| 4 | 월간 리포트 | `GET` | `/api/v1/report/monthly?baseDate=` | 완료 |
| 5 | 종합 리포트 (트리아지) | `GET` | `/api/v1/report/overall?baseDate=` | 완료 |

**다섯 개 전부 구현 완료다** (1~4는 2026-08-15, 5는 2026-08-16). **전부 `baseDate` 하나만 받는다** — 월간도 `yearMonth`가 아니다(아래 4번).

#### 1. 일간 리포트 (REP-02·04·05)

**화면 3개를 한 응답에 담는다.** 한 화면에서 같이 보이고 전부 같은 날짜의 `sleep_session` + `skin_forecast`를 읽어, 나누면 같은 조회를 세 번 돈다.

```jsonc
GET /api/v1/report/daily?baseDate=2026-08-14
X-User-Id: 1

{ "success": true,
  "data": {
    "baseDate": "2026-08-14",
    "sleepSummary": {
      "status": "AVAILABLE",
      "message": null,
      "summary": {
        "totalSleepMinutes": 432,
        "sleepScore": 70,
        "deepSleepMinutes": 126,
        "lightSleepMinutes": 71,     // = SleepSession.coreSleepMinutes
        "awakeCount": 2,
        "awakeMinutes": 7,
        "remSleepMinutes": 36,       // 2026-08-17 추가
        "hrv": 42.0,                 // 2026-08-17 추가 — 워치 미착용이면 null
        "restingHeartRate": 55       // 2026-08-17 추가 — 워치 미착용이면 null
      }
    },
    "skinForecast": {
      "status": "AVAILABLE",
      "message": null,
      "darkCircle":  { "today": 44, "diffFromYesterday": 1 },
      "complexion":  { "today": 63, "diffFromYesterday": 7 },
      "barrier":     { "today": 79, "diffFromYesterday": null }
    }
  } }
```

**두 섹션이 각자 `status`·`message`를 갖는다 — 응답 전체를 하나의 상태로 감싸지 않는다.** 검증을 마친 날의 예보는 세션이 갱신돼도 재산출되지 않으므로(§5.1 중복 수신 차단) **세션 유무와 예보 유무가 항상 같이 가지 않는다.** 한쪽이 비었다고 다른 쪽까지 숨기면 있는 데이터를 못 보여준다. 빈 상태는 각각 `NO_SLEEP_DATA`이며 `summary`는 `null`, 지표 셋은 `today`·`diffFromYesterday`가 전부 `null`이다.

- **`sleepScore`는 예보 점수(HOME-03)와 다른 계산이다** — 그날 참여한 수면 피처 부분점수 `s(f)`의 **단순 평균**이다([prd.md](prd.md) §10.8). 예보는 지표별 가중평균에 개인 가중치까지 곱한 값이다. **화면에서 두 숫자가 나란히 보이므로 라벨을 섞지 말 것**
- **`diffFromYesterday`는 `오늘 − 어제`이고, 어느 한쪽이 없으면 `null`이다** — "변화 없음"이 아니라 "비교 불가"다. `0`으로 채우면 존재하지 않는 비교가 생긴다
- **`awakeCount`·`awakeMinutes`를 리포트에서 다시 계산하지 않는다.** 수면 정규화 시점(5분 임계값)에 확정된 `SleepSession`의 값을 그대로 쓴다

**`remSleepMinutes`·`hrv`·`restingHeartRate`는 2026-08-17에 프론트 요청으로 추가됐다** (`sub-docs/report-todo-tuning.md`). 셋 다 **`SleepSession`에 이미 저장돼 있던 값**이고 계산이 새로 생기지 않았다 — `POST /sleep/sessions` 응답에는 나가는데 조회 쪽 DTO에서만 빠져 있어 프론트가 REM을 하드코딩하고 있었다.

- **`hrv`는 `Double`, `restingHeartRate`는 `Integer`이며 워치 미착용 시 `null`이다.** 엔티티가 `BigDecimal`로 들고 있는 `hrv`를 `Double`로 변환해 싣는다. **`null`을 `0`으로 채우지 않는다** — 결측 처리 원칙(§10.6)과 같은 자리다. 값이 없다고 섹션 전체를 `NO_SLEEP_DATA`로 바꾸지도 않는다: 세션은 존재하기 때문이다
- **`remSleepMinutes`는 `int`다** — 단계별 분은 세션이 있으면 항상 채워진다(MVP 전제, [prd.md](prd.md) §2)

#### 2. 수면 단계 타임라인 (REP-03)

**일간에서 분리했다.** `sleep_stage_segment`를 수백 행 읽어야 해 응답이 크고, 이 테이블을 쓰는 기능이 여기 하나뿐이다.

```jsonc
GET /api/v1/report/daily/timeline?baseDate=2026-08-14

{ "success": true,
  "data": {
    "status": "AVAILABLE",
    "message": null,
    "baseDate": "2026-08-14",
    "sleepOnsetTime": "2026-08-13T23:40:00+09:00",
    "wakeTime": "2026-08-14T07:10:00+09:00",
    "segments": [
      { "stage": "DEEP",  "startTime": "...", "endTime": "..." },
      { "stage": "AWAKE", "startTime": "...", "endTime": "..." }
    ]
  } }
```

`segments`는 **`startTime` 오름차순**이다(리포지토리 조회가 보장 — 응답을 만들며 다시 정렬하지 않는다). `stage`는 `DEEP`·`REM`·`CORE`·`AWAKE`·`UNSPECIFIED` 중 하나이며 **`UNSPECIFIED`도 그대로 나간다** — 렌더링용이라 감추지 않는다.

**집계값(분·비율)을 담지 않는다.** 그건 `SleepSession`이 이미 들고 있고 1번이 내보낸다. 빈 상태는 `200` + `NO_SLEEP_DATA` + 빈 배열이다.

#### 3~4. 주간 (REP-06/07) · 월간 (REP-08)

**기간은 둘 다 `baseDate`에서 역산한다 — 가입일에 고정된 창이 아니다.**

```
주간   periodStart = baseDate − 6      periodEnd = baseDate     (7일)
월간   periodStart = baseDate − 27     periodEnd = baseDate     (28일)
       W1 = baseDate−27 ~ −21 · W2 = −20 ~ −14 · W3 = −13 ~ −7 · W4 = −6 ~ baseDate
```

**월간이 달력의 달이 아니라 최근 28일인 이유** — 가입일이나 달력 경계에 앵커를 두면 주 경계가 사용자마다·달마다 달라지고, 주간과 월간이 서로 다른 방식으로 기간을 끊게 된다. `baseDate` 역산으로 통일하면 **앱이 아무 날짜나 넣어 "그날 기준 최근 7일/28일"을 자유롭게 조회**할 수 있고 두 화면의 계산이 같아진다. **`W4`가 항상 `baseDate`를 포함한 최근 7일이고 `W1`이 가장 과거다.**

```jsonc
GET /api/v1/report/weekly?baseDate=2026-08-14

{ "success": true,
  "data": {
    "status": "FULL",
    "periodStart": "2026-08-08",
    "periodEnd": "2026-08-14",
    "dailyScores": [
      { "date": "2026-08-08", "sleepScore": 62 },
      { "date": "2026-08-09", "sleepScore": null }    // 그날 세션 없음
    ],
    "summary": {
      "avgSleepScore": 70,
      "avgDeepSleepMinutes": 126
    },
    "correlations": [ /* 아래 */ ]
  } }
```

```jsonc
GET /api/v1/report/monthly?baseDate=2026-08-14

{ "success": true,
  "data": {
    "status": "FULL",
    "periodStart": "2026-07-18",
    "periodEnd": "2026-08-14",
    "weeks": [
      { "weekLabel": "W1", "avgSleepScore": 58, "avgDeepSleepMinutes": 104, "isHighest": false },
      { "weekLabel": "W4", "avgSleepScore": 70, "avgDeepSleepMinutes": 110, "isHighest": true }
    ],
    "summary": {
      "avgSleepScore": 61,
      "avgDeepSleepMinutes": 118
    },
    "correlations": [ /* 아래 */ ]
  } }
```

**`status`는 최상위 하나이고 값 집합이 일간과 다르다.** 일간은 두 섹션이 독립적으로 빌 수 있어 섹션마다 `QueryStatus`를 뒀지만, 주간·월간은 **기간 하나가 응답 전체의 성립 여부를 가른다** — 타임라인과 같은 최상위 단일 상태이며 값은 **`FULL`·`INSUFFICIENT_DATA` 둘뿐**이다(`QueryStatus`가 아니라 `ReportPeriodStatus`).

**`INSUFFICIENT_DATA`는 가입일 기준이지 "그 기간에 기록이 있었는가"가 아니다.** 가입 당일을 1일차로 세어(`가입일 → baseDate 일수 + 1`) 주간 7일·월간 28일 미만이면 아직 한 기간 분량이 쌓일 수 없는 신규 사용자다. 이때 `dailyScores`/`weeks`는 빈 배열, `summary`는 `null`, `correlations`도 빈 배열이다.

> **가입한 지 오래됐지만 그 기간에 안 잔 경우는 여전히 `FULL`이다.** 그때는 해당 날짜만 `sleepScore: null`로 나간다 — **데이터 품질 문제와 신규 사용자 문제를 같은 상태로 묶지 않는다.**

- **`dailyScores`는 `FULL`이면 항상 7개다** — 세션이 없는 날도 날짜는 남기고 점수만 `null`이다. **빼버리면 그래프 x축이 주마다 5칸·7칸으로 들쭉날쭉해진다**
- **`sleepScore`는 일간 리포트의 그것과 같은 계산이다**(§10.8). 일간에 있던 계산을 `DailySleepScoreCalculator`로 뽑아 주간·월간이 하루마다 호출한다
- **평균은 `null`을 분모에서 뺀다.** 기록 없는 날을 0점으로 채우면 "안 잔 날"이 "최악으로 잔 날"이 된다. 전부 결측이면 평균도 `null`이다
- **월간 `summary`의 두 평균은 주 평균 4개의 평균이 아니라 28일을 한 번에 평균낸 값이다** — 주마다 결측 일수가 다르면 두 계산이 갈린다(주별 가중치가 달라진다)
- **`isHighest`는 동점이면 여럿이 `true`가 될 수 있다.** `null`인 주는 비교에서 빠지고, 4주 모두 `null`이면 전부 `false`다. **판정 기준은 `avgSleepScore`이지 `avgDeepSleepMinutes`가 아니다**
- **`avgDeepSleepMinutes`는 점수가 아니라 분(minutes)이다** — `SleepSession.deepSleepMinutes`를 그대로 평균낸 관측값이고 0~100 척도가 아니다. **결측 처리는 `avgSleepScore`와 같다**(세션 없는 날은 분모에서 빠지고, 기간 전체가 결측이면 `null`). 목표 대비 달성 여부는 붙지 않는다 — 수면 목표값은 MVP에서 빠졌다([prd.md](prd.md) §7 B6)

**⚠️ 적중률(`hitRate`)·검증일수(`verifiedDays`)는 넣지 않는다** (2026-08-15). 화면에 없어 범위에서 뺐다. 셀피 실측 조회 자체는 아래 `correlations` 때문에 남아 있지만 **적중률로 다시 노출하지 말 것.**

##### `correlations` — 수면 피처 ↔ 피부 지표 상관 강도 (REP-07)

**주간·월간이 같은 배열을 공유한다.** 계산은 `CorrelationCalculator` 한 곳이 하고 두 서비스가 결과를 그대로 싣는다.

**⚠️ 2026-08-17에 구조가 바뀌었다 — flat 7개 배열에서 지표별 3그룹으로 교체했다** (`sub-docs/report-todo-tuning.md`). 화면이 7줄 나열이라 **다크서클·장벽·혈색 3개 카드로 묶어 달라는 프론트 요청**이었다. **필드를 병행하지 않고 타입을 바꿨다** — 소비자가 프론트 하나뿐이라 하위호환을 유지할 이유가 없었다.

```jsonc
"correlations": [
  { "skinMetric": "DARK_CIRCLE",
    "correlations": [
      { "sleepFeature": "AWAKE_COUNT", "featureLabel": "야간 각성",
        "skinMetric": "DARK_CIRCLE", "metricLabel": "다크서클 회복",
        "strength": "VERY_STRONG", "sampleSize": 6, "insufficientSample": false },
      { "sleepFeature": "TOTAL_SLEEP", "featureLabel": "총 수면 시간", "...": "..." }
    ] },
  { "skinMetric": "COMPLEXION", "correlations": [ /* 3개 */ ] },
  { "skinMetric": "BARRIER",    "correlations": [ /* 2개 */ ] }
]
```

- **`FULL`이면 그룹은 항상 3개다** — `SkinMetric.values()` 순서(`DARK_CIRCLE`·`COMPLEXION`·`BARRIER`)이고, 매핑된 피처가 없어도 빈 배열로 포함된다. 안쪽 항목 수는 7쌍 매핑 그대로 **2·3·2**다. **`INSUFFICIENT_DATA`면 그룹 자체가 없다**(빈 배열) — 3개의 빈 그룹이 아니다
- **`FeatureCorrelation`의 필드는 그대로다.** 그룹 안에도 `skinMetric`·`metricLabel`이 남아 있다 — 항목 하나만 떼어 봐도 어느 지표인지 알 수 있게 한 것이고, 바깥 `skinMetric`과 항상 같은 값이다
- **묶는 것은 응답 조립 단계뿐이다.** `CorrelationCalculator`는 여전히 flat 7개를 내고 `CorrelationGroup.groupBySkinMetric()`이 재배열한다 — **상관계수 계산·정렬 로직은 건드리지 않았다.** 그래서 원래 정렬(절댓값 내림차순, 표본 부족은 뒤로)이 **그룹 안에서 그대로 유지된다**

**예보값이 아니라 실측값(셀피 검증)과 비교한다.** 예보값은 애초에 이 피처들로 계산한 값이라, 예보와 상관을 내면 **수면으로 만든 값이 수면과 관련 있다는 것을 다시 확인하는 순환 논증**이 된다. 그래서 기간 안에서 **수면 세션과 셀피 검증이 둘 다 있는 날짜만** 짝으로 삼는다 — 검증하지 않은 날은 표본에서 빠진다.

| `sleepFeature` | 상관 계산에 쓰는 원본값 | → `skinMetric` |
|---|---|---|
| `AWAKE_COUNT` | 야간 각성 횟수 | `DARK_CIRCLE` |
| `TOTAL_SLEEP` | 총 수면 시간(분) | `DARK_CIRCLE` |
| `DEEP_SLEEP` | 깊은 수면 **비율(%)** | `BARRIER` |
| `REM_SLEEP` | REM 수면 **비율(%)** | `BARRIER` |
| `BEDTIME_REGULARITY` | 취침 규칙성(표준편차, 분) | `COMPLEXION` |
| `HRV` | 심박변이도 | `COMPLEXION` |
| `RESTING_HEART_RATE` | 안정시 심박 | `COMPLEXION` |

**7쌍은 예보 산출(§10.3)과 같은 매핑이다** — 여기가 어긋나면 리포트가 예보와 다른 근거를 말하게 된다.

- **"원본값"이지 0~100 부분점수가 아니다.** 부분점수는 이미 구간선형으로 정규화된 값이라 그것끼리 상관을 내면 **정규화 곡선의 모양이 상관계수에 섞여 들어간다**
- **비율의 분모는 여기서도 `deep + rem + core`다**(§10.5와 같은 이유). 단계 합이 `0`인 밤은 비율이 성립하지 않아 그 쌍의 표본에서 빠진다
- **그 피처만 결측인 날은 그 쌍의 계산에서만 빠진다** — 워치 미착용의 HRV·안정시 심박, 이력 3일 미만의 취침 규칙성. 같은 날짜의 다른 쌍은 그대로 쓴다. 그래서 `sampleSize`가 쌍마다 다르다
- **표본이 5개 미만이면 `insufficientSample: true` + `strength: null`이다.** 극단값 하나에 크게 흔들리는 표본으로 "강한 상관"이라고 말하지 않기 위해서다
- **7개를 항상 전부 반환한다** — 표본이 부족해도 빠지지 않는다. 프론트에서 항목 수가 달라지지 않게 한 것이며, **그룹으로 묶은 뒤에도 그대로다**(3그룹 안에 2·3·2)
- **정렬은 상관계수 절댓값 내림차순이고, 표본 부족은 값과 무관하게 맨 뒤로 간다** — **그룹 안에서 유지된다**(그룹 사이에는 정렬이 없다. `SkinMetric` 선언 순서 고정)
- **`strength`는 절댓값으로만 판정한다.** 부호(방향)는 **응답에 없다** — 계수 자체를 내보내지 않는다

```
|r| ≥ 0.7  VERY_STRONG      |r| ≥ 0.4  STRONG
|r| ≥ 0.2  MODERATE         |r| < 0.2  WEAK
```

> ⚠️ **강도 구간(0.7 / 0.4 / 0.2)과 표본 하한(5개)은 임시값이다** ([prd.md](prd.md) §9.2 L7). 통계학에서 흔히 쓰는 구간을 참고했을 뿐 이 도메인 데이터로 검증된 적이 없다. `CorrelationPolicy` 상수만 바꾸면 되도록 분리해 뒀다.
>
> **표본 안에서 한쪽 값이 전부 같으면(분산 0) 상관계수가 정의되지 않는다.** 이 경우 `NaN`을 내보내지 않고 **`0`으로 취급해 `WEAK`이 된다** — `strength: null`이 아니다. `null`은 "표본 부족"만을 뜻하도록 남겨 뒀다.

#### 5. 종합 리포트 (REP-09/10/11)

**2026-08-16에 구현됐다.** 오래 막고 있던 둘이 함께 풀렸다 — 발동 조건의 수면 쪽 항을 **수면 점수 추세**로 대체했고(§7 L6), "클리닉 필요" 3종을 **셀피 실측 전용 boolean 플래그**로 추가했다(§7 L9). 결정 근거는 `sub-docs/report-overall.md`, 정책값은 [prd.md](prd.md) §10.10이다.

```jsonc
GET /api/v1/report/overall?baseDate=2026-08-14
X-User-Id: 1

{ "success": true,
  "data": {
    "status": "FULL",                  // FULL | INSUFFICIENT_DATA
    "periodStart": "2026-07-25",       // baseDate − 20 (21일)
    "periodEnd": "2026-08-14",
    "triage": {
      "triggered": true,
      "sleepTrend": "RISING",          // STABLE | RISING | FALLING | VOLATILE | INSUFFICIENT_DATA
      "stagnantMetrics": ["COMPLEXION"]
    },
    "appManaged": ["DARK_CIRCLE", "COMPLEXION", "BARRIER"],
    "clinicNeeded": {                  // 실측 이력이 전혀 없으면 null
      "pigmentationDetected": false,
      "acneScarDetected": false,
      "agingDetected": true
    },
    "clinicLink": "https://amredclinic.com/ko"
  } }
```

**기간은 `baseDate − 20 ~ baseDate`(21일)로, 주간·월간과 다른 세 번째 창이다.** 역산 방식은 같다 — 여기서도 `baseDate`가 **모든 조회의 상한**이고 "오늘"이나 "전체 최신"을 쓰는 곳이 없다.

**발동 조건은 두 절반의 AND다.**

```
triggered = (sleepTrend ∈ {STABLE, RISING}) AND (stagnantMetrics 가 1개 이상)
```

- **수면 쪽 절반이 없으면 안 되는 이유는 그대로다.** "수면으로는 잡히지 않는 신호"라고 말하려면 **수면은 괜찮았다는 근거**가 있어야 한다. 목표 달성 판정(B6)이 사라진 자리를 **추세**가 대신한다 — 잘 자고 있는데(`STABLE`·`RISING`) 특정 지표만 정체일 때만 발동한다. `FALLING`·`VOLATILE`이면 **정체 지표가 있어도 발동하지 않는다**: 그건 수면으로 설명되는 신호다
- **`stagnantMetrics`는 배열이다.** 3종이 동시에 정체일 수 있다. **표본이 부족한 지표는 여기 담기지 않는다** — 판정 불가와 "정체 아님"을 이 배열에서는 구분하지 않는다
- **문장은 서버가 만들지 않는다.** 판정 라벨과 근거 데이터만 나가고 "수면은 좋아졌는데 혈색이 정체됐어요"는 클라이언트가 조립한다 — REP-02와 같은 원칙이다([prd.md](prd.md) §4.4 ⑧)

**`status`는 수면 추세 하나로만 갈린다.** `sleepTrend`가 `INSUFFICIENT_DATA`일 때만 전체가 `INSUFFICIENT_DATA`이고, **피부 지표 쪽 표본 부족은 그 지표를 `stagnantMetrics`에서 빼기만 한다.**

> **⚠️ 주간·월간과 달리 가입일을 보지 않는다.** 그쪽 `INSUFFICIENT_DATA`는 "가입한 지 7일/28일이 지났는가"지만, 여기는 **실제로 쌓인 유효 표본 수**로만 결정된다 — 가입한 지 오래됐어도 최근 3주에 잔 날이 5일 미만이면 같은 판정을 받는다. **같은 이름의 상태가 두 가지 다른 기준에서 나온다는 것을 기억할 것.** (`ReportPeriodStatus`가 아니라 `OverallReportStatus`인 이유이기도 하다.)

**`appManaged`는 계산하지 않는다.** `SkinMetric` 선언 순서를 그대로 쓰는 **고정 라벨 배열**이고, 지표별 개선 여부에 따라 달라지지 않는다.

##### `clinicNeeded` — 셀피 실측 전용 감지 플래그 3종

**`baseDate` 이하에서 가장 최근 실측 1건**의 플래그를 그대로 옮긴다. 추세·비교가 없다 — "지금 클리닉이 필요해 보이는가"만 보여준다.

- **점수화하지 않는다.** 0~100이 아니라 감지 여부(boolean)뿐이다. 심각도를 셀피 한 장으로 판정할 근거가 없다
- **예보 3종과 분리돼 있다.** 대응하는 예보값이 없어 **HOME-07 대조에도 HOME-08 개인 가중치 학습에도 관여하지 않는다** — "예보와 실측은 같은 세트"라는 원칙은 그 세트(`darkCircle`·`complexion`·`barrier`) 안에서 그대로다. **이 셋을 예보 3종에 섞으면 그때가 원칙 위반이다**
- **`clinicNeeded` 전체가 `null`인 것과 필드 하나가 `null`인 것은 뜻이 다르다.** 전체 `null`은 **실측 이력이 아예 없는 것**이고, 필드 하나만 `null`이면 **그 실측 행이 이 컬럼 도입(2026-08-16) 이전 데이터**라 미측정이다. **어느 쪽도 `false`로 채우지 않는다** — "감지 안 됨"과 "측정한 적 없음"은 다르다
- **`POST /skin/selfie` 응답에는 이 셋이 나가지 않는다.** 저장만 하고 여기서만 읽는다(§2.3)

**`clinicLink`는 서버 상수 고정값**이며 앱은 파싱하지 않고 그대로 연다. **연결 클릭 이벤트 기록(REP-11 "필요 시")은 이번 범위에 없다** — 제휴 지표가 필요해지면 그때 연다.

**도메인 에러는 `404 USER_NOT_FOUND` 하나뿐이다** (나머지는 다른 API와 공통인 `400 INVALID_INPUT`·`400 USER_ID_HEADER_INVALID`). **데이터가 없는 상황은 전부 `200`이다** — 표본이 부족하면 `INSUFFICIENT_DATA`, 실측 이력이 없으면 `clinicNeeded: null`이며 **어느 쪽도 4xx가 아니다.**

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
      "awakeMinutes": 21,
      "sleepScore": 78          // 참여 피처가 0개면 null
    },
    "forecast": {
      "darkCircle": { "score": 68, "grade": "NORMAL" },
      "complexion": { "score": 69, "grade": "NORMAL" },
      "barrier":    { "score": 98, "grade": "STABLE" },
      "unavailable": []
    },
    "exp": { /* §1 exp 응답 */ }
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

#### `sleepScore`와 exp 적립 (2026-08-14 추가)

**`sleepScore`는 그날 스코어링에 참여한 피처의 부분점수 평균이다** — 산식과 한계는 [prd.md](prd.md) §10.8. 저장하지 않고 매번 계산하며, 예보 점수와 **다른 값**이다(이쪽은 수면 자체의 질이다).

**수면 점수 보상 두 종이 여기서 지급된다**([prd.md](prd.md) §10.9).

| `reason` | 조건 | 양 |
|---|---|---|
| `SLEEP_SCORE_IMPROVED` | 전날 수면 점수보다 올랐음 | `(오늘 − 어제) × 2` |
| `SLEEP_SCORE_HIGH` | 오늘 수면 점수 `90` 이상 | `+10` |

**둘은 겹칠 수 있다** — 90점을 넘기며 오른 날은 `reasons`에 둘 다 실린다.

**`processed: false`면 적립하지 않는다.** 재처리를 하지 않은 요청이라 새로 산출된 점수가 없다. 앱이 시작할 때마다 호출하므로 **여기서 매번 적립하면 앱을 다섯 번 켤 때 다섯 번 붙는다.**

> ⚠️ **`processed: true`인데 이미 지급된 경우가 있다.** 해시가 다르고 검증 전이면 같은 날 두 번째 재산출이 일어나고, 그때 수면 점수가 바뀌면 조건이 다시 성립한다. **`exp_grant`의 `(user_id, base_date, reason)` 유니크가 두 번째 지급을 막는다**([erd.md](erd.md) §3.10) — 이 경우 `exp.gained`는 `0`이고 `reasons`는 `[]`다.

**전날 수면 점수가 없으면 `SLEEP_SCORE_IMPROVED`는 지급되지 않는다.** 비교 대상이 없는 것이지 0점에서 오른 것이 아니다 — 신규 사용자의 첫날이 `+180`을 받는 일이 없어야 한다. `SLEEP_SCORE_HIGH`는 전날과 무관하므로 첫날에도 지급된다.

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

**1단계 6개는 전부 끝났다.** 이어서 `GET /skin/verification/summary`(HOME-09) · `GET /skin/model`(REP-12) · **`todo` 2개**(§2.4) · **`user` 3·4·5번**(§2.1) · **출석 체크인**(§2.1 6번) · **`report` 1~4번**(§2.5)까지 끝났다.

**종합 리포트(`GET /report/overall`)까지 끝나 도메인 API 19개가 전부 구현됐다** (2026-08-16 · §2.5 5번). 마지막까지 남아 있던 정책 미정(§7 L6·L9)이 해소된 결과다 — 근거는 `sub-docs/report-overall.md`.

> **exp 적립은 네 API에 흩어져 붙어 있다** — `POST /users/me/attendance` · `POST /sleep/sessions` · `POST /skin/selfie` · `PATCH /todo/{id}`. **넷이 같은 `exp` 객체를 쓴다**(§1). 다섯 번째 적립 지점을 만든다면 그 모양을 따르고, **적립량은 `LevelPolicy`에서 가져온다** — 도메인 쪽에 상수를 복사하면 조용히 갈린다.

### 명세와 코드가 어긋났던 곳 — 2026-08-15에 정리됐다

기능이 끝난 뒤에도 **넷이 어긋난 채로 남아 있었다.** 전부 값 범위가 정상이라 제약에도 테스트에도 걸리지 않았다.

| # | 어긋났던 것 | 지금 |
|---|---|---|
| 1 | TODO 완료 exp가 `+10` (확정값은 `+5`) | `LevelPolicy.TODO_DONE_EXP`를 직접 쓴다 |
| 2 | 그날 `DO` 전부 완료 보너스가 없었다 | `+30`/`−30` 지급·회수 |
| 3 | `PATCH /todo/{id}`만 `exp` 객체가 아니었다 | `exp` + `allCompleted` (§2.4) |
| 4 | `correlations[].metricLabel`이 `"다크서클"` | `"다크서클 회복"` (§2.5) |

- **1·2는 [erd.md](erd.md) §3.10의 검산식으로만 드러났다** — `SUM(exp_grant.amount) + (DO 완료 수 × 5) + (전체 완료일 수 × 30) = users.exp`. **이제 성립한다**
- **4는 방향이 뒤집혀 읽히는 문제였다.** `DARK_CIRCLE`은 "심한 정도"가 아니라 **"회복된 정도"**(높을수록 좋음)라서([prd.md](prd.md) §1), "다크서클"이라고만 쓰면 높은 점수가 "다크서클이 심하다"로 읽힌다

### 2026-08-16~17에 더해진 것

| 무엇 | 어디 |
|---|---|
| 종합 리포트 `GET /report/overall` — 마지막 도메인 API | §2.5 5번 |
| `skin_measurement` 감지 플래그 3종 (클리닉 트리아지 전용) | §2.5 5번 · [erd.md](erd.md) §3.6 |
| 일간 `sleepSummary`에 `remSleepMinutes`·`hrv`·`restingHeartRate` | §2.5 1번 |
| 주간·월간 `correlations`가 지표별 3그룹으로 **교체** (필드 병행 없음) | §2.5 3~4번 |
| `action_master.threshold` 전 행 `+20` (상한 `90`) — 추천이 너무 드물게 뜨는 문제 | [erd.md](erd.md) §3.8 |

**API 스펙이 바뀐 것은 셋이고 그중 하나는 파괴적 변경이다** — `correlations`는 필드를 병행하지 않고 타입을 바꿨다. 소비자가 프론트 하나뿐이라 합의된 교체다.

**⚠️ `action_master.threshold` 상향은 운영 RDS에 아직 반영되지 않았다** — 배포 후 `action_master_raise_threshold.sql`을 사람이 실행해야 한다([workflow.md](workflow.md) §8).

### 2026-08-18에 바뀐 것 — 임계값이 후보를 거르지 않는다

| 무엇 | 어디 |
|---|---|
| `AVOID` 3 + `DO` 5가 **상한이 아니라 고정 개수**가 됐다 | §2.4 · [erd.md](erd.md) §3.8 |

**응답 스키마는 바뀌지 않았다** — 배열 길이만 달라진다. 임계값을 만족하는 후보가 모자라면 미만족 후보가 우선순위순으로 뒤를 채운다.

**`threshold` 상향(2026-08-17)의 후속이다.** `+20`으로도 컨디션이 좋은 날엔 후보가 5개에 못 미쳤다 — **값을 더 올리는 대신 매칭 방식을 바꿨다.** 값을 계속 올리면 결국 전 행이 `100`이 되어 임계값 컬럼이 무의미해지는데, 정렬 1순위로 남기면 "지금 급한 것부터"라는 의미는 그대로 유지된다. **`action_master_raise_threshold.sql` 실행은 여전히 필요하다** — 임계값이 순서를 정하기 때문이다.

---

## 5. MVP에서 만들지 않는 것

**아래 엔드포인트를 추가하지 말 것.**

| 기능 | 원래 경로 | 제외 사유 |
|---|---|---|
| TODO 항목 직접 추가 (TODO-06) | `POST /api/v1/todo` | MVP 제외 (2026-08-07) |
| 저녁 수면 가이드 (TODO-07) | `GET /api/v1/todo/sleep-guide` | MVP 제외 — 시뮬레이션 로직 전체가 빠진다 |
| 기록 내보내기 (MY-04 절반) | `GET /api/v1/users/me/export` | MVP 제외 — 형식(JSON/CSV)도 미정이었다 |
| 알림 설정 (MY-03) | — | MVP 제외. `notification_setting` 테이블도 없다 |
| 게이미피케이션 **전용 조회 API** | `GET /api/v1/game` | **레벨·exp는 확정됐지만**(2026-08-14) 조회 엔드포인트는 만들지 않는다 — 아래 |

> **게이미피케이션에 조회 API를 따로 두지 않는 이유** (2026-08-14) — 레벨·exp를 읽는 화면이 둘인데 **둘 다 이미 부르는 API가 있다.** 마이페이지 로드맵은 `GET /users/me`(§2.1)가, 홈 화면은 앱 시작 시 부르는 `POST /users/me/attendance`의 응답이 채운다. 세 번째 엔드포인트를 만들면 **같은 숫자를 내는 자리가 셋이 되고 어긋나도 알아채기 어렵다.**
>
> **월~일 출석 도장판도 같은 이유로 체크인 응답에 실었다** (2026-08-16, §2.1). `GET /users/me/attendance`를 새로 만드는 대신 `weekStartDate`·`weekDays`를 얹었다 — 도장판을 그리는 화면이 이미 체크인을 부르고 있다. **지난 주로 넘겨 보는 화면이 생기면** 그때 조회 엔드포인트를 연다.
>
> **캐릭터 목록 API도 없다.** 서버는 레벨 숫자만 알고 이름·이미지는 클라이언트 리소스다([prd.md](prd.md) §10.9).

**백엔드 구현 대상이 아닌 것** (클라이언트 전용): ONB-01, ONB-04, HOME-01, HOME-05, REP-01, MY-05

> **ONB-01은 화면만 클라이언트 전용이다.** 진입 분기(온보딩을 띄울지 건너뛸지)의 근거는 `GET /api/v1/users/me`가 준다(§2.1). 원래는 앱의 로컬 플래그로만 판단할 계획이었으나, **로컬 플래그로는 약관 개정에 따른 재동의를 감지할 수 없어** 서버 조회로 바꿨다 (2026-08-10).
