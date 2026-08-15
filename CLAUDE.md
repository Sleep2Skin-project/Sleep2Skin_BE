# sleep2skin_be

애플워치 수면 데이터를 피부 지표로 변환해 예보하고, 셀피로 검증해 개인 모델을 학습시키는 서비스의 백엔드. 멋사 14기 중앙해커톤 프로젝트.

## 기술 스택

Java 21 · Spring Boot 4.1.0 · Gradle 9.5.1 · MySQL + Spring Data JPA · springdoc-openapi
배포: AWS EC2 + Docker · DB: AWS RDS(MySQL) · 외부 연동: OpenAI Vision(`gpt-5.6-terra`)
**S3는 쓰지 않는다.** 셀피는 멀티파트로 받아 메모리에서 바로 LLM에 넘기고 지표만 저장한다.

## 명령어

```bash
./gradlew bootRun     # 실행 (8080)
./gradlew test        # 테스트
./gradlew build       # 빌드 + 테스트
```

Swagger UI: http://localhost:8080/swagger-ui.html · 헬스체크: `/api/v1/health`

## 패키지 구조

```
com.allday.sleep2skin_be
├── global/     config · response · exception · entity · infra   (공통 인프라)
└── domain/
    ├── user/     사용자 · 동의 이력 · 설정
    ├── sleep/    수면 세션 수신 · 정규화 · 집계
    ├── skin/     피부 예보 · 셀피 실측 · 검증 · 개인 모델
    ├── todo/     추천 엔진 · TODO 리스트
    ├── report/   일간 · 타임라인 · 주간 · 월간 (종합은 보류)
    ├── game/     레벨 · 경험치 적립 · 출석 (HOME-04)
    └── health/   헬스체크 (구현 완료 — 패턴 참고용)
```

도메인마다 `Controller / Service / repository / entity / dto`. Controller-Service-Repository 3계층.
의존 방향은 **`domain → global` 한쪽뿐이다.** `global`이 `domain`을 참조하면 안 된다.

## 핵심 루프

```
수면 수집(앱이 전달) → 피부 예보 → 행동 처방(TODO) → 셀피 검증 → 개인 모델 학습 → 리포트
```

②③④⑤가 서버 로직이며 이 프로젝트의 실질적 범위다.

## 반드시 지킬 것

### 피부 지표는 3종 고정

```java
enum SkinMetric { DARK_CIRCLE, COMPLEXION, BARRIER }   // 다크서클 회복 · 혈색 · 장벽
```

전부 0~100 점수, 높을수록 좋음. 예보와 실측이 **같은 세트**여야 검증이 성립한다.
**`DARK_CIRCLE`은 "심한 정도"가 아니라 "회복된 정도"다** — 각성이 많을수록 점수가 내려간다. UI 표시명 "다크서클 회복". LLM Vision 프롬프트에서 이 방향이 뒤집히면 값 범위는 정상이라 아무 제약에도 안 걸리고 적중률만 무너진다.
기능명세서 초안의 유분·칙칙함·색소침착은 확정에서 **제외**됐다. 추가하지 말 것.

### 셀피 원본은 보관하지 않는다

멀티파트 수신 → **메모리에서 바로 LLM 호출** → 지표 숫자만 RDS 저장. **이미지를 어디에도 쓰지 않는다** — S3·디스크·캐시 전부.
**엔티티에 이미지 경로·URL 컬럼을 두지 않는다.** 이게 정책을 지키는 가장 강한 장치다 (저장할 곳이 없으니 넣을 값도 없다).
`SkinVisionClient`는 `byte[]`/`MultipartFile`을 받는다 — **스토리지 키를 받는 시그니처를 만들지 말 것.** 그 순간 업로드가 필요해진다.
**고지 문구는 주어를 생략하지 않는다** (B4 확정, prd.md §2).

- (O) "분석을 위해 **외부 AI(OpenAI)로 이미지가 전송**됩니다" · "**서버에** 저장하지 않습니다" · "분석 직후 즉시 삭제하며 얼굴 복원 가능한 데이터를 보관하지 않습니다"
- (X) "**어디에도** 저장되지 않습니다" · "OpenAI도 보관하지 않습니다" — **제공자 쪽 보관 여부를 우리가 모른다.** 확인되지 않은 프라이버시 주장이다

"저장하지 않습니다"는 주어가 없어 "아무도 저장하지 않는다"로 읽힌다. **"서버에"를 붙이면 참이고 빼면 거짓일 수 있다.**

### MVP 전제 — 수면 데이터는 완전하게 온다

**앱이 보내는 수면 데이터에 누락이 없다고 가정한다** (prd.md §2). 워치를 차고 잤고 단계·HRV·안정시 심박이 모두 기록된 상태로 온다.

**B5(앱↔서버 단계 계약)가 보류**됐다 — 페이로드 규격은 우리가 정의해 통보하면 된다.

**전제가 깨져도 틀린 값이 나가지 않게 짰다.** 비용이 0이고 전제가 지켜지면 결과가 같다.

- **비율의 분모는 `총 수면`이 아니라 `deep + rem + core`다** (§10.5). 단계 미상 구간이 분모에 들어가면 **측정 못 한 시간이 "깊은 수면이 아니었던 시간"으로 계산된다**
- **단계 합이 0이면 `BARRIER`를 빈 상태로** (§10.6). **0점과 결측은 다르다** — 그대로 계산하면 장벽이 "위험"으로 나가는데 이건 없는 위험을 경고하는 것이다

**페이로드 규격 조건** — 단계 미상 구간을 `core`에 합쳐 보내면 안 된다. `total`에는 포함하되 단계별 분에는 넣지 않는다. 합치면 분모를 바꾼 의미가 사라진다.

**이 전제로 줄지 않는 것**: §10.6 재정규화(신규 사용자는 취침 규칙성 3일 미만을 반드시 탄다)와 빈 상태 처리(데이터 품질이 아니라 사용자가 새로워서 생긴다).

### 수면 세션은 첫 기상까지다

**낮잠은 넣지 않는다.** 기상 후 다시 잠든 것은 별개의 수면이며 예보에 쓰지 않는다. 하루에 여러 수면이 와도 **첫 수면만** `sleep_session` 한 행이 된다 (prd.md §4.1).

연속 `AWAKE` 길이로 판정한다 — **5분 미만** 뒤척임(버림) · **5분~60분** 야간 각성(1회로 셈) · **60분 이상** 기상(세션 종료, 이후는 낮잠).

**60분 임계값을 빼면 `awake_count`가 구조적으로 항상 0이 된다.** "첫 기상까지"를 문자 그대로 구현하면 5분짜리 각성에서도 세션이 끊겨 5분 이상 각성이 존재할 수 없다. 값 범위는 정상이라 아무 제약에도 안 걸리고 다크서클 적중률만 무너진다.

두 임계값은 **`domain/sleep` 정규화 정책 상수다 — `ScoringPolicy`가 아니다.** 스코어링 파라미터가 아니라 수집 정규화 규칙이다. **앱이 보낸 세션 분할을 그대로 믿지 않는다** — 기기·OS별로 기준이 다르다.

### TODO 목록은 그날 첫 조회에 고정된다

`GET /api/v1/todo`가 그날 `daily_todo` 행이 없으면 추천 엔진을 돌려 만들고(`AVOID` 3 + `DO` 5, 최대 8행), 있으면 **다시 계산하지 않고** 그대로 반환한다. **조회 API가 행을 만드는 유일한 자리다.**

**재계산하면 같은 날 안에서 목록이 실제로 바뀐다.** 우선순위의 `verdictBonus`가 *가장 최근 검증*을 보는데, 그 값은 사용자가 그날 셀피를 찍는 순간 달라진다. 아침에 본 목록과 검증 후 본 목록이 다르면 이미 체크한 항목이 사라진 것처럼 보이고, REP-10이 "그날 무엇이 추천됐는가"를 재현할 수 없다.

- **점수 축은 예보 하나로 통일돼 있다.** 후보 추출도 우선순위도 `skin_forecast` 점수만 쓰고, `skin_measurement`에서 가져오는 것은 **판정 결과(`verdict`)뿐 — 실측 점수 자체는 계산에 안 들어간다**
- **`OVERESTIMATED`에만 보너스를 준다** — 예보 점수를 실제보다 높게 낸 것 = **피부 위험을 과소평가**한 것이다. `UNDERESTIMATED`는 위험 신호가 아니다. **두 축이 반대라 여기서 뒤집기 쉽다**
- **그날 예보가 `null`인 지표를 겨냥한 액션은 후보에서 뺀다.** 비교할 점수가 없다
- **"오늘 밤 체크리스트"는 화면 이름이지 카테고리가 아니다.** `DO` 상위 5개를 그렇게 부르는 것이고 `ActionCategory`는 `AVOID`/`DO` 2종 고정이다 — **`NIGHT_CHECK`를 되살리지 말 것**
- **`AVOID`도 `daily_todo`에 저장한다.** 체크 대상이 아닌데도 남기는 이유는 **REP가 "그날 무엇을 피하라고 했는지"를 되짚어야** 하기 때문이다. 그 대가로 `AVOID` 행이 `status = PENDING`을 갖게 되므로(컬럼이 `NOT NULL`), 응답에서 `null`로 가리고 `PATCH`는 `ACTION_NOT_CHECKABLE`로 막는다. **둘 중 하나만 있으면 조용히 뚫려 달성률이 오염된다** — REP-10 집계는 `category = 'DO'`로 먼저 거른다
- **차단은 `400`이다 — 조용한 `200`이 아니다.** 무시하면 그 요청을 보낸 앱 버그가 드러나지 않는다

**예보가 없는 날은 `200` + `NO_SLEEP_DATA` + 빈 배열이다 — 404가 아니다.** 수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면 일상적으로 발생한다. **후보가 0개인 날(`AVAILABLE` + 빈 배열)과 다른 상태**이므로 둘을 같은 status로 묶지 말 것.

**exp 적립과 회수는 대칭이라야 한다** — `PENDING → DONE`에 `+5`, `DONE → PENDING`에 `−5`. 그날 `DO`를 전부 채우면 `+30`이 더 붙고 **하나라도 풀리면 `−30`으로 되돌아간다.** **회수를 빼면 껐다 켜는 것만으로 무한 적립이 된다.** 판정이 "이번에 `DONE`이 됐는가"뿐이라 중복 호출만 막히기 때문이다. `TodoServiceTest.반복_토글로_적립되지_않는다`가 이 자리를 붙들고 있다.

**값은 `LevelPolicy.TODO_DONE_EXP`(`5`)·`TODO_ALL_DONE_EXP`(`30`)에서 온다** (prd.md §10.9). `todo` 쪽에 상수를 복사해 두지 말 것 — 한때 `TodoService.EXP_PER_DONE = 10`이 따로 있었고 **어긋난 동안 실제 지급은 `10`이었다.**

**TODO 적립만 `exp_grant`에 기록하지 않는다.** 되돌릴 수 있는 적립이라 `daily_todo.status`가 이미 지급 여부를 말한다. 나머지 4종(출석·연속 검증·수면 점수 2종)은 상태로 환원되지 않아 이력 행의 유니크로 하루 1회를 막는다 (erd.md §3.10).

**액션 마스터는 앱이 채우지 않는다.** `src/main/resources/db/seed/action_master.sql` 24행을 **사람이 한 번 실행한다** (workflow.md §8). 비어 있으면 TODO 탭이 빈 배열로 나가는데 **에러가 아니라 로그에도 안 남는다.** 실행할 때 `--default-character-set=utf8mb4`가 없으면 한국어가 `???`로 들어가고 INSERT는 성공한다.

### 리포트 기간은 `baseDate`에서 역산한다 — 가입일이 아니다

```
주간   baseDate − 6  ~ baseDate     (7일)
월간   baseDate − 27 ~ baseDate     (28일 = 7일 × 4주, W1 최과거 ~ W4 최근)
```

**월간은 달력의 달이 아니다.** `yearMonth`를 받지 않는다 — 네 API 전부 `baseDate` 하나만 받는다. 가입일이나 달력에 앵커를 두면 주 경계가 사용자마다·달마다 달라지고 **앱이 날짜를 옮겨 가며 조회할 수 없다.**

**가입일(`users.created_at`)은 `INSUFFICIENT_DATA` 판정에만 쓴다** — 가입 당일을 1일차로 세어 7일·28일 미만이면 신규 사용자다. **"가입한 지 오래됐지만 그 주에 안 잤다"는 여기 해당하지 않는다**(정상 응답 + 그 날짜만 `null`) — 데이터 품질 문제와 신규 사용자 문제를 같은 상태로 묶지 말 것.

- **주간·월간의 `status`는 `QueryStatus`가 아니라 `ReportPeriodStatus`(`FULL`·`INSUFFICIENT_DATA`)다.** 일간은 두 섹션이 독립적으로 비므로 섹션마다 `QueryStatus`를 갖는다 — **응답 전체를 하나로 감싸지 말 것**
- **기록 없는 날은 배열에서 빼지 않고 점수만 `null`이다.** 빼면 그래프 x축이 주마다 5칸·7칸이 된다. **평균에서는 `null`을 분모에서 뺀다** — 0으로 채우면 "안 잔 날"이 "최악으로 잔 날"이 된다
- **월간 전체 평균은 주 평균 4개의 평균이 아니라 28일을 한 번에 평균낸 값이다** (주별 결측 수가 다르면 갈린다)
- **적중률·검증일수는 넣지 않는다** (2026-08-15, 화면에 없음). 셀피 실측 조회는 상관 강도 때문에 남아 있을 뿐이다

**REP-07 상관 강도는 예보가 아니라 실측(셀피)과 비교한다.** 예보값은 이 피처들로 만든 값이라 **수면으로 만든 값이 수면과 관련 있다는 순환 논증**이 된다. **세션과 검증이 둘 다 있는 날짜만 표본**이고, 상관은 **정규화된 부분점수가 아니라 원본값**끼리 낸다(정규화 곡선이 상관계수에 섞여 든다). 비율의 분모는 여기서도 `deep + rem + core`다.

**수면 점수 계산기가 두 개다 — 세 번째를 만들지 말 것.** `sleep/SleepScoreCalculator`(exp 적립용)와 `report/DailySleepScoreCalculator`(리포트용)가 같은 산식(prd.md §10.8)을 각자 갖고 있다. **지금은 같은 숫자를 내지만 한쪽만 바뀌면 exp로 지급한 점수와 리포트가 보여준 점수가 갈린다.** 정리한다면 리포트 쪽이 `SleepScoreCalculator.calculate(featureScores)`를 호출하는 방향이다.

### ⚠️ DB에 `users` 외래키가 없다 — CASCADE가 걸리지 않는다

자식 테이블이 `userId`를 **연관관계가 아니라 단순 `Long` 컬럼**으로 들고 있어(architecture.md §4) Hibernate가 FK 제약을 만들지 않았다. **erd.md가 오래 "모든 FK에 ON DELETE CASCADE를 건다"고 적고 있었지만 사실이 아니다** (2026-08-14 확인, erd.md §5에 정정).

진짜 FK는 둘뿐이다 — `sleep_stage_segment → sleep_session`, `daily_todo → action_master`. **`users`를 가리키는 것은 하나도 없다.**

- **MY-04 전체 삭제는 `UserService.delete`가 자식을 손으로 지운다** (현재 **8개** — `exp_grant` 포함). `users` 행만 지우면 고아 행이 남고, **조회에 잡히지 않아 알아채기 어렵다** — 같은 `userId`가 재사용되면 남의 이력이 새 사용자에게 붙는다. **`exp_grant`가 특히 조용하다** — 남은 행의 유니크 `(user_id, base_date, reason)`에 새 사용자가 걸려 **출석 보상을 못 받는데 에러도 로그도 남지 않는다**
- **`sleep_stage_segment`를 `sleep_session`보다 먼저 지운다.** 유일하게 진짜 FK가 있어 순서를 바꾸면 제약 위반이다
- **`userId` 컬럼을 가진 테이블을 새로 만들면 그 삭제 목록에 한 줄을 추가한다.** 빠뜨려도 컴파일도 테스트도 통과한다

### 서버가 하지 않는 것

HealthKit 직접 접근 · 배치 스케줄러 · 푸시 발송 · 인증/세션.
수면 데이터는 **앱이 읽어서 서버로 전달**한다. 서버 스케줄러는 존재하지 않는다.

### 같은 수면 데이터는 재처리하지 않는다

앱은 시작될 때마다 업로드하므로 새 수면 데이터가 생기기 전까지 **같은 세션이 계속 온다.**
정규화 후 페이로드 해시를 비교해 **저장·스코어링을 시작하기 전에 중단**한다. upsert로 덮어쓰지 않는다.

```
해시 동일 → 아무것도 하지 않고 기존 예보 반환 (processed=false)
해시 다름 → 그날 셀피 검증을 마쳤으면 갱신하지 않음 (processed=false)
            검증 전이면 갱신 + 재산출        (processed=true)
```

**검증을 마친 날의 예보는 절대 바뀌지 않는다.** 이 규칙 덕분에 예보 이력 테이블이 필요 없다.

재산출하면 **오늘자 예보값이 바뀌어, 이미 마친 셀피 검증의 대조 기준이 사후에 바뀐다.** 적중률이 훼손되고 개인 가중치가 중복 학습된다. 성능이 아니라 정확성 문제다.

### 응답은 항상 래퍼로 감싼다

```java
ApiResponse.success(data)   // { success, data, error }
```

에러는 `BusinessException(ErrorCode)` → `@RestControllerAdvice`가 처리.
**빈 상태(수면 데이터 없음, 검증 이력 없음, 기록 부족)는 정상 흐름**이다.
조회 API는 **200 + `{status, message, 페이로드}`**로 내보낸다. 4xx가 아니다 — 신규 사용자에게 일상적으로 발생하므로 에러로 취급하면 진짜 문제가 묻힌다.
동작 API(셀피 검증 등)에서 필요한 것이 없으면 그건 진짜 에러이므로 `ErrorCode`로 4xx를 낸다.

### DTO는 record, Entity는 밖으로 안 나간다

DTO에 Lombok 금지(record가 대신). Entity에 `@Setter`·`@Data` 금지.
Entity → DTO 변환은 DTO의 정적 팩토리 메서드로. `HealthCheckResponse.up()`이 기준 패턴.

### 한국어로 쓰는 것

주석 · Swagger 설명(`@Tag`/`@Operation`/`@Schema`) · 에러 메시지 · 커밋 메시지.
클래스·메서드·변수명은 영어.

### 도메인 용어 고정

`forecast`(예보) · `measurement`(실측) · `verification`(검증) · `weight`(가중치) · `baseDate`(기준일)

## 인증

**없다.** 테스트 유저를 DB에 직접 주입한다. Spring Security를 추가하지 않는다.
단, Service는 항상 `userId`를 파라미터로 받는다 — 나중에 JWT를 붙여도 Controller만 바뀌게.

**`userId`는 `X-User-Id` 헤더로 받는다** (conventions.md §8). 경로 변수나 쿼리 파라미터가 아니다 — JWT를 붙일 때 **헤더를 읽던 자리 한 곳만** 바뀐다.

**컨트롤러는 그 헤더를 직접 읽지 않는다.** `@CurrentUserId Long userId`로 받고, 헤더를 읽는 코드는 `CurrentUserIdArgumentResolver` 한 곳뿐이다 — JWT를 붙이면 그 안쪽만 바뀌고 컨트롤러 시그니처는 전부 그대로다. `@RequestHeader`로 직접 받지 말 것.

## API 공통 규약 (conventions.md §8)

- **`X-User-Id` 헤더** — 모든 API 공통
- **`baseDate` 쿼리 파라미터** — 날짜가 필요한 API는 전부 받는다. **조회 API만의 규칙이 아니다** — 동작 API인 `POST /skin/selfie`도 어느 날짜의 예보와 대조할지 알아야 해서 받는다(멀티파트라도 폼 필드가 아니라 쿼리). **서버는 "오늘"을 모른다** (`users`에 `time_zone`이 없다). 서버 시각으로 계산하면 한국 시간 오전 9시 이전에 하루 밀린다
- **모든 시각은 ISO 8601 오프셋 포함** — 오프셋이 없으면 `sleepDate`가 밀리고 예보·검증 조인이 전부 어긋난다
- 경로에 동사를 넣지 않는다. 상태 변경은 `PATCH /todo/{id}` + 본문 `{status}`

**`POST /api/v1/sleep/sessions`는 앱이 단계 구간 배열만 보내고 서버가 집계를 전부 계산한다** (architecture.md §3.1). 서버가 세션을 첫 기상에서 자르므로 앱이 보고한 총합은 쓸 수 없다. **앱은 `UNSPECIFIED`를 `CORE`로 바꿔 보내면 안 된다** — 비율 분모가 오염된다.

## 문서

작업에 필요한 것만 읽는다.

| 문서 | 읽어야 할 때 |
|---|---|
| [docs/prd.md](docs/prd.md) | 기능 요구사항 확인, 기능 ID(HOME-03 등) 조회, 미결정 사항 확인, 구현 우선순위, **확정된 정책값(§10 등급 컷오프·판정 구간)** |
| [docs/architecture.md](docs/architecture.md) | 새 도메인 설계, 핵심 플로우 파악, 외부 연동(OpenAI) 구현, RDS 구성 |
| [docs/erd.md](docs/erd.md) | **엔티티 작성 직전** — 테이블 10개의 컬럼과 근거, 일부러 뺀 컬럼, 유니크 제약 |
| [docs/api.md](docs/api.md) | **엔드포인트 작업 직전** — 경로·요청·응답의 **유일한 출처**. 도메인별 API 19개, `POST /sleep/sessions` 상세 규격, 구현 순서와 남은 정리 작업, **MVP에서 만들지 않는 것** |
| [docs/conventions.md](docs/conventions.md) | 코드 작성 직전 — 응답 포맷, 에러 코드, DTO/Entity 규칙, 경로 명명 규칙, Swagger |
| [docs/workflow.md](docs/workflow.md) | 브랜치 생성, PR, 팀 분담, 빌드, **배포·운영 DB 설정(§7·§8)** |

기능 ID는 `ONB-01~05` / `HOME-01~09` / `TODO-01~07` / `REP-01~12` / `MY-01~05`.
원본 기획: Notion 「기능명세서」 (prd.md §11에 링크)

## 현재 상태

**도메인 API 19개 중 18개가 끝났다.** 남은 하나(종합 리포트)는 구현이 아니라 **정책이 미정이라 보류**다.

**구현됨**
- **엔티티 10개 + Repository 10개** — erd.md의 전부 (`exp_grant` 포함)
- **테스트 유저 시딩** — `TestUserSeeder`(`CommandLineRunner`). 멱등하며 운영 포함 전 환경에서 돈다. 사용자가 한 명이라도 있으면 건너뛴다
- `GET /api/v1/health` 헬스체크
- **`POST /api/v1/users/me/consents`** (ONB-02) · **`PATCH /api/v1/users/me/onboarding`** (ONB-05)
- **수면 정규화·집계 코어** — `SleepSessionNormalizer`·`SleepNormalizationPolicy`·`BedtimeRegularityCalculator`
- **예보 스코어링 코어** — `ScoringPolicy`·`SkinScoringEngine` (§10.3~§10.7 확정값 전부)
- **`POST /api/v1/sleep/sessions`** (수면 업로드 + 예보 산출) · **`GET /api/v1/sleep/interpretation`** (HOME-02)
- **`GET /api/v1/skin/forecast`** (HOME-03) · **`POST /api/v1/skin/selfie`** (HOME-06→07→08)
- **`GET /api/v1/skin/verification/summary`** (HOME-09) · **`GET /api/v1/skin/model`** (REP-12) — **`skin` 도메인 완료**
- **`GET /api/v1/todo`** · **`PATCH /api/v1/todo/{id}`** (TODO-02~05) — **`todo` 도메인 완료.** 추천 엔진(`TodoScoringPolicy`) + 액션 마스터 시드 24행
- **`GET /api/v1/users/me`**(ONB-01+MY-01) · **`GET /api/v1/users/me/data-status`**(MY-02) · **`DELETE /api/v1/users/me`**(MY-04) · **`POST /api/v1/users/me/attendance`**(HOME-04 출석) — **`user`·`game` 도메인 완료**
- **`GET /api/v1/report/daily`**(REP-02·04·05) · **`/daily/timeline`**(REP-03) · **`/weekly`**(REP-06·07) · **`/monthly`**(REP-08) — **종합(REP-09~11)만 보류**
- **게이미피케이션(HOME-04)** — `LevelPolicy`·`ExpService`·`AttendanceService`·`AttendanceWeekCalculator`. 적립 6종 중 **4종이 동작한다**(아래 ⚠️). **월~일 출석 도장판은 체크인 응답에 실려 있다** — 새 테이블 없이 `exp_grant`의 `ATTENDANCE` 행에서 나오고, **엔드포인트를 늘리지 않았다**(api.md §5)
- **개인 가중치 학습** — `SkinModelService`. 첫 검증에 7행을 `1.0`으로 만들고, 그날 참여한 피처만 보정한다
- **OpenAI Vision 연동** — `global/infra/openai/`의 `SkinVisionClient`(인터페이스) + `OpenAiSkinVisionClient`(Responses API + Structured Outputs). **점수 방향은 2026-08-10 실호출로 확인됨**
- `global/` — `ApiResponse`·`ErrorResponse`·`ErrorCode`·`BusinessException`·`GlobalExceptionHandler`·`BaseTimeEntity`·`BaseCreatedEntity`·`JpaConfig`·`SwaggerConfig`·`CorsConfig`·`WebMvcConfig`·`OpenAiConfig`·`CurrentUserId`(+`CurrentUserIdArgumentResolver`)·`QueryStatus`
- 인프라 — MySQL + JPA + validation 의존성, Docker/Compose, GitHub Actions CI/CD

**미도입**: **종합 리포트(`GET /report/overall`, REP-09~11) 하나뿐이다.** 핵심 루프(수면 → 예보 → 처방 → 검증 → 학습)는 닫혔고 리포트·게이미피케이션까지 얹혔다.

### 코드와 확정 명세가 어긋났던 곳 — 2026-08-15에 정리됐다

**한동안 넷이 어긋나 있었고 전부 조용히 틀렸다.** 값 범위가 정상이라 아무 제약에도 안 걸렸다. **되살아나지 않게 지키는 자리를 함께 적어 둔다.**

| 무엇 | 어긋났던 값 | 지금 | 지키는 테스트 |
|---|---|---|---|
| `DO` 완료 exp | `10` | `LevelPolicy.TODO_DONE_EXP`(`5`)를 직접 쓴다 | `TodoServiceTest.완료하면_적립된다` |
| 전부 완료 보너스 | 없었다 | `+30`/`−30` | `TodoServiceTest.전체_완료_보너스` |
| `PATCH /todo/{id}` 응답 | `expGained`·`totalExp` | `exp` 객체 + `allCompleted` | `TodoControllerTest`(옛 필드 부재까지 단언) |
| 상관 강도 지표명 | `"다크서클"` | `"다크서클 회복"` | `CorrelationCalculatorTest` |

- **`TodoService`에 적립량 상수를 다시 만들지 말 것.** `LevelPolicy`가 유일한 출처다 — 도메인 쪽에 사본을 두면 정확히 같은 방식으로 다시 갈린다
- **exp 회수 대칭은 보너스에도 그대로 적용된다.** `+30`을 넣으면 `−30`도 같이 넣는다 — 빼면 **마지막 항목 하나를 껐다 켜는 것만으로** 무한 적립이 된다
- **`allCompleted`는 전이가 아니라 현재 상태다.** 전이는 `exp.reasons`의 `TODO_ALL_DONE`이 말한다 — 두 곳이 같은 사실을 말하면 어긋날 자리가 생긴다
- **`"다크서클"`이라고만 쓰면 방향이 뒤집혀 읽힌다.** 점수는 "심한 정도"가 아니라 "회복된 정도"다

**검산식이 이제 성립한다** (erd.md §3.10) — `SUM(exp_grant.amount) + (DO 완료 수 × 5) + (전체 완료일 수 × 30) = users.exp`. **적립 지점을 새로 만들면 이 식이 여전히 맞는지 확인한다.**

**모든 도메인이 같은 테스트 구성을 갖췄다** — 정책 클래스(DB 없이 도는 순수 로직) · Service · Controller(`@WebMvcTest`) · `*ApiDocsTest`. `todo`가 기준이고(`TodoScoringPolicyTest`·`TodoServiceTest`·`TodoControllerTest`·`TodoApiDocsTest`), `report`(`CorrelationPolicyTest`·`CorrelationCalculatorTest`·`DailySleepScoreCalculatorTest`·서비스 3종·`ReportControllerTest`·`ReportApiDocsTest`)와 `game`(`LevelPolicyTest`·`ExpServiceTest`·`AttendanceServiceTest`·`GameControllerTest`·`GameApiDocsTest`)도 같다. **새 도메인은 이 네 자리를 채운다.**

**연속 검증 횟수는 `VerificationStreakCalculator` 한 곳에서만 계산한다.** HOME-09와 MY-01이 같은 숫자를 써야 하고(prd.md §4.2), 각자 계산하면 두 화면이 어긋난다. **두 API가 실제로 이 컴포넌트를 호출하고 있다 — 세 번째가 생겨도 계산을 다시 적지 말 것.**

**`OPENAI_API_KEY`가 없어도 앱은 뜬다.** 셀피 분석만 502로 실패하고 기동 시 WARN이 남는다 — 키 없는 팀원도 수면·예보 쪽을 개발할 수 있게 한 것이다. 운영에서 키가 빠지는 것은 CD 선검사가 경고한다.

**LLM 호출은 트랜잭션 밖이다.** `SelfieAnalysisService`(선검사 + Vision 호출) → `SkinVerificationService`(`@Transactional` 저장·대조)로 빈을 나눈 이유가 그것이다. 30초짜리 외부 호출이 DB 커넥션을 잡으면 셀피가 몰릴 때 **수면 업로드까지 함께 막힌다.**

### 굳어진 패턴 — 이후 API가 그대로 복제한다

`user` 도메인이 기준이다(엔드포인트 5개 전부). 새 도메인을 시작하기 전에 그 코드를 먼저 본다.

| 패턴 | 기준 구현 |
|---|---|
| `userId`는 `@CurrentUserId Long userId`로 받는다 — 컨트롤러가 헤더를 직접 읽지 않는다 | `CurrentUserIdArgumentResolver` |
| 사용자 존재 검증은 **Service**가 `USER_NOT_FOUND`로 한다 — `global`이 `domain`을 참조할 수 없어 리졸버는 DB를 보지 않는다 | `ConsentService`·`UserService` |
| Swagger 문서는 `{도메인}ControllerSpec` 인터페이스에 둔다 | `UserControllerSpec` |
| 응답 래퍼는 비어 있는 쪽을 직렬화하지 않는다 (성공엔 `error` 키가, 실패엔 `data` 키가 없다) | `ApiResponse` |
| 에러 예시는 `ErrorCode`에서 생성해 `ref`로 참조한다 — 손으로 적지 않는다 | `SwaggerConfig` |
| 문서 자체를 테스트로 지킨다 — 새 API를 추가하면 여기에도 추가 | `SwaggerConfigTest` |

**함정과 근거는 conventions.md §8·§11에 전부 적혀 있다.** 전부 한 번씩 실제로 어긋났던 자리이고, 값 범위는 정상이라 아무 제약에도 안 걸린다.

**약관 버전은 `ConsentPolicy.CURRENT_TERMS_VERSION = "1.0"`으로 시작했다.** 서버 상수이며 클라이언트가 보내지 않는다. P4(약관 원문)가 확정되면 **이 값만 올리면 된다** — 그 다음 동의부터 새 이력이 append된다.

**로컬은 MySQL 컨테이너, 운영은 RDS다.** 로컬 스택은 `docker-compose.local.yml`이고 `.env`의 `COMPOSE_FILE`이 이 파일을 가리킨다. 운영은 Compose를 쓰지 않는다 — CD가 EC2에서 `docker run --env-file`로 띄운다.

⚠️ **로컬 `.env`의 `DB_HOST`에 RDS 주소를 넣지 말 것.** `ddl-auto: update`라서 앱이 뜨는 순간 작업 중인 엔티티가 운영 스키마에 반영되고, `update`는 컬럼을 지우지 않으므로 되돌릴 수 없다. 로컬에서 쓰는 `docker compose down -v`에 대응하는 조치가 RDS에는 없다 (workflow.md §1).

**테스트**: `./gradlew test`는 DB 없이 돈다 (`test` 프로파일이 H2 사용). Controller는 `@WebMvcTest`. **CI도 DB 서비스를 띄우지 않는다** — 유니크 제약처럼 실제 MySQL이 필요한 검증이 생기면 그때 다시 붙인다.

**스키마는 엔티티에서 만든다.** `ddl-auto: update`로 결정됐고 DDL 스크립트를 따로 두지 않는다 (근거는 `application.yml` 주석). `update`는 컬럼 추가만 반영하므로 **엔티티를 파괴적으로 바꿨다면 DB를 지우고 다시 만든다** — `docker compose down -v && docker compose up -d mysql`.

다음 착수 순서 (api.md §4 · prd.md §8) — **1~4단계가 전부 끝났다.**

1. ~~엔티티 + Repository~~ · ~~테스트 유저 시딩~~ · ~~동의 저장(ONB-02)~~ · ~~온보딩 완료(ONB-05)~~ — 완료
2. ~~수면 세션 수신 `POST /api/v1/sleep/sessions`~~ — 완료
3. ~~피부 예보 산출 (HOME-03)~~ · ~~수면 통역 카드 (HOME-02)~~ — 완료
4. ~~셀피 분석·검증·학습 `POST /api/v1/skin/selfie` (HOME-06→07→08)~~ — 완료
5. ~~TODO 추천 엔진·리스트 (TODO-02~05)~~ · ~~배너(HOME-09)·내 모델(REP-12)~~ — 완료
6. ~~일간 리포트·타임라인 (REP-02~05)~~ — 완료
7. ~~게이미피케이션 (HOME-04)~~ — 완료. `todo` 쪽 적립값 교체까지 끝났다 (2026-08-15)
8. ~~주간(REP-06/07) · 월간(REP-08)~~ — 완료. **종합(REP-09~11)은 보류** — 정책 미정(prd.md §7 L6·L9)

**코드로 할 일은 남지 않았다.** 남은 셋은 전부 사람이 정하거나 실행하는 것이다 — ① 종합 리포트 정책 결정(prd.md §7 L6·L9) ② 임시값 확정(상관 강도, §9.2 L7) ③ 약관 원문(P4). 그 밖에 **dev → main 배포**가 밀려 있다.

**P5(액션 마스터 데이터)는 해소됐다** — 24행이 시드 SQL로 들어왔다.

**⚠️ HOME-08은 §10.7의 문구 하나를 의도적으로 따르지 않았다.** 명세는 "`s(f)`와 **지표점수**를 검증 시점에 다시 계산한다"고 적었지만, 구현은 **지표점수를 저장된 예보값으로 쓴다.** `e = 실측 − 예보`의 예보와 `s(f) − 지표점수`의 지표점수는 **같은 항**인데 한쪽만 재계산하면 두 값이 갈린다(`67.5` vs `68`). 근거는 `SkinModelService` javadoc에 있다.

## 스코어링 명세 (prd.md §10 — 확정)

**전부 `domain/skin/ScoringPolicy` 한 곳에 모은다. 서비스 로직에 하드코딩하지 않는다.** 참조하는 코드에는 `// 확정값 (PRD §10.4)` 식으로 출처를 남긴다.

```
지표점수(m) = Σ_f [ w'(m,f) × s(f) ]
w'(m,f)    = w일반 × w개인 / Σ(...)    ← 지표 내 합 = 1로 재정규화
s(f)       = 피처의 0~100 부분점수 (구간선형)
```

**재정규화가 이 설계의 핵심이다.** 없으면 개인 가중치가 점수 전체를 같은 방향으로 밀 뿐 상대 비중을 학습하지 못하고, 결측 처리도 분기가 필요해진다.

- **초기(일반) 가중치는 지표 내 균등** — `DARK_CIRCLE`·`BARRIER` 각 `0.5`, `COMPLEXION` 각 `1/3`. 근거는 가중치가 아니라 정규화 기준에 실려 있다 (§10.5 + §11.1 문헌)
- **`DEEP_SLEEP`·`REM_SLEEP`은 분이 아니라 비율(%)로 정규화한다. 분모는 `총 수면`이 아니라 `deep + rem + core`다** (§10.5). 저장은 분 그대로. 여기서 단위나 분모를 헷갈리면 장벽 점수만 조용히 틀린다
- **결측(HRV·안정시 심박)은 그 항을 빼고 재정규화한다.** 대입하지 않는다 — 존재하지 않은 값이 개인 가중치 학습에 반영된다. **그 밤의 검증으로 결측 피처의 가중치를 갱신하지 않는다**
- **취침 규칙성은 최근 7일 `sleep_onset_time` 표준편차, 3일 미만이면 결측과 동일 처리.** `COMPLEXION` 피처가 전부 없으면 그날 **혈색만** 빈 상태로 응답하고 나머지 두 지표는 정상 발급한다
- **각성 총 시간은 피처가 아니다 — 표시 전용.** 각성 횟수와 같은 5분 임계값·같은 구간 집합에서 나와 중복 상관이 강하다. 배정을 미룬 게 아니라 넣지 않기로 정한 것이니 **추가하지 말 것**
- **`REM_SLEEP` → `BARRIER`는 직접 근거가 약하다는 것이 문서에 명시돼 있다** (§10.3). 학습 가능성 확보를 위해 포함한 것이며, 리포트 문구에서 이 한계를 과장해 설명하지 않는다

### 개인 가중치 학습 (HOME-08 — §10.7)

```
Δw(f) = 0.5 × (실측−예보)/100 × (s(f)−지표점수)/100
w(f)  = clamp(w(f) + Δw(f), 0.5, 2.0)
```

**오차를 부분점수 편차로 배분한다** — 실측이 나빴으면 부분점수가 평균보다 낮았던 피처의 비중이 올라간다. 지표점수가 가중평균이라 `∂지표점수/∂w(f) ∝ s(f)−지표점수`인 데서 그대로 나온다.

- **그날 스코어링에 참여한 피처만 갱신한다.** 결측 밤의 오차를 `HRV` 탓으로 돌리지 않는다
- **첫 검증부터 즉시 예보에 반영한다.** 클램프가 폭주를 막으므로 최소 검증 횟수를 두지 않는다
- **첫 검증 때 7행을 전부 `1.0`으로 만든다.** 참여한 피처에만 보정값을 적용한다
- **두 피처의 부분점수가 같은 날은 `Δw = 0`이다.** 오차를 배분할 근거가 없는 날이므로 학습하지 않는 게 맞다 — 버그가 아니다
- **계통 편차(늘 N점씩 높음)는 잡지 못한다.** 절편 항을 두지 않기로 했고 한계로 문서화했다. `personal_weight`에 컬럼을 추가하지 말 것
- 부분점수는 검증 시점에 `sleep_session`에서 다시 계산한다. 저장하지 않는다

등급 컷오프는 `25`·`50`·`75`로 25점씩 4등분(위험/주의/보통/안정), 판정은 `예보−실측` 기준 ±5 적중 / ±6~15 근접 / ±16~ 과소·과대다 (§10.1·§10.2). **`과소예측`은 점수 축 기준** — 점수를 낮게 예측한 것이고, 그건 피부 위험을 과대평가한 것이다. 두 축이 반대라 문구에서 뒤집히기 쉽다.

스코어링 피처는 **7종**이다 (§10.3). `sleep_session`에 저장하는 10항목과 다르다 — 코어 수면·기상 시각·각성 총 시간은 저장하되 피처가 아니고, `BEDTIME_REGULARITY`는 컬럼이 아니라 `sleep_onset_time`에서 파생된다. 매핑은 `personal_weight` 7행 = §10.3의 7쌍.

## 임시값 주의

§10으로 확정되지 않은 값들은 아직 임시다 — **상관 강도 구간·표본 하한(L7) · 트리아지 임계값(L6)** (prd.md §9.2). L8(신뢰도 등급)은 서버 대상에서 빠졌다.

**리포트 쪽 임시값은 `domain/report/CorrelationPolicy`에 모여 있다** — 강도 구간 `0.7 / 0.4 / 0.2`와 표본 하한 `5`. **`ScoringPolicy`가 아니다**: 예보 스코어링은 정규화된 부분점수를, 상관 강도는 저장된 원본값을 다룬다. 확정되면 **이 클래스의 상수만 바꾸고 계산 로직은 손대지 않는다.**

**수면 목표값(B6)은 MVP에서 빠졌다** (2026-08-14). **`ScoringPolicy`에 목표값 상수를 두지 말 것** — 값이 있으면 어딘가에서 쓰이게 된다. 리포트는 목표 달성 판정 대신 **관측값을 그대로 보여준다**(prd.md §4.4). 목표를 정하는 화면이 없는데 서버가 권장치를 정하면, 근거 없는 숫자가 "목표 달성"이라는 판정문으로 사용자에게 나간다.

**TODO 쪽 두 값도 임시다** — **절단 개수(`AVOID` 3 · `DO` 5) · `verdictBonus` 배율(`× 10`)**. 구현하며 정해진 값이고 §10에 없다. **`domain/skin/ScoringPolicy`가 아니라 `TodoScoringPolicy`·`TodoService` 상수로 둔다** — 예보 스코어링과 추천 정렬은 다른 축이다.

**exp 관련 값은 임시가 아니라 확정이다** (prd.md §10.9). 다만 **셋 중 어디도 아닌 `domain/game/LevelPolicy`에 모은다** — 적립 트리거가 `user`·`sleep`·`skin`·`todo` 네 도메인에 흩어져 있어, 어느 한 도메인의 정책 클래스에 두면 나머지 셋이 그걸 참조하게 된다.

## 확정이 필요한 것

**블로커가 하나도 남지 않았다.** B1·B2·B3·B7이 확정됐고, B4는 고지 문구가 답에 의존하지 않게 만들어 해소, B5는 MVP 전제로 보류, **B6(수면 목표값)은 2026-08-14에 MVP에서 제외**됐다. **구현이 막힌 곳도 없다** — 남은 결정은 아래 종합 리포트뿐이고, 그것도 4단계라 다른 작업을 막지 않는다.

**HOME-04(게이미피케이션)도 2026-08-14에 확정됐다** — 레벨 컷오프·적립량·수면 점수 정의까지 전부 정해졌고(prd.md §10.8·§10.9), S5(캐릭터 클릭 동작)는 **서버가 캐릭터를 모르므로** 클라이언트 영역으로 정리됐다.

**종합 리포트(REP-09~11)가 유일하게 막혀 있고, 막힌 것이 둘이다.**

1. **발동 조건** (L6) — "수면 목표는 달성했는데 특정 피부 지표만 정체"에서 앞 절반이 사라졌다. 수면 쪽 근거 없이 두면 **잠을 못 잔 사람에게도 "클리닉에 가보라"고 말하게 된다**
2. **화면이 요구하는 항목을 현재 지표로 만들 수 없다** (L9) — REP-10의 "클리닉 필요" 셋(색소침착·여드름 흉터·구조적 노화)이 전부 지표 3종 밖이다. 색소침착을 **셀피 실측 전용 지표**로 되살리는 안이 논의됐는데, **`report`만 고쳐서 되는 일이 아니다** — `skin_measurement` 컬럼 · 엔티티 · DTO · Vision 프롬프트 · 구조화 출력 스키마까지 함께 바뀐다. **`skin` 담당과 합의 없이 넣지 말 것.** 예보 3종에 섞으면 "예보와 실측이 같은 세트"라는 원칙 위반이다

값을 임의로 정하지 말고 사용자에게 확인할 것. 전체 미결정 목록은 prd.md §7 (B=블로커, L=로직, E=빈상태, S=화면, P=정책).

**사람이 움직여야 하는 것은 둘이다** — **약관 원문(P4)**(`consent_history.terms_version`의 출처)과 **위 종합 리포트 결정**. 그 밖에 임시값 확정(L7 상관 강도)이 있으나 개발을 막지 않는다. (액션 마스터 데이터 P5는 해소됐다.)
