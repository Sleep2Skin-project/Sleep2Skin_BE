# 개발 워크플로

브랜치 전략, 빌드, 협업 규칙. 코드 규칙은 [conventions.md](conventions.md) 참조.

---

## 1. 빌드와 실행

Java 21 · Gradle 9.5.1 (wrapper 포함, 별도 설치 불필요)

```bash
./gradlew bootRun          # 앱 실행 (기본 8080)
./gradlew test             # 테스트
./gradlew build            # 빌드 + 테스트
./gradlew bootJar          # 실행 가능한 JAR
./gradlew clean build      # 클린 빌드
```

Windows PowerShell에서는 `.\gradlew.bat` 을 쓴다.

### 확인 엔드포인트

| 용도 | URL |
|---|---|
| 헬스체크 | http://localhost:8080/api/v1/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI 스펙 | http://localhost:8080/v3/api-docs |

### 로컬 실행 준비

`.env.example`을 복사해 `.env`를 만들고 값을 채운다. **`.env`는 커밋하지 않는다.**

```bash
cp .env.example .env
docker compose up -d mysql
```

`bootRun`은 셸에 `.env` 값이 주입돼 있어야 한다. 구체적인 명령은 [README.md](../README.md)를 본다 — **실행 방법은 README가 단일 출처**이고, 이 문서는 팀 규칙을 다룬다.

**테스트는 MySQL 없이 돈다.** `test` 프로파일이 H2를 물려주므로 `./gradlew test`만 치면 된다.

| | 로컬 테스트 | CI | 운영 |
|---|---|---|---|
| DB | H2 (인메모리) | MySQL service | MySQL |
| 스키마 | `ddl-auto: create-drop` | `ddl-auto: none` | `ddl-auto: none` |

---

## 2. 브랜치 전략 — Git Flow

### Git Flow란

기능 개발과 배포를 브랜치로 분리하는 전략이다. **항상 배포 가능한 브랜치(`main`)와 다음 릴리스를 모으는 브랜치(`dev`)를 나눠 두고**, 기능은 각자 브랜치에서 만들어 `dev`로 합친다.

핵심 아이디어는 **"작업 중인 코드가 배포 브랜치에 절대 섞이지 않게 한다"** 이다. 여러 명이 동시에 작업해도 `main`은 언제나 동작하는 상태로 남는다.

### 브랜치 구성

| 브랜치 | 역할 | 수명 |
|---|---|---|
| `main` | 배포된 상태. 항상 동작해야 함 | 영구 |
| `dev` | 다음 배포에 들어갈 것들이 모이는 곳. **기본 브랜치** | 영구 |
| `feature/*` | 기능 하나를 개발 | 머지 후 삭제 |
| `fix/*` | `dev`에 이미 들어간 것의 버그 수정 | 머지 후 삭제 |
| `hotfix/*` | **배포된** 코드의 긴급 수정 | 머지 후 삭제 |

> 정통 Git Flow에는 `release/*` 브랜치도 있지만, 해커톤처럼 릴리스 주기가 짧으면 `dev` → `main` 직접 머지로 충분하다. 필요해지면 그때 추가한다.

### 흐름

```
main     ──●───────────────────────────●──────▶  배포
            \                         /
dev      ────●──────●────────●───────●────────▶  통합
              \    /          \     /
feature/       ●──●            ●───●
               수면 API         예보 산출
```

1. `dev`에서 `feature/*` 브랜치를 딴다
2. 기능을 완성한다
3. `dev`로 PR을 올린다
4. 리뷰 후 머지, 브랜치 삭제
5. 배포 시점에 `dev` → `main` 머지

### 브랜치 이름

```
{접두사}/{도메인}-{작업}
```

**접두사는 커밋 type과 같은 단어를 쓴다.** `feat`만 `feature/`로 늘려 쓴다(관례). 그러면 브랜치 이름만 보고 커밋 type이 정해진다.

| 커밋 type | 브랜치 접두사 |
|---|---|
| `feat` | `feature/` |
| `fix` | `fix/` |
| `refactor` | `refactor/` |
| `docs` | `docs/` |
| `chore` | `chore/` |
| — | `hotfix/` (배포된 코드 긴급 수정 전용) |

| 예시 | 설명 |
|---|---|
| `feature/sleep-session-upload` | 수면 세션 업로드 API |
| `feature/skin-forecast` | 피부 예보 산출 |
| `feature/global-exception-handler` | 전역 예외 처리 |
| `refactor/global-package` | common 패키지를 global로 통일 |
| `fix/deploy-issue-template` | dev에 있는 배포 이슈 템플릿 버그 수정 |
| `docs/add-claude-md` | CLAUDE.md 추가 |
| `hotfix/selfie-s3-delete` | 배포 후 발견된 셀피 삭제 누락 긴급 수정 |

소문자 + 하이픈. 한글을 쓰지 않는다.

### 자주 쓰는 명령

```bash
git checkout dev
git pull origin dev
git checkout -b feature/skin-forecast

# 작업 후
git add .
git commit -m "..."
git push origin feature/skin-forecast
# → GitHub에서 dev로 PR 생성

# 머지 후 정리
git checkout dev
git pull origin dev
git branch -d feature/skin-forecast
```

### 머지 규칙

| 항목 | 규칙 |
|---|---|
| `dev` 직접 push | 금지. PR을 거친다 |
| `main` 직접 push | 금지 |
| 머지 방식 | Squash and merge — feature 브랜치의 중간 커밋을 하나로 압축 |
| 머지 조건 | 빌드·테스트 통과 |

**작업 전 `dev`를 먼저 당겨온다.** 오래된 `dev`에서 브랜치를 따면 충돌이 커진다.

---

## 3. 커밋 메시지 — Conventional Commits

```
type(scope): 한국어 설명
```

**설명은 한국어로, type과 scope는 영어 소문자로** 쓴다.

### type

| type | 언제 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작은 그대로, 구조만 개선 |
| `docs` | 문서만 변경 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드·CI·의존성·설정 등 코드 외 작업 |

### scope

무엇을 건드렸는지. **도메인 패키지명이나 영역명**을 쓴다.

```
feat(sleep):    수면 세션 업로드 API 추가
feat(skin):     피부 예보 가중합 스코어링 구현
fix(skin):      셀피 분석 실패 시 S3 객체 삭제 누락 수정
refactor(global): 응답 래퍼를 record로 전환
docs(prd):      셀피 이미지 취급 정책 상세화
chore(build):   JPA/MySQL 연동 의존성 추가
chore(ci):      GitHub Actions 빌드/테스트 워크플로 추가
```

주로 쓰는 scope: `sleep` · `skin` · `todo` · `report` · `user` · `global` · `build` · `ci` · `docker` · `config` · `github` · `readme` · `prd`

### 규칙

- 커밋 하나에 **하나의 변경**만 담는다
- 제목 줄은 50자 안쪽. 마침표를 찍지 않는다
- "무엇을" 뿐 아니라 "왜"가 필요하면 본문에 쓴다

```
fix(skin): 검증 완료한 날의 예보 재산출 차단

같은 수면 일자에 내용이 다른 데이터가 오면 예보가 갱신되어,
이미 마친 셀피 검증의 대조 기준이 사후에 바뀌는 문제가 있었다.
적중률이 훼손되고 개인 가중치가 중복 학습된다.
```

---

## 4. 팀 구성과 작업 분담

### 현재 (2026-08-04)

| 인원 | 역할 |
|---|---|
| 본인 | API 개발 전담 |
| 팀원 1명 | CI/CD 파이프라인 구축 (API 개발 미참여) |

당분간 **API 개발은 1인 진행**이므로 브랜치 충돌 위험은 낮다. 다만 CI/CD 구축과 동시에 진행되므로 다음 두 파일은 서로 건드릴 수 있다:

| 파일 | 충돌 가능성 |
|---|---|
| `build.gradle` | 의존성 추가 시 (MySQL, JPA, AWS SDK, OpenAI) |
| `application.yml` / `application.properties` | 프로파일·환경 변수 설정 |
| `Dockerfile`, CI 설정 | CI/CD 담당 영역 |

**의존성을 추가하면 팀원에게 알린다.** Docker 빌드가 깨질 수 있다.

### 팀원 합류 시 도메인 분담

API 개발자가 합류하면 도메인 단위로 나눈다. 도메인 패키지가 분리돼 있어 충돌이 적다.

| 묶음 | 도메인 | 비고 |
|---|---|---|
| A | `sleep` + `skin` | 핵심 루프. 서로 강하게 엮여 한 사람이 맡는 게 낫다 |
| B | `todo` + `report` | A의 결과를 조회하는 쪽 |
| 공통 | `global`, `user` | 먼저 완성해두고 양쪽이 공유 |

**`global` 패키지(응답 래퍼, 에러 코드, 예외 처리)를 가장 먼저 확정한다.** 이게 흔들리면 두 사람의 코드가 모두 흔들린다.

에러 코드 enum은 여러 명이 동시에 추가하면 충돌이 잦은 파일이다. 도메인별로 구역을 나눠 추가한다.

---

## 5. 작업 순서

[prd.md](prd.md) §8의 우선순위를 따른다. 요약하면:

```
1단계  공통 기반 → 수면 수신 → 피부 예보 → 수면 통역 → 셀피 분석 → 검증
2단계  추천 엔진 + 액션 마스터 → TODO → 개인 모델 학습 → 일간 리포트
3단계  주간 리포트 → 저녁 가이드 → 배너·프로필
4단계  월간·종합 리포트 → 데이터 관리 → 게이미피케이션
```

**세로로 관통시킨다.** 모든 도메인의 Controller를 먼저 만들지 않고, 핵심 루프 하나가 끝까지 동작하게 만든 다음 옆으로 넓힌다.

### 착수 전 확인

PRD §7의 블로커 6건(B1~B6)은 코드로 결정할 수 없는 것들이다. 특히 다음 3개는 예보 산출(HOME-03) 구현 직전에 반드시 확정한다:

- **B1** 가중합 스코어링 공식과 초기 가중치 (§9.1 임시 매핑 재확정 포함)
- **B2** 등급 컷오프 구간
- **B3** 판정 오차 구간 (적중/근접/과소예측/과대예측)

### 임시값 취급

PRD §9의 임시값(수면 피처 → 지표 매핑 등)은 **반드시 `ScoringPolicy` 한 곳에 모아** 둔다. 재확정 시 이 파일만 고치면 되도록. 임시값을 참조하는 코드에는 `// 임시값 (PRD §9.1)` 주석을 남긴다.

**임시값을 서비스 로직 안에 하드코딩하지 않는다.** 흩어지면 재확정 때 전부 찾아다녀야 한다.

---

## 6. PR과 이슈

템플릿이 `.github/`에 있다. PR을 열면 자동으로 채워진다.

| 파일 | 용도 |
|---|---|
| `.github/PULL_REQUEST_TEMPLATE.md` | PR 본문 + **체크리스트** |
| `.github/ISSUE_TEMPLATE/feature.md` | 기능 추가 |
| `.github/ISSUE_TEMPLATE/bugfix.md` | 버그·에러 |
| `.github/ISSUE_TEMPLATE/refactor.md` | 리팩토링 |
| `.github/ISSUE_TEMPLATE/documentation.md` | 문서 |
| `.github/ISSUE_TEMPLATE/deploy.md` | 배포 기록 |

**PR 체크리스트는 템플릿이 단일 출처다.** 여기에 중복해 적지 않는다 — 두 곳에 있으면 어긋나고, 어긋나면 아무도 안 본다.

체크 항목 중 두 개는 이 프로젝트 고유의 것이라 반드시 지켜야 한다:

- **시크릿 커밋 금지** — OpenAI 키, AWS 자격증명, DB 접속 정보
- **셀피 이미지 경로 컬럼 금지** — 컴파일러도 테스트도 잡아주지 않는다. 코드 리뷰가 유일한 방어선이다 ([prd.md](prd.md) §5.2)

---

## 7. 참고

- 아키텍처: [architecture.md](architecture.md)
- 코딩 규칙: [conventions.md](conventions.md)
- 제품 요구사항: [prd.md](prd.md)
