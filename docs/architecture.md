# 아키텍처

sleep2skin 백엔드의 구조와 설계 결정. 제품 요구사항은 [prd.md](prd.md) 참조.

---

## 1. 시스템 구성

```
┌──────────────┐         ┌─────────────────────────┐         ┌──────────────┐
│  iOS 앱      │  HTTPS  │  sleep2skin_be          │  이미지  │  OpenAI      │
│              │────────▶│  Spring Boot 4.1 / EC2  │────────▶│  Vision API  │
│  HealthKit   │ 멀티파트 │                         │         └──────────────┘
│  카메라       │◀────────│  Docker 컨테이너         │
└──────────────┘         └────────────┬────────────┘
                                      │ JDBC
                                      ▼
                              ┌────────────────┐
                              │  AWS RDS       │
                              │  (MySQL)       │
                              └────────────────┘
```

**셀피는 어디에도 파일로 착지하지 않는다.** 멀티파트로 받은 바이트를 그대로 OpenAI Vision에 실어 보내고, RDS에는 0~100 점수 3개만 남는다. 오브젝트 스토리지(S3)를 경유하지 않는다 — §5 참조.

### 서버가 하지 않는 것

경계를 명확히 하는 편이 설계를 단순하게 만든다.

| 안 하는 것 | 대신 누가 | 이유 |
|---|---|---|
| HealthKit 직접 접근 | 앱이 읽어 전달 | 서버는 애플 건강 데이터에 접근할 수 없다 |
| 배치 스케줄러 | 앱 시작 시 업로드 | 위와 같은 이유. 배치 인프라가 통째로 불필요해진다 |
| 푸시 알림 발송 | 앱 로컬 알림 | 취침·아침 알림은 로컬 알림으로 충분하다 |
| 셀피 이미지 보관 | — | 제품 정체성. §5 참조 |
| 인증·세션 관리 | — | 해커톤 범위에서 로그인 없음. §6 참조 |

---

## 2. 패키지 구조

```
com.allday.sleep2skin_be
├── Sleep2skinBeApplication.java
│
├── global/                     공통 인프라 — 도메인 로직 없음
│   ├── config/
│   │   ├── SwaggerConfig       (구현 완료)
│   │   ├── JpaConfig           @EnableJpaAuditing
│   │   └── OpenAiConfig        HTTP 클라이언트 빈 + 타임아웃
│   ├── response/
│   │   ├── ApiResponse         { success, data, error }
│   │   └── ErrorResponse       { code, message }
│   ├── exception/
│   │   ├── ErrorCode           enum — 도메인별 구역으로 나눠 관리
│   │   ├── BusinessException
│   │   └── GlobalExceptionHandler
│   ├── entity/
│   │   ├── BaseTimeEntity      createdAt + updatedAt
│   │   └── BaseCreatedEntity   createdAt만 (append-only 이력용)
│   └── infra/                  외부 연동 — 교체 가능하게 감싼다
│       └── openai/             SkinVisionClient 인터페이스 + 구현체
│
└── domain/                     비즈니스 도메인
    ├── user/                   사용자 · 동의 이력 · 설정
    ├── sleep/                  수면 세션 수신 · 정규화 · 집계
    ├── skin/                   피부 예보 · 셀피 실측 · 검증 · 개인 모델
    ├── todo/                   추천 엔진 · TODO 리스트
    ├── report/                 일간 · 타임라인 · 주간 · 월간 (종합은 보류)
    ├── game/                   레벨 · 경험치 적립 · 출석 (HOME-04)
    └── health/                 헬스체크 (구현 완료)
```

`global`과 `domain` 두 갈래로 나눠, 최상위만 봐도 **공통 인프라와 비즈니스 로직의 경계**가 보이게 한다. 의존 방향은 `domain → global` 한쪽뿐이다. **`global`이 `domain`을 참조하면 안 된다.**

### 도메인 패키지 내부 구조

모든 도메인 패키지는 같은 형태를 따른다. `health` 도메인이 이미 이 패턴의 축소판이다.

```
domain/skin/
├── SkinController.java             HTTP 진입점. 검증과 응답 변환만
├── SkinForecastService.java        예보 산출 (HOME-03)
├── SkinVerificationService.java    예보 vs 실측 검증 (HOME-07)
├── SkinModelService.java           개인 가중치 학습 (HOME-08)
├── repository/
│   ├── SkinForecastRepository.java
│   └── SkinMeasurementRepository.java
├── entity/
│   ├── SkinForecast.java
│   ├── SkinMeasurement.java
│   └── SkinMetric.java             enum
└── dto/
    ├── request/                    SelfieAnalyzeRequest ...
    └── response/                   SkinForecastResponse ...
```

**계층 3개 + DTO 계층**:

| 계층 | 책임 | 금지 |
|---|---|---|
| Controller | 요청 매핑, 검증(`@Valid`), 응답 변환, Swagger 문서화 | 비즈니스 로직, Repository 직접 호출 |
| Service | 비즈니스 로직, 트랜잭션 경계, 도메인 간 조합 | HTTP 관심사(HttpServletRequest 등) |
| Repository | 데이터 접근 (Spring Data JPA) | 비즈니스 판단 |
| DTO | 계층 간 데이터 전달. 전부 `record` | 로직 (정적 팩토리 메서드는 허용) |

**Entity는 절대 Controller 밖으로 나가지 않는다.** 응답은 항상 DTO로 변환한다.

### 화면 도메인이 아니라 데이터 도메인으로 나눈 이유

기능명세서는 화면 기준으로 `home / todo / report / mypage`를 제시하지만, 백엔드 패키지는 `sleep / skin`으로 나눈다.

HOME 화면 하나에 세 가지 다른 관심사가 들어 있다 — 수면 데이터 정규화(HOME-02), 피부 예보 스코어링(HOME-03), LLM Vision 연동(HOME-06). 이걸 `home` 패키지 하나에 넣으면 서로 무관한 코드가 뭉치고, `report` 패키지는 결국 그 안을 전부 들여다봐야 한다.

수면과 피부를 분리하면 REPORT·TODO가 두 도메인을 조회하는 관계가 명확해진다.

| 화면 | 백엔드 패키지 |
|---|---|
| ONBOARDING, MY | `user` |
| HOME-02, REP-03/04 | `sleep` |
| HOME-03/06/07/08/09, REP-05/12 | `skin` |
| TODO 전체 | `todo` |
| REPORT 집계 | `report` |
| HOME-04 (레벨·exp) | `game` |

#### `game`은 화면이 아니라 **적립 창구**라서 따로 뺐다 (2026-08-14)

게이미피케이션은 화면으로 보면 HOME 하나지만, **exp가 붙는 자리는 네 도메인에 흩어져 있다** ([prd.md](prd.md) §10.9).

| 적립 트리거 | 일어나는 곳 |
|---|---|
| `ATTENDANCE` | `game` (전용 API) |
| `SLEEP_SCORE_IMPROVED` · `SLEEP_SCORE_HIGH` | `sleep` |
| `VERIFICATION_STREAK` | `skin` |
| `TODO_DONE` · `TODO_ALL_DONE` | `todo` |

**넷 중 어디에 `ExpService`를 두어도 나머지 셋이 그 도메인을 참조하게 된다.** `user`에 두는 것이 그나마 자연스러워 보이지만(`users.exp`가 거기 있으니), 그러면 `user`가 레벨 컷오프·연속 보상 구간·수면 점수 임계값까지 갖게 된다 — **온보딩·동의와 아무 관계 없는 규칙들이다.**

```
domain/game/
├── GameController.java          POST /users/me/attendance
├── ExpService.java              적립·회수의 유일한 창구
├── LevelPolicy.java             컷오프 · 적립량 · 연속 보상 구간 (상수)
├── repository/ExpGrantRepository.java
├── entity/
│   ├── ExpGrant.java
│   └── ExpReason.java           enum — 6종
└── dto/response/ExpResponse.java   네 API가 함께 쓴다 (api.md §1)
```

- **`ExpService.grant(userId, baseDate, reason, amount)` 하나로 모인다.** 하루 1회 판정(`exp_grant` 유니크)과 `users.exp` 갱신·0 하한이 **한 곳에서만** 일어난다 — 도메인마다 따로 구현하면 어느 하나가 회수를 빼먹어도 컴파일도 테스트도 통과한다
- **`domain → domain` 참조는 이미 있다** — `todo`가 `skin`의 `SkinMetric`·예보 점수를 본다. 새로 만드는 규칙이 아니다
- **`global`에 둘 수 없다.** `ExpService`는 Repository를 쓰므로 도메인이며, `global → domain` 참조는 금지다
- **`GameController`의 경로가 `/users/me/attendance`인 것은 의도적이다.** 패키지 이름이 URL을 정하지 않는다 — 화면상 이것은 사용자 행위이고, 경로는 [api.md](api.md)가 유일한 출처다

---

## 3. 핵심 플로우

### 3.1 수면 데이터 수신 → 예보 산출 (앱 시작 시)

```
POST /api/v1/sleep/sessions
   │
   ├─ SleepController          요청 검증
   │
   ├─ SleepService             수면 단계 매핑 → 정규화 → 페이로드 해시 계산
   │     │
   │     └─ [중복 판단]  ← 저장·스코어링을 시작하기 전에 수행
   │          ├ 기존 없음        → 계속 진행
   │          ├ 해시 동일        → 여기서 중단. 기존 예보 반환 (processed=false)
   │          └ 해시 다름        → 검증 완료한 날이면 중단, 아니면 갱신 후 진행
   │
   ├─ SkinForecastService      수면 피처 추출
   │                           → 개인 가중치 조회 (없으면 일반 가중치)
   │                           → 지표 3종 가중합 스코어링 → 0~100 정규화
   │                           → 등급 컷오프 매핑 (25/50/75 — prd.md §10.1)
   │                           → 오늘자 예보 저장 (HOME-07 대조 기준)
   └─ 응답: { processed, sleepDate, 수면 집계, 예보 3종 }
```

#### 앱↔서버 페이로드 규격

**요청·응답의 전체 규격은 [api.md](api.md) §3에 있다.** 여기서는 구조적 판단만 적는다.

**앱은 단계 구간 배열만 보내고 집계는 서버가 전부 계산한다.** 총 수면·단계별 분·각성 횟수를 앱에서 받지 않는다.

**이유는 서버가 세션을 자르기 때문이다.** 연속 `AWAKE` 60분 이상이면 첫 기상으로 보고 거기서 끊는데([prd.md](prd.md) §4.1), 앱이 보고한 총합에는 그 뒤의 낮잠이 섞여 있을 수 있다. **서버가 자를 거면 서버가 세는 것이 맞다.** 각성 횟수를 앱에서 받지 않기로 한 것과 같은 논리다.

**앱 팀에 반드시 전달할 세 가지**

| # | 내용 | 어기면 |
|---|---|---|
| 1 | **`UNSPECIFIED`를 `CORE`로 바꿔 보내지 말 것** | 비율 분모가 오염되어 **장벽 점수만 조용히 틀린다** ([prd.md](prd.md) §10.5) |
| 2 | **시각에 오프셋을 반드시 포함할 것** | `sleepDate`가 하루 밀리고, 그 날짜로 조인되는 예보·검증이 전부 어긋난다 |
| 3 | **`inBed`는 보내지 말 것** | 서버가 무시한다. `inBed` 의존 지표는 명세에서 제외됐다 |

**서버 처리 순서**

```
1. 시간순 정렬 · 구간 겹침 검사
2. 세션 경계 자르기    연속 AWAKE 60분 이상 → 첫 기상. 이후 구간 전부 버림
3. 집계               총 수면 = asleep 구간 합 (UNSPECIFIED 포함)
                      deep/rem/core = 단계별 합
                      각성 = 5분~60분 구간의 개수와 합
4. sleepDate 결정      wake_time의 날짜 (오프셋 기준)
5. 해시 계산·비교      ← 저장 전에
```

**응답에 `processed`를 싣는다.** 앱은 시작할 때마다 호출하므로 하루에 같은 데이터가 여러 번 온다. 그날 첫 수신이면 `201`, 그 외에는 `200`이다. **검증을 마친 날의 예보는 절대 바뀌지 않는다** — 바뀌면 이미 끝난 검증의 대조 기준이 사후에 달라져 적중률이 훼손된다.

`SkinForecast`는 **하루 1건**이며, 나중에 셀피 검증(HOME-07)이 이 값을 기준으로 대조한다. 예보 없이는 검증이 성립하지 않으므로, 검증 API는 오늘자 예보 존재 여부를 먼저 확인한다.

#### 중복 수신 차단이 필수인 이유

앱은 시작될 때마다 수면 세션을 업로드한다. 새 수면 데이터가 생기기 전까지는 **같은 데이터가 계속 온다.** 이때 upsert로 덮어쓰고 재산출하면 안 된다.

| 이유 | 설명 |
|---|---|
| **검증 무효화** | 예보를 다시 산출하면 오늘자 예보값이 바뀐다. 이미 셀피 검증(HOME-07)을 마쳤다면 **대조 기준이 사후에 바뀌어 적중률이 훼손**된다 |
| 학습 오염 | 재산출이 개인 가중치 학습(HOME-08)을 다시 트리거하면 같은 데이터로 중복 학습해 가중치가 왜곡된다 |
| 비용 | 스코어링·집계가 무의미하게 반복된다 |

```java
@Transactional
public SleepUploadResponse upload(Long userId, SleepSessionUploadRequest request) {
    SleepSessionData normalized = sleepNormalizer.normalize(request);
    String hash = normalized.payloadHash();

    Optional<SleepSession> existing =
            sleepSessionRepository.findByUserIdAndSleepDate(userId, normalized.sleepDate());

    // 같은 데이터가 다시 왔다면 아무것도 하지 않고 기존 결과를 돌려준다
    if (existing.isPresent() && existing.get().hasSameHash(hash)) {
        return SleepUploadResponse.notProcessed(
                forecastService.getForecast(userId, normalized.sleepDate()));
    }

    SleepSession saved = sleepSessionRepository.save(existing
            .map(s -> s.update(normalized, hash))
            .orElseGet(() -> SleepSession.of(userId, normalized, hash)));

    return SleepUploadResponse.processed(forecastService.createForecast(saved));
}
```

**해시는 정규화 후에 계산한다.** 앱이 필드 순서나 표현 형식을 바꿔 보내도 같은 데이터로 판정되어야 한다.

`(user_id, sleep_date)` 유니크 제약은 그대로 유지한다 — 동시 요청이 들어와도 DB 레벨에서 막힌다.

### 3.2 셀피 분석 → 검증 → 학습

```
POST /api/v1/skin/selfie
   │
   ├─ SelfieAnalysisService                 (multipart/form-data)
   │     ├─ MultipartFile → byte[]          메모리에서만 다룬다. 디스크·버킷에 쓰지 않는다
   │     ├─ OpenAI Vision 호출 (Structured Outputs로 지표 3종 강제)
   │     ├─ 0~100 정규화 → SkinMeasurement 저장 (RDS — 숫자 3개)
   │     └─ 바이트 참조 해제                  ← 메서드를 벗어나면 남는 참조가 없다
   │
   ├─ SkinVerificationService
   │     ├─ 오늘자 예보 조회 (없으면 SKIN_FORECAST_NOT_FOUND)
   │     ├─ 지표별 |예보 − 실측| → 판정 매핑
   │     ├─ 전체 적중률 산출 → 검증 이력 저장 → 연속일수 갱신
   │     └─ SkinModelService.learn() 호출
   │
   └─ SkinModelService
         └─ 오차 방향·크기로 개인 가중치 갱신 (단순 평균 이동)
            → 다음 예보에 즉시 반영
```

### 3.3 추천 엔진 (TODO-02) — 구현 완료 (2026-08-13)

```
GET /api/v1/todo?baseDate=

  그날 daily_todo 행이 있으면  → 그대로 반환 (재계산 없음)

  없으면
   1. 그날 예보 조회            없으면 200 + NO_SLEEP_DATA (빈 배열, 행을 만들지 않는다)
   2. 지표별 직전 검증 판정 조회  검증 이력이 없으면 빈 맵
   3. 카테고리별 활성 액션 전체 조회 (AVOID / DO 각각)
   4. 매칭    예보 점수 ≤ threshold 인 것만        ← 예보 점수만 본다
              그날 예보가 null인 지표는 후보에서 제외
   5. 정렬    impact_score × (100 − 예보 점수) + verdictBonus
              동점은 id 오름차순
   6. 절단    AVOID 3 + DO 5                      → 최대 8행 저장
```

**정렬은 `impact_score` 단독이 아니라 지표 심각도로 가중한다.** 세 지표의 후보를 한 풀에 넣고 뽑기 때문에, 가중하지 않으면 거의 정상인 지표의 액션이 심각한 지표를 밀어낸다. 계산식과 근거는 [erd.md](erd.md) §3.8.

**`verdictBonus`는 직전 검증이 `OVERESTIMATED`(위험 과소평가)인 지표에만 붙는다** — 예보가 말한 것보다 실제 피부가 나빴던 지표를 위로 올린다. **매칭에는 관여하지 않는다.**

**후보 추출만 SQL, 매칭·가중·정렬·절단은 `domain/todo/TodoScoringPolicy`(코드).** 이래야 추천 로직 단위 테스트가 DB 없이 돈다. `threshold` 비교까지 Java로 내린 이유는 [erd.md](erd.md) §3.8.

> ⚠️ **`domain/skin/ScoringPolicy`와 다른 클래스다.** 앞엣것은 예보 스코어링([prd.md](prd.md) §10.3~§10.7), 이쪽은 추천 정렬이다.

**목록은 그날 첫 조회 시 고정된다.** 조회 API가 행을 만드는 유일한 자리이며, 이유는 [erd.md](erd.md) §3.9.

**예보가 없는 날은 에러가 아니다.** 조회 API의 빈 상태는 200이라는 규칙([conventions.md](conventions.md) §2)을 따른다 — **쓰기를 겸한다는 것이 예외의 근거가 되지 못한다.** 갈리는 기준은 그 빈 상태가 사용자에게 일상적인가이고, 여기서는 수면을 아직 올리지 않은 신규 사용자가 매번 만난다.

**룰 기반**으로 구현한다. LLM 생성은 재현성과 비용 때문에 해커톤 범위에서 제외한다. 액션 마스터는 DB 테이블로 관리하며, 초기 데이터는 시드 SQL(24행)로 넣는다 — **사람이 한 번 실행한다**([workflow.md](workflow.md) §8).

### 3.4 리포트 집계 (REP-02~08) — 구현 완료 (2026-08-15)

**리포트는 새 값을 만들지 않는다.** 저장된 `sleep_session`·`skin_forecast`·`skin_measurement`를 읽어 기간으로 묶을 뿐이고, **파생값을 컬럼으로 두지 않는다**는 원칙([erd.md](erd.md) §2 원칙 ①) 그대로 볼 때마다 계산한다. 그래서 **리포트 전용 테이블이 하나도 없다.**

```
GET /report/weekly?baseDate=
        ↓
periodStart = baseDate − 6                        ← 가입일이 아니라 baseDate 역산
        ↓
가입 후 7일 미만?  → INSUFFICIENT_DATA + 빈 배열   ← 가입일은 여기에만 쓴다
        ↓
findByUserIdAndSleepDateBetween(...)              ← 기간을 한 번에 조회
        ↓
날짜별 DailySleepScoreCalculator                  ← §10.8 부분점수 단순 평균
        ↓
CorrelationCalculator(같은 세션 맵을 그대로 재사용)  ← 세션을 다시 조회하지 않는다
```

**`skin` 도메인을 고치지 않고 호출만 한다.** 수면 점수는 `SkinScoringEngine.featureScores`(가중치를 곱하기 **전** 단계인 `s(f)`)만 재사용하고 지표점수 계산은 쓰지 않는다 — **예보 점수와 수면 점수는 다른 값이다**([prd.md](prd.md) §10.8).

| | 예보 점수 (HOME-03) | 수면 점수 (리포트·exp) |
|---|---|---|
| 무엇 | 이 수면이 **피부에** 어떻게 나타날까 | **수면 자체**가 어땠나 |
| 계산 | 지표별 가중평균 + 개인 가중치 | 참여 피처 부분점수의 **단순 평균** |

> ⚠️ **화면에서 두 숫자가 나란히 보인다. 라벨을 섞지 말 것.**

**상관 강도(REP-07)만 다른 원천을 쓴다** — `skin_measurement`(셀피 실측)다. 예보와 상관을 내면 **수면으로 만든 값이 수면과 관련 있다는 순환 논증**이 되기 때문이며, 그래서 **세션과 검증이 둘 다 있는 날짜만 표본**이 된다. 값도 정규화된 부분점수가 아니라 **저장된 원본값**을 쓴다.

**⚠️ 수면 점수 계산이 두 클래스에 있다** — `sleep/SleepScoreCalculator`(exp 적립)와 `report/DailySleepScoreCalculator`(리포트). 산식은 같고 입력 모양만 다르다(전자는 부분점수 맵 또는 재조회, 후자는 이미 조회한 세션). **한쪽만 바뀌면 exp로 지급한 점수와 리포트가 보여준 점수가 갈린다** — `VerificationStreakCalculator`를 한 곳으로 묶은 것과 같은 자리다. **세 번째를 만들지 말 것.**

---

## 4. 데이터 설계 원칙

> 테이블 10개의 컬럼 전체와 각 판단의 근거는 [erd.md](erd.md)에 있다. 여기서는 원칙만 다룬다. (**엔티티 10개가 전부 만들어져 있다** — `exp_grant` 포함.)

### 기준일(baseDate) 중심

거의 모든 엔티티가 `(userId, baseDate)` 조합으로 하루 1건이다.

| 엔티티 | 유니크 키 |
|---|---|
| `SleepSession` | `(user_id, sleep_date)` |
| `SkinForecast` | `(user_id, base_date)` |
| `SkinMeasurement` | `(user_id, base_date)` |
| `Verification` | `(user_id, base_date)` |

이 제약이 §3.1의 중복 차단을 DB 레벨에서도 보장한다. 애플리케이션에서는 **페이로드 해시 비교로 재처리 자체를 막고**, DB 제약은 동시 요청에 대한 안전망 역할을 한다.

`SleepSession`은 `payload_hash` 컬럼(정규화된 페이로드의 SHA-256)을 함께 갖는다.

### 지표 3종 저장

`SkinMetric` enum(`DARK_CIRCLE`, `COMPLEXION`, `BARRIER`)과 점수를 별도 행으로 두지 않고, **엔티티에 컬럼 3개로 평면화**한다. 지표 수가 3개로 고정이고 항상 함께 조회·비교되므로, 행 분리는 조인 비용만 늘린다.

```java
@Entity
public class SkinForecast extends BaseTimeEntity {
    private Long userId;
    private LocalDate baseDate;
    private int darkCircle;   // 0~100
    private int complexion;   // 0~100
    private int barrier;      // 0~100
}
```

지표가 늘어날 가능성이 생기면 그때 행 분리로 전환한다.

### 시간 타입

- 날짜만: `LocalDate` (기준일, 수면 일자)
- 시각 포함: `OffsetDateTime` — 타임존을 잃지 않기 위해. `HealthCheckResponse`가 이미 이 패턴이다
- DB 컬럼: `DATE`, `DATETIME(6)`

### 공통 엔티티

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {
    @CreatedDate  private OffsetDateTime createdAt;
    @LastModifiedDate private OffsetDateTime updatedAt;
}
```

`@EnableJpaAuditing`은 `global/config/JpaConfig`에 둔다.

`consent_history` 같은 **append-only 이력 테이블**은 수정되지 않으므로 `updatedAt`이 항상 `createdAt`과 같다. 이런 엔티티는 `BaseCreatedEntity`(createdAt만)를 상속한다.

### `global`에 넣지 않는 것

| 흔히 넣지만 이 프로젝트엔 불필요 | 이유 |
|---|---|
| `security/` | 인증이 없다. Spring Security를 추가하지 않는다 |
| `resolver/` (`@LoginUser`) | 위와 같은 이유. `userId`는 파라미터로 받는다 |
| `interceptor/`, `filter/` | 인증·로깅 요구사항이 아직 없다 |
| `CorsConfig` | 클라이언트가 iOS 앱이라 CORS를 타지 않는다. 웹 프론트가 붙으면 그때 |
| `util/` | **비어 있는 채로 만들지 않는다.** 두 번째 도메인이 같은 걸 필요로 할 때 옮긴다 |

**`ScoringPolicy`는 `global`이 아니라 `skin`에 둔다.** 공통 설정처럼 보이지만 피부 도메인의 비즈니스 규칙이다. 판단 기준은 *"다른 도메인이 이걸 쓸 일이 있나?"* — 없으면 도메인 안이다.

---

## 5. 셀피 이미지 취급 (구조로 증명하기)

제품이 약속하는 "얼굴 데이터를 보관하지 않는다"를 **문서가 아니라 코드 구조로** 지킨다.

| # | 장치 | 코드 위치 |
|---|---|---|
| 1 | **오브젝트 스토리지를 쓰지 않는다** — 멀티파트 → 메모리 → LLM. 업로드할 버킷이 없다 | `SelfieAnalysisService` |
| 2 | **DB 스키마에 이미지 경로·URL 컬럼 없음** | `SkinMeasurement` 엔티티 |
| 3 | 이미지 바이트를 필드·정적 변수·캐시에 담지 않는다 (메서드 지역 변수만) | `SelfieAnalysisService` |
| 4 | 예외 로그에 바이트·base64를 찍지 않는다 | `SelfieAnalysisService`, `GlobalExceptionHandler` |

**장치 1과 2가 본체다.** 저장할 곳도, 저장한 것을 가리킬 컬럼도 없으면 실수로도 보관할 수 없다.

```java
public SkinMeasurement analyze(Long userId, MultipartFile selfie) {
    byte[] image = selfie.getBytes();                          // 메모리에만 존재
    SkinMetricScores scores = visionClient.analyze(image);     // OpenAI Vision
    return measurementRepository.save(SkinMeasurement.of(userId, scores));
}
```

S3를 경유하던 초안에서 바뀐 부분이다. 임시 업로드가 사라지면서 `finally` 삭제 블록 · Lifecycle 정책 · 버킷 접근제어가 **전부 불필요해졌다.** 지켜야 할 장치가 줄어든 만큼 정책이 깨질 경로도 줄었다.

**표현 주의**: 이미지가 우리 인프라에 파일로 남지는 않지만, 처리 중 서버 메모리를 거치고 OpenAI로 전송된다. "저장하지 않습니다"보다 **"분석 직후 즉시 삭제하며, 얼굴을 복원할 수 있는 데이터를 보관하지 않습니다"**가 정확하다.

**미확인 사항**: OpenAI API 요청 로그에 이미지가 남는지는 **여전히 확인되지 않았다.** S3를 뺀 뒤에도 이 항목은 그대로 남는다 — 우리 인프라가 아니라 **제공자 쪽 보관**의 문제이기 때문이다.

**다만 고지 문구가 더 이상 이 답에 의존하지 않는다** (2026-08-07 확정). 우리가 확실히 아는 사실 — **외부 AI로 전송되어 분석된다는 것, 그리고 우리 서버에 저장하지 않는다는 것** — 만 밝히고, 제공자 쪽 보관 여부는 문구에서 주장하지 않기로 했다. 확인이 끝나면 문장을 **추가**하면 된다. ([prd.md](prd.md) §2 고지 문구 원칙)

> **"어디에도 저장되지 않습니다"는 쓸 수 없다.** 주어가 없어 제공자까지 포함하는 것으로 읽히고, 그건 우리가 모르는 사실이다. **"서버에 저장하지 않습니다"** 로 주어를 붙이면 참이다.

---

## 6. 인증 (현재: 없음)

해커톤 범위에서 로그인 체계를 두지 않는다. 테스트 유저를 DB에 직접 주입해 사용한다.

- `data.sql` 또는 `CommandLineRunner`로 테스트 유저 시딩
- **API는 `userId`를 `X-User-Id` 헤더로 받는다** (2026-08-07 확정 — [conventions.md](conventions.md) §8)
- Spring Security 의존성을 **추가하지 않는다** — 없는 인증을 위한 설정 파일이 늘어날 뿐이다

**헤더로 받는 이유는 아래 "인증을 붙일 때를 위한 준비"와 같은 목적이다.** 경로 변수로 받으면 JWT 도입 시 전 경로를 갈아야 하고, 쿼리 파라미터로 받으면 API마다 파라미터를 하나씩 지워야 한다. 헤더면 **읽던 자리 한 곳만** 바뀐다.

### 나중에 인증을 붙일 때를 위한 준비

Service 계층은 `userId`를 파라미터로 받도록 통일한다. 나중에 JWT를 도입하면 Controller에서 토큰을 파싱해 `userId`를 꺼내 넘기는 것으로 끝난다. Service 시그니처는 바뀌지 않는다.

```java
// 지금
public SkinForecastResponse getForecast(Long userId, LocalDate date)

// 인증 도입 후에도 동일 — Controller만 바뀐다
```

**Service 안에서 SecurityContext나 요청 컨텍스트를 직접 들여다보지 않는다.** 이 규칙 하나로 인증 도입 비용이 거의 사라진다.

---

## 7. 외부 연동

### OpenAI Vision

| 항목 | 값 |
|---|---|
| 모델 | `gpt-5.6-terra` (기본값) |
| API | Responses API + `input_image` |
| 이미지 전달 | 요청 본문에 **base64 인라인**. URL을 넘기지 않는다(넘길 URL이 없다 — §5) |
| 출력 강제 | Structured Outputs — 지표 3종 정수 스키마 |
| 대체 모델 | `gpt-5.6-luna` (비용 문제 시) |

모델 ID는 `application.yml`에 프로퍼티로 둬서 코드 수정 없이 교체할 수 있게 한다.

```yaml
openai:
  api-key: ${OPENAI_API_KEY:}          # 비어 있어도 앱은 뜬다 — 아래 참조
  base-url: https://api.openai.com
  vision-model: ${OPENAI_VISION_MODEL:gpt-5.6-terra}
  timeout: 30s                          # 읽기 — 초과 시 SELFIE_ANALYSIS_TIMEOUT(504)
  connect-timeout: 5s                   # 연결 — 장애와 지연은 앱이 할 일이 다르다
```

**SDK를 쓰지 않고 Spring `RestClient`로 직접 호출한다** (2026-08-10 확정). 보내는 것은 Responses API 한 엔드포인트뿐이라 SDK의 이득이 작고, 대신 **Structured Outputs 스키마를 우리 손으로 통제해야 한다** — 점수 방향이 뒤집히면 아무 제약에도 안 걸리는 구조라 그 자리가 라이브러리 뒤에 숨으면 안 된다. 의존성도 늘지 않는다(`RestClient`는 이미 있다).

**`api-key`의 기본값이 빈 문자열인 것은 의도한 것이다.** `${OPENAI_API_KEY}`로 두면 키가 없는 환경에서 앱이 아예 기동하지 못해, 키를 받지 못한 팀원이 수면·예보 쪽 작업조차 못 하고 CI·테스트도 더미 키를 넣어야 돈다. 대신 **셀피 분석만 502로 실패**하고 기동 시 WARN이 남는다. 운영에서 키가 빠지는 사고는 이 기본값이 아니라 **CD가 배포 전 `app.env`를 선검사**해서 막는다([workflow.md](workflow.md) §7).

**타임아웃을 반드시 지정한다.** 기본값은 무제한이라 OpenAI가 응답하지 않으면 톰캣 워커 스레드가 영구히 묶이고, 스레드 풀이 하나라 **수면 업로드까지 같이 막힌다.**

Structured Outputs로 응답 스키마를 강제하면 파싱 실패가 사라진다. 자유 텍스트 응답을 정규식으로 긁는 방식은 쓰지 않는다.

**점수 방향을 프롬프트에 반드시 명시한다.** 세 지표 모두 **0~100, 높을수록 좋은 상태**이며 예보와 같은 방향이어야 한다.

| 필드 | 100점 | 0점 |
|---|---|---|
| `darkCircle` | 눈 밑이 맑다 | 다크서클이 짙다 |
| `complexion` | 안색에 생기가 돈다 | 창백하고 칙칙하다 |
| `barrier` | 장벽이 튼튼하다 | 건조·붉음·거칠다 |

**`darkCircle`이 특히 위험하다.** 이름만 보면 "다크서클이 심한 정도"로 읽혀 모델이 방향을 뒤집기 쉽다. 뒤집혀도 값은 0~100 정수라서 `CHECK` 제약도 Structured Outputs 스키마도 걸러내지 못한다. **HOME-07 적중률만 조용히 무너지고, 개인 가중치가 반대 방향으로 학습된다.** 필드 설명(JSON Schema `description`)에 "100 = 다크서클이 거의 없는 맑은 상태"처럼 **양 끝을 문장으로 적는다.**

> 프롬프트 수정 시 회귀 확인: 눈 밑이 뚜렷하게 어두운 샘플에서 `darkCircle`이 **낮게** 나오는지 본다. 스텁이 아닌 실제 호출로 한 번은 확인해야 한다.
>
> ✅ **2026-08-10에 실호출로 확인했다.** 다크서클이 뚜렷한 사진과 눈 밑이 맑은 사진을 각각 `POST /skin/selfie`에 넣어 대조했고, **맑은 쪽이 더 높은 `darkCircle`**이 나왔다 — 정의(`회복된 정도`)와 같은 방향이다. **`SkinVisionPrompt`를 고쳤다면 이 확인을 다시 해야 한다.**

**인터페이스로 감싼다** — 제공자를 바꿀 가능성이 있으므로 `SkinVisionClient` 인터페이스를 두고 `OpenAiSkinVisionClient`로 구현한다. **인터페이스는 `byte[]`(또는 `MultipartFile`)를 받는다 — 스토리지 키를 받지 않는다.** 테스트에서는 고정값을 반환하는 스텁으로 대체한다.

**반환 타입은 `SkinVisionScores`(필드 3개)이고 `SkinMetric`을 쓰지 않는다.** 이 패키지는 `global`이라 `domain`을 참조할 수 없다(§2). 지표가 3종 고정이라 필드로 펴도 늘어날 일이 없다.

**범위를 벗어난 점수는 자르지 않고 실패시킨다.** strict 스키마가 `minimum`/`maximum`을 지원하지 않아(넣으면 요청이 400이다) 0~100은 코드가 지켜야 하는데, 클램프하면 **모델이 다른 척도로 답했다는 사실이 숨는다** — 101을 100으로 만들면 저장은 되고 적중률만 틀린다. 실패하면 앱이 재시도하고 행은 생기지 않는다([erd.md](erd.md) §3.6).

### AWS RDS (MySQL)

운영 DB는 EC2 인스턴스 안이 아니라 **관리형 RDS**에 둔다. 배포로 컨테이너를 갈아끼워도 데이터가 살아남고, 백업·장애 조치를 직접 만들지 않아도 된다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

접속 정보는 전부 환경 변수다. **엔드포인트·비밀번호를 코드나 설정 파일에 넣지 않는다.**

| 항목 | 방침 |
|---|---|
| 엔진 | MySQL 8.0.16 이상 (`CHECK` 제약이 실제로 동작하는 최소 버전 — [erd.md](erd.md) §3) |
| 네트워크 | 퍼블릭 액세스 차단. **EC2 보안 그룹에서만** 3306 인바운드 허용 |
| 자격 증명 | 마스터 계정을 앱에 쓰지 않고 앱 전용 계정을 별도로 만든다 |
| 백업 | 자동 백업 기본값 유지 (해커톤 범위에서 별도 튜닝 없음) |

> **반영 완료.** RDS가 구축됐고 CD가 EC2에 배포한다. **로컬 개발 DB는 컨테이너를 유지**하기로 했다 — 공용 RDS를 개발에 쓰면 `ddl-auto: update` 때문에 작업 중인 엔티티가 운영 스키마에 반영된다 ([workflow.md](workflow.md) §1).
>
> 로컬 스택 파일은 **`docker-compose.local.yml`** 로 분리했다 (2026-08-08). 운영은 Compose를 쓰지 않는다.

---

## 8. 설정과 시크릿

`application.yml`은 구조만 두고 값은 `${ENV_VAR}`로 주입한다. **구축 완료 상태다.**

| 항목 | 관리 방법 | 상태 |
|---|---|---|
| DB 접속 정보 | 환경 변수 `DB_HOST`·`DB_PORT`·`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD` | ✅ (값만 RDS 엔드포인트로 교체하면 된다) |
| OpenAI API 키 | 환경 변수 `OPENAI_API_KEY` | ✅ **없어도 앱은 뜬다** — 셀피 분석만 `502`로 실패하고 기동 시 WARN이 남는다. 운영에서 빠지면 CD 선검사가 경고한다 |

**RDS로 옮겨도 코드는 바뀌지 않는다.** `DB_HOST`가 `localhost`에서 RDS 엔드포인트로 바뀔 뿐이다. 이 변수 이름들을 이미 쓰고 있는 덕분에 전환 비용이 설정값 하나로 끝난다.

`.env.example`이 필요한 환경 변수의 목록 역할을 한다. **`.env`는 커밋하지 않는다** (`.gitignore` 처리됨).

### 프로파일

| 프로파일 | 용도 | DB |
|---|---|---|
| (기본) | 로컬 실행 · 운영 | MySQL — 로컬은 컨테이너, 운영은 RDS. `ddl-auto: update` |
| `test` | 테스트 | H2 인메모리, `ddl-auto: create-drop` |

프로파일을 나누지 않고 `DB_HOST`만 바꾼다. **운영용 프로파일을 따로 만들지 않는다** — 접속 대상만 다르고 나머지 설정이 같으므로, 파일이 늘면 두 곳이 어긋날 여지만 생긴다.

`src/test/resources/application-test.yml`은 **파일명이 `application.yml`이면 안 된다.** 그러면 main의 설정을 통째로 가려버려 `spring.application.name`·springdoc 설정까지 사라진다. 프로파일 파일로 두고 `@ActiveProfiles("test")`로 덮어쓴다.

> JWT 관련 설정은 **전부 제거했다.** 이 프로젝트는 인증을 두지 않는다(§6). 참조하는 코드가 없어 무해했지만, 남겨두면 "인증이 있는 줄" 알고 작업하는 사람이 생긴다. 나중에 인증을 도입하면 그때 다시 넣는다.

### DDL 관리 — `ddl-auto: update`

엔티티에서 스키마를 만들고 DDL 스크립트를 따로 두지 않는다. 해커톤 범위의 결정이다.

**`update`의 한계 — 알고 써야 한다.**

| 하는 것 | 하지 않는 것 |
|---|---|
| 없는 테이블 생성 (제약·인덱스 포함) | 컬럼 **이름 변경** 반영 |
| 없는 컬럼 추가 | 컬럼 **타입 변경** 반영 |
| | 컬럼·제약 **삭제** |

즉 **더하기만 하고 빼거나 고치지는 않는다.** 필드명을 바꾸면 옛 컬럼이 그대로 남아 `NOT NULL` 위반으로 INSERT가 깨진다.

**엔티티를 파괴적으로 바꿨으면 DB를 지우고 다시 만든다.**

```bash
docker compose down -v && docker compose up -d mysql
```

> ⚠️ **이 방법은 로컬 컨테이너에서만 쓴다.** 운영 RDS에는 `-v`에 해당하는 안전한 대응이 없다. RDS 전환 후 파괴적 스키마 변경이 필요하면 `DROP TABLE` 후 재기동하거나 마이그레이션 도구 도입을 논의한다 — 팀 협의 대상이다.

### ⚠️ 유니크 제약은 반드시 눈으로 확인한다

이 프로젝트의 유니크 제약은 **장식이 아니라 정확성 장치**다.

| 테이블 | 제약 | 지키는 것 |
|---|---|---|
| `sleep_session` | `(user_id, sleep_date)` | 같은 수면 데이터 중복 저장 차단 |
| `skin_forecast` | `(user_id, base_date)` | 하루 1건 예보 — 검증의 단일 기준 |
| `skin_measurement` | `(user_id, base_date)` | 하루 1회 검증 |
| `personal_weight` | `(user_id, sleep_feature, skin_metric)` | 가중치 중복 학습 차단 |
| `daily_todo` | `(user_id, base_date, action_master_id)` | 같은 항목 중복 추가 차단 |

`@Table(uniqueConstraints = ...)`로 **엔티티에 명시**하고, 테이블이 처음 만들어진 뒤 한 번은 실제로 걸렸는지 확인한다.

```sql
SHOW CREATE TABLE sleep_session;
```

제약이 빠지면 중복 차단이 애플리케이션 코드에만 의존하게 되고, 동시 요청에서 조용히 뚫린다.

---

## 9. 배포

AWS EC2 + Docker, DB는 **RDS(MySQL)**. **CI/CD 파이프라인 구축 완료.**

| 파일 | 역할 |
|---|---|
| `Dockerfile` | 멀티스테이지 빌드 (JDK 21 빌드 → JRE 21 실행) |
| `docker-compose.local.yml` | **로컬 전용** app + MySQL 스택. app은 MySQL healthcheck 통과 후 기동 |
| `.github/workflows/ci.yml` | `dev`·`main` 대상 PR에서 `bootJar` + `test` + Docker 이미지 빌드 |

**운영에서 DB 컨테이너를 띄우지 않는다.** EC2에는 앱 컨테이너만 올리고 DB는 RDS를 바라본다 — 배포로 컨테이너를 교체해도 데이터가 남는다.

**그래서 Compose 파일은 통째로 로컬 전용이다.** 운영 배포는 Compose를 거치지 않고 CD가 EC2에서 `docker run --env-file`로 직접 실행한다.

> **파일 분리로 결정했다** (2026-08-08). `compose.yaml` → **`docker-compose.local.yml`**.
> 프로파일 지정 방식도 검토했지만, **파일명 자체가 용도를 드러내는 쪽**을 골랐다. `compose.yaml`이라는 이름은 "운영에서도 쓰나?"를 계속 다시 확인하게 만든다.
> 자동 인식되는 이름이 아니므로 `.env`의 `COMPOSE_FILE`이 이 파일을 가리킨다 — `.env`가 있어야 `docker compose` 명령이 `-f` 없이 동작한다.

**CI는 DB 서비스를 띄우지 않는다.** 테스트가 전부 H2(`test` 프로파일) 아니면 `@WebMvcTest`라 DataSource가 필요 없고, `bootJar`는 컴파일만 한다.

> 원래 CI에 MySQL service가 있었지만 **아무도 쓰지 않았다.** 테스트는 `@ActiveProfiles("test")`로 H2를 물고 있었고, MySQL 컨테이너는 매번 기동·헬스체크 시간만 쓰고 있었다. 2026-08-08에 제거했다.
>
> ⚠️ **방언 차이는 지금 어디서도 걸리지 않는다.** H2를 `MODE=MySQL`로 띄워도 완전히 같지 않다. 특히 **유니크 제약이 실제로 걸렸는지 확인하는 것**([erd.md](erd.md) §2.4)은 H2에서 의미가 없다. 엔티티 작업에서 이 검증이 필요해지면 그때 MySQL service를 다시 붙이고 해당 테스트만 실제 MySQL로 돌린다.

```
PR 생성 (dev/main 대상)
   → CI: bootJar + test (H2) + docker build
   → 머지
   → EC2 배포
   → GET /api/v1/health 로 기동 확인
```

`HealthCheckController`가 이 용도로 만들어져 있다 — 로드밸런서·배포 파이프라인의 기동 확인 엔드포인트다. 응답은 공통 래퍼에 담기지만 **본문을 검사하는 곳은 없다**(상태 코드만 본다).

---

## 10. 참고

- 코딩 규칙: [conventions.md](conventions.md)
- 브랜치·협업: [workflow.md](workflow.md)
- 제품 요구사항: [prd.md](prd.md)
