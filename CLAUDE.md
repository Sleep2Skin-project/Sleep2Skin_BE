# sleep2skin_be

애플워치 수면 데이터를 피부 지표로 변환해 예보하고, 셀피로 검증해 개인 모델을 학습시키는 서비스의 백엔드. 멋사 14기 중앙해커톤 프로젝트.

## 기술 스택

Java 21 · Spring Boot 4.1.0 · Gradle 9.5.1 · MySQL + Spring Data JPA · springdoc-openapi
배포: AWS EC2 + Docker · 외부 연동: OpenAI Vision(`gpt-5.6-terra`), AWS S3

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
├── user/       사용자 · 동의 이력 · 설정
├── sleep/      수면 세션 수신 · 정규화 · 집계
├── skin/       피부 예보 · 셀피 실측 · 검증 · 개인 모델
├── todo/       추천 엔진 · TODO 리스트
├── report/     일간 · 주간 · 월간 · 종합 리포트
└── health/     헬스체크 (구현 완료 — 패턴 참고용)
```

도메인마다 `Controller / Service / repository / entity / dto`. Controller-Service-Repository 3계층.

## 핵심 루프

```
수면 수집(앱이 전달) → 피부 예보 → 행동 처방(TODO) → 셀피 검증 → 개인 모델 학습 → 리포트
```

②③④⑤가 서버 로직이며 이 프로젝트의 실질적 범위다.

## 반드시 지킬 것

### 피부 지표는 3종 고정

```java
enum SkinMetric { DARK_CIRCLE, COMPLEXION, BARRIER }   // 다크서클 · 혈색 · 장벽
```

전부 0~100 점수, 높을수록 좋음. 예보와 실측이 **같은 세트**여야 검증이 성립한다.
기능명세서 초안의 유분·칙칙함·색소침착은 확정에서 **제외**됐다. 추가하지 말 것.

### 셀피 원본은 보관하지 않는다

S3 임시 업로드 → 분석 → **`finally` 블록에서 즉시 삭제**. DB에는 숫자 지표만 남는다.
**엔티티에 이미지 경로·URL 컬럼을 두지 않는다.** 이게 정책을 지키는 가장 강한 장치다.
표현 주의: "저장하지 않습니다"(X) → "분석 직후 즉시 삭제하며 얼굴 복원 가능한 데이터를 보관하지 않습니다"(O)

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
**빈 상태(수면 데이터 없음, 검증 이력 없음, 기록 부족)는 정상 흐름**이며 전부 `ErrorCode`로 관리한다.

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
| [docs/prd.md](docs/prd.md) | 기능 요구사항 확인, 기능 ID(HOME-03 등) 조회, 미결정 사항 확인, 구현 우선순위 |
| [docs/architecture.md](docs/architecture.md) | 새 도메인·엔티티 설계, 핵심 플로우 파악, 외부 연동(S3·OpenAI) 구현 |
| [docs/conventions.md](docs/conventions.md) | 코드 작성 직전 — 응답 포맷, 에러 코드, DTO/Entity 규칙, API 경로, Swagger |
| [docs/workflow.md](docs/workflow.md) | 브랜치 생성, PR, 팀 분담, 빌드 |

기능 ID는 `ONB-01~05` / `HOME-01~09` / `TODO-01~07` / `REP-01~12` / `MY-01~05`.
원본 기획: Notion 「기능명세서」 (prd.md §9에 링크)

## 현재 상태

`GET /api/v1/health` 헬스체크만 구현됨. DB·JPA·AWS·OpenAI 의존성 미도입.

다음 착수 순서 (prd.md §8):
1. 공통 기반 — 응답 래퍼, 전역 예외 처리, 에러 코드, MySQL 연결, 테스트 유저 시딩
2. 수면 세션 수신 `POST /api/v1/sleep/sessions` (수면 일자 기준 upsert — 멱등 필수)
3. 피부 예보 산출 (HOME-03)

## 임시값 주의

**수면 피처 → 피부 지표 매핑은 확정값이 아니다.** 문서를 완성하려 임시로 채운 것이고 팀 논의로 재확정된다 (prd.md §9.1). 예보 산출·리포트·학습이 전부 이 매핑에 의존하므로 바뀔 때 파급이 크다.

임시값은 전부 `skin/ScoringPolicy` 한 곳에 모은다. **서비스 로직에 하드코딩하지 않는다.** 참조하는 코드에는 `// 임시값 (PRD §9.1)` 주석을 남긴다.

## 착수 전 확정이 필요한 것

예보 산출(HOME-03)을 구현하려면 코드로 정할 수 없는 값이 먼저 필요하다:

- **B1** 가중합 스코어링 공식과 초기(일반) 가중치 — §9.1 임시 매핑 재확정 포함
- **B2** 등급 컷오프 구간 (0~100 → 등급 라벨)
- **B3** 판정 오차 구간 (적중/근접/과소예측/과대예측)

이 값들을 임의로 정하지 말고 사용자에게 확인할 것. 전체 미결정 목록은 prd.md §7 (B=블로커, L=로직, E=빈상태, S=화면, P=정책).
