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

| | 로컬 테스트 | 로컬 실행 | 운영 |
|---|---|---|---|
| DB | H2 (인메모리) | MySQL 컨테이너 | **AWS RDS (MySQL)** |
| 스키마 | `ddl-auto: create-drop` | `ddl-auto: update` | `ddl-auto: update` |

**세 환경의 차이는 `DB_HOST` 값뿐이다.** 운영 전환은 환경 변수 교체로 끝나고 코드·프로파일은 그대로다.

### ⚠️ 로컬 `.env`에 운영 RDS 주소를 넣지 않는다

**설정 파일은 하나뿐이고, 어느 DB를 물지는 전적으로 `DB_HOST`가 결정한다.** `application.yml`에 운영용 프로파일이 따로 없다.

그런데 `ddl-auto: update`이므로 **앱이 뜨는 순간 작업 중인 엔티티가 그대로 스키마에 반영된다.** 로컬 `.env`의 `DB_HOST`만 RDS로 바꾸면 `bootRun` 한 번이 운영 스키마를 바꾼다.

| | 로컬 | 운영 |
|---|---|---|
| 환경 변수 출처 | `.env` (gitignore) | EC2의 `/home/{user}/app.env` · GitHub Secrets |
| 스키마 되돌리기 | `docker compose down -v` | **대응하는 조치가 없다** |

**`update`는 컬럼을 추가만 하고 지우지 않는다.** 실험하다 만 엔티티가 한 번 닿으면 그 컬럼은 RDS에 영구히 남는다.

> **주의할 것은 "Hibernate가 RDS를 건드리는 것" 자체가 아니다.** 운영도 `ddl-auto: update`라 RDS 스키마는 원래부터 엔티티가 관리한다. 위험한 건 **머지되지 않은, 작업 중인 로컬 엔티티가 RDS에 닿는 것**이다.

**지켜야 할 것**

- `.env`의 `DB_HOST`는 **항상 `localhost`**다. RDS 주소를 넣지 않는다
- 운영 값을 `.env`나 `.env.example`에 복사해두지 않는다
- 운영 DB를 봐야 하면 **그때 bastion/SSM으로 열고 평소엔 닫아 둔다**
- **RDS 보안그룹은 EC2 보안그룹만 3306을 허용한다** — 개발자 IP를 막아두면 실수해도 접속 자체가 안 된다. 이게 유일하게 사람에 기대지 않는 방어다

**사고가 났다면** — 아직 운영 데이터가 없는 동안은 RDS를 비우고 재배포하면 복구된다. 검증 이력이 쌓인 뒤에는 개인 가중치를 되살릴 방법이 없으므로(셀피를 다시 찍어야 한다) **데이터가 쌓이기 전에 보안그룹을 막아두는 것이 맞다.**

**엔티티를 파괴적으로 바꿨다면**(필드명·타입 변경, 삭제) `update`는 반영하지 못한다. DB를 지우고 다시 만든다.

```bash
docker compose down -v && docker compose up -d mysql
```

**로컬 컨테이너에서만 쓰는 방법이다.** 운영 RDS에는 대응하는 조치가 없다 (architecture.md §8).

이때 테스트 유저 시딩도 다시 돌아야 한다. 팀원에게 **"DB 갈아엎어야 한다"고 알리는 것**을 잊지 말 것 — 조용히 바꾸면 상대는 원인 모를 `NOT NULL` 위반을 겪는다.

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
| `hotfix/selfie-analyze-timeout` | 배포 후 발견된 셀피 분석 타임아웃 긴급 수정 |

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
fix(skin):      셀피 분석 실패 시 예외 로그에 이미지가 남던 문제 수정
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
| `build.gradle` | 의존성 추가 시 (MySQL, JPA, OpenAI) — **AWS SDK는 필요 없다.** S3를 쓰지 않고 RDS는 JDBC로만 붙는다 |
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

PRD §7의 블로커는 코드로 결정할 수 없는 것들이다.

**예보 산출(HOME-03)을 막는 블로커는 남아 있지 않다.** B1(공식·초기 가중치)·B7(결측 처리)이 2026-08-07에, B2(등급 컷오프)·B3(판정 오차 구간)이 2026-08-06에 확정됐다 → PRD §10.1~§10.6.

남은 블로커는 다른 것을 막는다:

- **B4** OpenAI 데이터 보관 정책 — 셀피 분석(HOME-06) 고지 문구
- **B5** 수면 단계 매핑 계약 (앱 팀 협의) — 양쪽 다 받게 짜면 개발은 막히지 않는다
- **B6** 수면 목표값 — 2단계 일간 리포트 착수 전까지

### 스코어링 파라미터 취급

**등급 컷오프·판정 구간·피처 매핑·초기 가중치·정규화 구간은 전부 `ScoringPolicy` 한 곳에 모은다.** 확정값이지만 조정될 수 있고, 흩어지면 조정할 때 전부 찾아다녀야 하는 것은 임시값과 같다. 참조하는 코드에 출처를 남긴다: `// 확정값 (PRD §10.4)`

**서비스 로직 안에 하드코딩하지 않는다.** PRD §9에 남은 임시값(상관 강도 라벨 구간, 신뢰도 등급 일수, 트리아지 임계값)도 같은 규칙이며, 이쪽에는 `// 임시값 (PRD §9.2)`를 쓴다.

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
