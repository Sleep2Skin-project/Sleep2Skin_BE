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
    ├── report/   일간 · 주간 · 월간 · 종합 리포트
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
표현 주의: "저장하지 않습니다"(X) → "분석 직후 즉시 삭제하며 얼굴 복원 가능한 데이터를 보관하지 않습니다"(O) — 메모리를 거치고 OpenAI로 전송되기 때문이다.

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

## 문서

작업에 필요한 것만 읽는다.

| 문서 | 읽어야 할 때 |
|---|---|
| [docs/prd.md](docs/prd.md) | 기능 요구사항 확인, 기능 ID(HOME-03 등) 조회, 미결정 사항 확인, 구현 우선순위, **확정된 정책값(§10 등급 컷오프·판정 구간)** |
| [docs/architecture.md](docs/architecture.md) | 새 도메인 설계, 핵심 플로우 파악, 외부 연동(OpenAI) 구현, RDS 구성 |
| [docs/erd.md](docs/erd.md) | **엔티티 작성 직전** — 테이블 9개의 컬럼과 근거, 일부러 뺀 컬럼, 유니크 제약 |
| [docs/conventions.md](docs/conventions.md) | 코드 작성 직전 — 응답 포맷, 에러 코드, DTO/Entity 규칙, API 경로, Swagger |
| [docs/workflow.md](docs/workflow.md) | 브랜치 생성, PR, 팀 분담, 빌드 |

기능 ID는 `ONB-01~05` / `HOME-01~09` / `TODO-01~07` / `REP-01~12` / `MY-01~05`.
원본 기획: Notion 「기능명세서」 (prd.md §11에 링크)

## 현재 상태

**구현됨**
- `GET /api/v1/health` 헬스체크
- `global/` — `ApiResponse`·`ErrorResponse`·`ErrorCode`·`BusinessException`·`GlobalExceptionHandler`·`BaseTimeEntity`·`BaseCreatedEntity`·`JpaConfig`·`SwaggerConfig`
- 인프라 — MySQL + JPA + validation 의존성, Docker/Compose, GitHub Actions CI

**미도입**: OpenAI, AWS RDS. 엔티티·Repository·Service는 아직 하나도 없음.

**DB는 현재 로컬 MySQL 컨테이너다.** 운영 RDS 전환은 문서(architecture.md §7)에만 반영돼 있고 `compose.yaml`·`application.yml`은 그대로다 — **팀 협의 후 수정한다. 먼저 손대지 말 것.** 접속 정보가 이미 `DB_HOST` 등 환경 변수라서 전환은 값 교체로 끝난다.

**테스트**: `./gradlew test`는 MySQL 없이 돈다 (`test` 프로파일이 H2 사용). Controller는 `@WebMvcTest`.

**스키마는 엔티티에서 만든다.** `ddl-auto: update`로 결정됐고 DDL 스크립트를 따로 두지 않는다 (근거는 `application.yml` 주석). `update`는 컬럼 추가만 반영하므로 **엔티티를 파괴적으로 바꿨다면 DB를 지우고 다시 만든다** — `docker compose down -v && docker compose up -d mysql`.

다음 착수 순서 (prd.md §8):
1. **엔티티 9개 + Repository** — 컬럼이 전부 확정돼 있어 아래 미결정 값과 **무관하게 지금 만들 수 있다** (erd.md §6)
2. 테스트 유저 시딩 — 인증이 없으므로 DB에 직접 넣는다
3. 수면 세션 수신 `POST /api/v1/sleep/sessions` (페이로드 해시로 중복 차단 — 위 규칙 필수)
4. 피부 예보 산출 (HOME-03) — **블로커 없음.** 스코어링 명세가 prd.md §10.3~§10.6으로 확정됐다

## 스코어링 명세 (prd.md §10 — 확정)

**전부 `domain/skin/ScoringPolicy` 한 곳에 모은다. 서비스 로직에 하드코딩하지 않는다.** 참조하는 코드에는 `// 확정값 (PRD §10.4)` 식으로 출처를 남긴다.

```
지표점수(m) = Σ_f [ w'(m,f) × s(f) ]
w'(m,f)    = w일반 × w개인 / Σ(...)    ← 지표 내 합 = 1로 재정규화
s(f)       = 피처의 0~100 부분점수 (구간선형)
```

**재정규화가 이 설계의 핵심이다.** 없으면 개인 가중치가 점수 전체를 같은 방향으로 밀 뿐 상대 비중을 학습하지 못하고, 결측 처리도 분기가 필요해진다.

- **초기(일반) 가중치는 지표 내 균등** — `DARK_CIRCLE`·`BARRIER` 각 `0.5`, `COMPLEXION` 각 `1/3`. 근거는 가중치가 아니라 정규화 기준에 실려 있다 (§10.5 + §11.1 문헌)
- **`DEEP_SLEEP`·`REM_SLEEP`은 분이 아니라 총 수면 대비 비율(%)로 정규화한다.** 저장은 분 그대로. 여기서 단위를 헷갈리면 장벽 점수만 조용히 틀린다
- **결측(HRV·안정시 심박)은 그 항을 빼고 재정규화한다.** 대입하지 않는다 — 존재하지 않은 값이 개인 가중치 학습에 반영된다. **그 밤의 검증으로 결측 피처의 가중치를 갱신하지 않는다**
- **취침 규칙성은 최근 7일 `sleep_onset_time` 표준편차, 3일 미만이면 결측과 동일 처리.** `COMPLEXION` 피처가 전부 없으면 그날 **혈색만** 빈 상태로 응답하고 나머지 두 지표는 정상 발급한다
- **각성 총 시간은 피처가 아니다 — 표시 전용.** 각성 횟수와 같은 5분 임계값·같은 구간 집합에서 나와 중복 상관이 강하다. 배정을 미룬 게 아니라 넣지 않기로 정한 것이니 **추가하지 말 것**
- **`REM_SLEEP` → `BARRIER`는 직접 근거가 약하다는 것이 문서에 명시돼 있다** (§10.3). 학습 가능성 확보를 위해 포함한 것이며, 리포트 문구에서 이 한계를 과장해 설명하지 않는다

등급 컷오프는 `25`·`50`·`75`로 25점씩 4등분(위험/주의/보통/안정), 판정은 `예보−실측` 기준 ±5 적중 / ±6~15 근접 / ±16~ 과소·과대다 (§10.1·§10.2). **`과소예측`은 점수 축 기준** — 점수를 낮게 예측한 것이고, 그건 피부 위험을 과대평가한 것이다. 두 축이 반대라 문구에서 뒤집히기 쉽다.

스코어링 피처는 **7종**이다 (§10.3). `sleep_session`에 저장하는 10항목과 다르다 — 코어 수면·기상 시각·각성 총 시간은 저장하되 피처가 아니고, `BEDTIME_REGULARITY`는 컬럼이 아니라 `sleep_onset_time`에서 파생된다. 매핑은 `personal_weight` 7행 = §10.3의 7쌍.

## 임시값 주의

§10으로 확정되지 않은 값들은 아직 임시다 — **상관 강도 라벨 구간(L7) · 신뢰도 등급 일수(L8) · 트리아지 임계값(L6) · 수면 목표값(B6)** (prd.md §9.2). 같은 규칙으로 `ScoringPolicy`에 모으고 `// 임시값 (PRD §9.2)` 주석을 남긴다.

## 확정이 필요한 것

**예보 산출(HOME-03)과 검증(HOME-07)을 막는 블로커는 없다.** B1·B7이 2026-08-07에, B2·B3이 2026-08-06에 확정됐다.

남은 블로커는 다른 것을 막는다 — **B4**(OpenAI 데이터 보관 정책 → 셀피 고지 문구) · **B6**(수면 목표값 → 2단계 일간 리포트).

값을 임의로 정하지 말고 사용자에게 확인할 것. 전체 미결정 목록은 prd.md §7 (B=블로커, L=로직, E=빈상태, S=화면, P=정책).

**B5는 성격이 다르다** — `asleepUnspecified`를 앱이 `CORE`로 합쳐 보낼지는 **앱 팀과의 계약**이라 서버가 정할 수 없고 답을 받는 데 시간이 걸린다. 개발을 막지는 않으니(양쪽 다 받게 짜면 된다) 병렬로 물어두고 진행한다. 각성 판정 5분 임계값은 이미 확정됐다 (prd.md §4.1).
