# 아키텍처

sleep2skin 백엔드의 구조와 설계 결정. 제품 요구사항은 [prd.md](prd.md) 참조.

---

## 1. 시스템 구성

```
┌──────────────┐         ┌─────────────────────────┐         ┌──────────────┐
│  iOS 앱      │  HTTPS  │  sleep2skin_be          │         │  OpenAI      │
│              │────────▶│  Spring Boot 4.1 / EC2  │────────▶│  Vision API  │
│  HealthKit   │         │                         │         └──────────────┘
│  카메라       │◀────────│  Docker 컨테이너         │         ┌──────────────┐
└──────────────┘         │                         │────────▶│  AWS S3      │
                         └────────────┬────────────┘         │  (임시)       │
                                      │                      └──────────────┘
                                      ▼
                                 ┌─────────┐
                                 │  MySQL  │
                                 └─────────┘
```

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
│   │   ├── S3Config            S3Client 빈
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
│       ├── s3/                 SelfieStorage (업로드 + 삭제)
│       └── openai/             SkinVisionClient 인터페이스 + 구현체
│
└── domain/                     비즈니스 도메인
    ├── user/                   사용자 · 동의 이력 · 설정
    ├── sleep/                  수면 세션 수신 · 정규화 · 집계
    ├── skin/                   피부 예보 · 셀피 실측 · 검증 · 개인 모델
    ├── todo/                   추천 엔진 · TODO 리스트
    ├── report/                 일간 · 주간 · 월간 · 종합 리포트
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
   │                           → 등급 컷오프 매핑
   │                           → 오늘자 예보 저장 (HOME-07 대조 기준)
   └─ 응답: { processed, 예보 3종, 수면 통역 헤드라인 }
```

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
   ├─ SelfieAnalysisService
   │     ├─ S3 임시 업로드   selfie/tmp/{uuid}.jpg
   │     ├─ OpenAI Vision 호출 (Structured Outputs로 지표 3종 강제)
   │     ├─ 0~100 정규화 → SkinMeasurement 저장
   │     └─ finally { S3 객체 삭제 }        ← 예외·타임아웃에도 반드시 실행
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

### 3.3 추천 엔진 (TODO-02)

```
오늘 예보 3종 + 직전 검증 결과
   → 액션 마스터 테이블에서 임계값 룰 매칭
   → 영향도순 정렬
   → 구분별(피하세요/이렇게) 상위 3개
```

**룰 기반**으로 구현한다. LLM 생성은 재현성과 비용 때문에 해커톤 범위에서 제외한다. 액션 마스터는 DB 테이블로 관리하며, 초기 데이터는 시드 SQL로 넣는다.

---

## 4. 데이터 설계 원칙

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
| 1 | S3 Lifecycle 정책 — `selfie/tmp/` 1일 자동 만료 | 인프라 설정 (Terraform/콘솔) |
| 2 | `finally` 블록 삭제 — 예외·타임아웃에도 실행 | `SelfieAnalysisService` |
| 3 | **DB 스키마에 이미지 경로 컬럼 없음** | `SkinMeasurement` 엔티티 |
| 4 | 버킷 퍼블릭 액세스 전면 차단, EC2 IAM 역할로만 접근 | 인프라 설정 |

장치 3이 가장 강력하다. 이미지를 참조할 컬럼이 없으면 실수로도 보관할 수 없다.

```java
public SkinMeasurement analyze(Long userId, MultipartFile selfie) {
    String key = "selfie/tmp/" + UUID.randomUUID() + ".jpg";
    try {
        s3Client.upload(key, selfie);
        SkinMetricScores scores = visionClient.analyze(key);   // OpenAI Vision
        return measurementRepository.save(SkinMeasurement.of(userId, scores));
    } finally {
        s3Client.delete(key);   // 어떤 경로로 빠져나가도 실행
    }
}
```

**미확인 사항**: OpenAI API 요청 로그에 이미지가 남는지 확인이 필요하다. 사용자 고지 문구를 확정하기 전에 반드시 검증한다. ([prd.md](prd.md) §7 블로커 B5)

**재검토 여지**: S3를 경유하지 않고 요청 본문에 이미지를 직접 실어 보내면 우리 인프라에는 이미지가 전혀 남지 않는다. 설명이 더 단순해지므로 S3 경유가 정말 필요한지 재검토할 만하다.

---

## 6. 인증 (현재: 없음)

해커톤 범위에서 로그인 체계를 두지 않는다. 테스트 유저를 DB에 직접 주입해 사용한다.

- `data.sql` 또는 `CommandLineRunner`로 테스트 유저 시딩
- API는 `userId`를 경로 변수 또는 쿼리 파라미터로 받는다
- Spring Security 의존성을 **추가하지 않는다** — 없는 인증을 위한 설정 파일이 늘어날 뿐이다

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
| 출력 강제 | Structured Outputs — 지표 3종 정수 스키마 |
| 대체 모델 | `gpt-5.6-luna` (비용 문제 시) |

모델 ID는 `application.yml`에 프로퍼티로 둬서 코드 수정 없이 교체할 수 있게 한다.

```yaml
openai:
  api-key: ${OPENAI_API_KEY}
  vision-model: gpt-5.6-terra
  timeout-seconds: 30
```

Structured Outputs로 응답 스키마를 강제하면 파싱 실패가 사라진다. 자유 텍스트 응답을 정규식으로 긁는 방식은 쓰지 않는다.

**인터페이스로 감싼다** — 제공자를 바꿀 가능성이 있으므로 `SkinVisionClient` 인터페이스를 두고 `OpenAiSkinVisionClient`로 구현한다. 테스트에서는 고정값을 반환하는 스텁으로 대체한다.

### AWS S3

```yaml
aws:
  s3:
    bucket: sleep2skin-selfie-tmp
    region: ap-northeast-2
```

인증은 EC2 IAM 역할을 사용한다. **액세스 키를 코드나 설정 파일에 넣지 않는다.**

---

## 8. 설정과 시크릿

`application.yml`은 구조만 두고 값은 `${ENV_VAR}`로 주입한다. **구축 완료 상태다.**

| 항목 | 관리 방법 | 상태 |
|---|---|---|
| DB 접속 정보 | 환경 변수 `DB_HOST`·`DB_PORT`·`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD` | ✅ |
| OpenAI API 키 | 환경 변수 `OPENAI_API_KEY` | 미도입 |
| AWS 자격 증명 | EC2 IAM 역할 (키 없음) | 미도입 |

`.env.example`이 필요한 환경 변수의 목록 역할을 한다. **`.env`는 커밋하지 않는다** (`.gitignore` 처리됨).

### 프로파일

| 프로파일 | 용도 | DB |
|---|---|---|
| (기본) | 로컬 실행·운영 | MySQL, `ddl-auto: none` |
| `test` | 테스트 | H2 인메모리, `ddl-auto: create-drop` |

`src/test/resources/application-test.yml`은 **파일명이 `application.yml`이면 안 된다.** 그러면 main의 설정을 통째로 가려버려 `spring.application.name`·springdoc 설정까지 사라진다. 프로파일 파일로 두고 `@ActiveProfiles("test")`로 덮어쓴다.

### ⚠️ 정리가 필요한 설정

`application.yml`에 `jwt.secret`·`jwt.expiration`이 있고 `compose.yaml`·`ci.yml`에도 `JWT_SECRET`·`JWT_EXPIRATION`이 있다. **이 프로젝트는 인증을 두지 않기로 확정했다** (§6). 참조하는 코드가 없어 지금은 무해하지만, 남겨두면 나중에 "인증이 있는 줄" 알고 작업하는 사람이 생긴다.

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

AWS EC2 + Docker. **CI/CD 파이프라인 구축 완료.**

| 파일 | 역할 |
|---|---|
| `Dockerfile` | 멀티스테이지 빌드 (JDK 21 빌드 → JRE 21 실행) |
| `compose.yaml` | app + MySQL 로컬 스택. app은 MySQL healthcheck 통과 후 기동 |
| `.github/workflows/ci.yml` | `dev`·`main` 대상 PR에서 `bootJar` + `test` + Docker 이미지 빌드 |

CI는 MySQL service를 띄워 **실제 MySQL로도 테스트**한다. 로컬은 H2를 쓰므로 방언 차이는 CI에서 걸린다.

```
PR 생성 (dev/main 대상)
   → CI: bootJar + test (MySQL service) + docker build
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
