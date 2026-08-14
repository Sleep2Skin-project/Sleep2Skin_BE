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

로컬 스택 파일은 **`docker-compose.local.yml`** 이다. 자동 인식되는 이름이 아니라서 `.env`의 `COMPOSE_FILE`이 이 파일을 가리킨다 — **`.env`를 먼저 만들어야 `docker compose` 명령이 `-f` 없이 동작한다.**

**이름을 `compose.yaml`로 두지 않은 이유**는 이 파일이 로컬 전용이기 때문이다. 운영은 Compose를 쓰지 않는다 — CD가 EC2에서 `docker run --env-file`로 직접 띄우고 DB는 RDS를 바라본다. 파일명이 그 사실을 드러내야 **"이거 운영에서도 쓰나?"를 매번 다시 확인하지 않는다.**

`bootRun`은 셸에 `.env` 값이 주입돼 있어야 한다. 구체적인 명령은 [README.md](../README.md)를 본다 — **실행 방법은 README가 단일 출처**이고, 이 문서는 팀 규칙을 다룬다.

**테스트는 MySQL 없이 돈다.** `test` 프로파일이 H2를 물려주므로 `./gradlew test`만 치면 된다.

### IDE 실행 설정 — 타임존을 UTC로 맞춘다

IDE 실행 버튼으로 띄운다면 **VM 옵션에 `-Duser.timezone=UTC`를 넣는다.**

운영 컨테이너는 UTC다(`Dockerfile`의 `ENV TZ=UTC`). 로컬 개발 머신은 KST이므로 그냥 두면 **두 환경의 JVM 타임존이 다르다.** 코드에 `LocalDate.now()` 같은 것이 섞여 들어가도 로컬에서는 멀쩡하고 **EC2에서만, 그것도 한국 시간 오전 9시 이전에만** 하루 밀린다. 재현이 안 되는 버그가 된다.

**값이 UTC라는 것보다 두 환경이 같다는 것이 요점이다.** 저장 기준을 왜 고정했는지는 [erd.md](erd.md) §3.1에 있다.

IDE는 `.env`를 읽지 않으므로 **DB 접속 정보도 실행 설정의 환경 변수에 직접 넣어야 한다** — `DB_HOST`·`DB_PORT`·`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`. 값은 `.env`와 같다. 빠뜨리면 `${DB_HOST}`가 풀리지 않아 기동에 실패한다.

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

#### 지금 적용 (2026-08-08)

**아직 1인 개발이지만 B를 넘길 수 있게 비워둔 채로 진행한다.**

| 묶음 | 상태 |
|---|---|
| 공통 `global` · `user` | 진행 중. 동의·온보딩(ONB-02·05) 완료, 조회 3개(MY-01·02·04)는 A 다음 |
| A `sleep` · `skin` | 진행 중 |
| **B `todo` · `report`** | **파일을 하나도 만들지 않는다.** 껍데기 클래스도 미리 두지 않는다 |

**A를 쪼개지 않는다.** `POST /sleep/sessions`가 업로드받고 그 자리에서 예보까지 돌려주는 계약이라([api.md](api.md) §3) 두 도메인이 한 트랜잭션에 묶여 있다.

**엔티티·Repository 9개는 이미 다 있다.** B를 맡는 사람이 새로 만들 것이 없고, A를 하는 쪽이 B 폴더를 열 이유도 없다.

**B 인수인계 전에 A의 Repository 조회 메서드를 안정시킨다.** `report`가 `SleepSessionRepository`·`SkinForecastRepository`를 직접 읽는데, 넘긴 뒤에 시그니처를 바꾸면 그쪽이 깨진다.

**`action_master` 마스터 데이터(prd.md §7 P5)는 개발보다 콘텐츠 작업량이 크다.** B를 맡을 사람이 정해지면 코드보다 이걸 먼저 시작하는 게 낫다.

#### 도메인을 나눠도 같이 건드리는 파일

| 파일 | 대응 |
|---|---|
| `global/exception/ErrorCode.java` | 도메인별 구역을 지켜 추가한다 |
| `build.gradle` | 의존성 추가 시 CI/CD 담당과도 겹친다. 추가하면 알린다 |
| `docs/api.md` · `docs/conventions.md` | 공용 문서 |
| `global/config/SwaggerConfigTest` | **도메인 무관 규칙만 두고 문서 전체를 순회한다.** 새 API를 추가해도 손댈 일이 없다 — 도메인별 단언은 `{도메인}ApiDocsTest`에 둔다 (conventions.md §11) |

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
- ~~**B6** 수면 목표값~~ — **MVP 제외 (2026-08-14).** 목표 달성 판정 자체를 뺐다 (PRD §4.4)

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

## 7. 배포

`main`에 push되면 `cd.yml`이 이미지를 ECR에 올리고 EC2에서 교체한다. 로컬은 Compose를 쓰지만 **운영은 `docker run --env-file`** 하나다.

### 배포는 네 단계다 (2026-08-09)

```
1. 선검사    app.env 에 필수 값이 있는가            ← 아무것도 건드리기 전
2. 선검증    새 이미지가 실제로 뜨는가 (임시 포트)   ← 기존 컨테이너는 살아 있다
3. 교체      기존을 내리고 새 것을 올린다
4. 롤백      교체가 실패하면 이전 이미지로 복구
```

**앞의 두 단계에서 실패하면 서비스가 그대로 살아 있다.** 원래는 기존 컨테이너를 먼저 지우고 새 것을 띄워서, **기동 실패가 곧 서비스 중단**이었다.

**2단계가 실패의 대부분을 잡는다.** 임시 컨테이너가 운영과 같은 `app.env`로 뜨므로 DB 연결·스키마 생성·시딩이 전부 여기서 검증된다.

**헬스체크는 재시도 루프다.** 고정 `sleep`은 짧으면 멀쩡한 배포를 실패로 잡고 길면 실패를 늦게 안다 — 첫 배포는 테이블 9개를 만들고 시딩까지 돈다.

### EC2 `app.env`에 있어야 하는 값

| 키 | 비고 |
|---|---|
| `DB_HOST` · `DB_PORT` · `DB_NAME` · `DB_USERNAME` · `DB_PASSWORD` | **없으면 기동 자체가 안 된다.** 1단계가 여기서 끊는다 |
| `OPENAI_API_KEY` | 셀피 분석(HOME-06)용. **없어도 앱은 뜨고, 셀피만 502로 실패한다** — 그래서 1단계는 배포를 막지 않고 경고만 남긴다 |
| `CORS_ALLOWED_ORIGINS` | 없으면 `http://localhost:8081`. 앱이 다른 오리진에서 부르면 넣는다 |
| `OPENAI_VISION_MODEL` | 없으면 `gpt-5.6-terra`. 비용 문제로 `gpt-5.6-luna`로 내릴 때만 넣는다 |

**기동을 막는 값과 기능을 막는 값을 나눈 이유**는 배포를 세우는 기준이 달라야 하기 때문이다. DB 값이 빠지면 컨테이너가 뜨지 않아 배포 자체가 무의미하지만, `OPENAI_API_KEY`가 빠진 배포는 나머지 API가 전부 정상이다. 여기서 배포를 막으면 **셀피와 무관한 핫픽스까지 함께 막힌다.**

⚠️ 대신 **키가 없는 배포는 초록불로 끝난다.** 사용자가 셀피를 찍을 때가 되어서야 502가 나오므로, 1단계의 `::warning::` 을 흘려보내지 않는다. 앱 기동 로그에도 `OPENAI_API_KEY 가 비어 있다` WARN 이 남는다.

`COMPOSE_FILE`·`DB_ROOT_PASSWORD`는 **로컬 전용**이라 넣지 않는다.

⚠️ **`--env-file`은 따옴표를 벗기지 않는다.** `DB_PASSWORD="abc"`로 적으면 **따옴표까지 비밀번호가 된다.** `=` 앞뒤 공백도 값에 들어간다. Compose의 `.env`는 따옴표를 벗겨주므로 **로컬에서 쓰던 감각으로 옮겨 적으면 걸린다.**

```
DB_PASSWORD=값        ← 이렇게
DB_PASSWORD="값"      ← 이러면 따옴표가 비밀번호의 일부다
```

값을 노출하지 않고 확인하는 방법이다. `first`가 `"`나 `'`면 잘못 적힌 것이다.

```bash
docker run --rm --env-file ~/app.env alpine sh -c 'echo "user=[$DB_USERNAME] len=${#DB_PASSWORD} first=[$(printf %.1s "$DB_PASSWORD")]"'
```

---

## 8. 운영 DB (RDS) 설정

**로컬과 운영이 정반대라 여기서 한 번 막힌다** (2026-08-09 첫 배포에서 실제로 발생).

### `app.env`를 고쳐도 RDS에는 아무 일이 일어나지 않는다

로컬 스택은 MySQL 컨테이너에 이 값들을 넘긴다.

```yaml
# docker-compose.local.yml
MYSQL_DATABASE: ${DB_NAME}       # 이 이름으로 DB 를 만든다
MYSQL_USER:     ${DB_USERNAME}   # 이 계정을 만든다
MYSQL_PASSWORD: ${DB_PASSWORD}
```

**로컬은 `.env` 한 곳만 고치면 DB·계정·테이블이 전부 만들어진다.** 그래서 운영도 그럴 거라고 기대하게 된다.

**RDS는 이미 돌고 있는 서버라 "처음 뜰 때"가 없다.** `app.env`를 읽는 주체도 아니다 — 그건 앱에게 "이 계정으로 붙어라"라고 지시하는 파일이고, RDS에게 계정을 만들라는 지시가 아니다. `cd.yml`도 `app.env`를 컨테이너에 넘겨주기만 한다.

```
app.env  →  "sleep2skin 계정으로, 이 비밀번호로 붙여줘"    ← 요청하는 쪽
RDS      →  "그런 계정 없는데"                            ← 확인하는 쪽
```

자물쇠는 그대로인데 열쇠만 새로 깎아 온 셈이라, 아무리 새 비밀번호를 적어도 RDS가 모르면 `Access denied for user ...`가 난다.

### 운영에서 누가 무엇을 만드는가

| 대상 | 누가 만드나 |
|---|---|
| 계정 (`sleep2skin`) | **사람** — `CREATE USER` |
| 데이터베이스 (`sleep2skin`) | **사람** — `CREATE DATABASE` |
| 테이블 9개 | Hibernate (`ddl-auto: update`) |

**데이터베이스도 앱이 만들지 못한다.** `ddl-auto: update`는 *이미 있는 데이터베이스 안에* 테이블을 만드는 기능이고, JDBC URL에 `createDatabaseIfNotExist`가 없다. **DB가 없으면 연결 단계에서 실패한다.**

### ⚠️ 비밀번호는 두 곳에 있다

| 어디 | 무엇이 정하나 |
|---|---|
| RDS 안 | `CREATE USER` / `ALTER USER` |
| `app.env` | 파일에 적어둔 값 |

**한쪽만 바꾸면 `Access denied`가 난다.** `app.env`의 `DB_PASSWORD`를 바꿀 때는 RDS에서 `ALTER USER`도 함께 실행한다.

> MySQL은 **계정 없음 · 비밀번호 틀림 · 호스트 불일치**를 전부 같은 메시지로 응답한다. 메시지만으로 구분할 수 없으므로 `SELECT user, host FROM mysql.user WHERE user = '...'`로 확인한다.

### 계정을 둘로 나눈다

| 계정 | 쓰임 | 어디에 두나 |
|---|---|---|
| `admin` (마스터) | 사람이 DB 작업할 때만 | **`app.env`에 넣지 않는다** |
| `sleep2skin` (앱 전용) | 앱이 붙을 때 | `app.env` |

**둘을 다른 값으로 둔다.** 그래야 `app.env`가 유출돼도 피해가 `sleep2skin` 스키마 하나로 갇힌다. 마스터 계정이 거기 있으면 그 파일 하나가 인스턴스 전체 권한을 들고 있는 셈이다.

마스터 비밀번호를 잊었다면 **콘솔 → RDS → 인스턴스 → 수정 → 새 마스터 암호 → 즉시 적용**으로 재설정한다. 기존 값을 몰라도 되고 재부팅도 필요 없다.

### 최초 설정 절차

**EC2에서 실행한다.** 보안그룹이 EC2만 3306을 허용하므로 개발자 PC에서는 접속되지 않는다.

**1. 주소를 `app.env`에서 가져온다** — 손으로 옮겨 적으면 오타가 난다(실제로 `.com`이 `.co`로 잘려 한 번 막혔다).

```bash
RDS=$(grep '^DB_HOST=' ~/app.env | cut -d= -f2)
```

```bash
echo "$RDS"
```

**2. 마스터 계정으로 접속한다.** `mysql` 클라이언트가 설치돼 있지 않아도 된다.

```bash
docker run -it --rm mysql:8 mysql -h "$RDS" -u admin -p
```

⚠️ **명령을 여러 줄로 나누지 않는다.** `\` 뒤에 공백이 하나만 붙어도 줄 연결이 깨져 `-h`가 전달되지 않고, mysql이 로컬 소켓을 찾다가 엉뚱한 에러를 낸다.

**3. 프롬프트가 `mysql>`로 바뀌면** DB와 계정을 만든다.

```sql
CREATE DATABASE IF NOT EXISTS sleep2skin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'sleep2skin'@'%' IDENTIFIED BY '앱비밀번호';
ALTER USER 'sleep2skin'@'%' IDENTIFIED BY '앱비밀번호';
GRANT ALL PRIVILEGES ON sleep2skin.* TO 'sleep2skin'@'%';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user = 'sleep2skin';
```

- **`utf8mb4`를 명시한다.** 닉네임·`action_master` 문구가 전부 한국어다. MySQL 8 기본값이지만 기본값에 기대면 인스턴스 설정이 다를 때 조용히 깨진다
- **호스트는 `%`다.** `localhost`로 만들면 EC2에서 오는 접속이 다른 계정 취급이라 막힌다
- **권한을 `sleep2skin.*`로 준다.** `ddl-auto: update`가 `CREATE TABLE`·`ALTER TABLE`을 하므로 더 좁히면 **앱은 뜨는데 테이블을 못 만드는** 상태가 된다. 의미 있는 경계는 권한 종류가 아니라 **스키마 하나로 가두는 것**이다

**4. 앱 계정으로 붙는지 확인한 뒤 배포한다.**

```bash
docker run -it --rm mysql:8 mysql -h "$RDS" -u sleep2skin -p sleep2skin -e "SELECT 1"
```

`1`이 찍히면 앱도 붙는다. 실패하면 그대로 배포해도 CD 2단계에서 걸린다.

### 액션 마스터 시드 — 사람이 한 번 넣는다 (2026-08-13)

**`action_master` 24행은 앱이 만들지 않는다.** 테이블만 Hibernate가 만들고 안은 비어 있다 — `spring.sql.init`도 Flyway도 쓰지 않는다. 테스트 유저와 다르다(`TestUserSeeder`는 `CommandLineRunner`라 자동으로 돈다).

**비어 있으면 TODO 탭이 통째로 빈다.** 예보는 정상이고 API도 `200`이라 `avoidItems`·`checklistItems`가 빈 배열로만 나간다 — **에러가 아니라서 로그에도 안 남는다.** 배포 후 확인 목록에 넣는다.

**배포는 테이블을 만들 뿐 채우지 않으므로, 순서는 `배포 → 시드`다.** 테이블이 없는 상태에서 실행하면 `Unknown table`이 난다.

```bash
# 파일을 EC2로 옮긴 뒤 (또는 리포지토리를 clone 한 위치에서)
docker run -i --rm mysql:8 mysql \
  --default-character-set=utf8mb4 \
  -h "$RDS" -u sleep2skin -p sleep2skin < action_master.sql
```

⚠️ **`--default-character-set=utf8mb4`를 반드시 붙인다.** 없으면 클라이언트가 `latin1`로 접속해 **한국어 `title`·`reason`이 `???`로 들어간다.** DB와 컬럼이 `utf8mb4`여도 소용없다 — 깨지는 곳은 클라이언트 ↔ 서버 구간이다. **INSERT는 성공하고 에러도 없다.**

이미 깨진 채로 넣었다면 다시 지우지 말고 `action_master_fix_encoding.sql`을 같은 옵션으로 실행한다 — `id`를 유지한 채 `title`·`reason`만 UPDATE한다. **`daily_todo.action_master_id`가 이미 그 id를 가리키고 있을 수 있어 DELETE 후 재INSERT는 안전하지 않다.**

```bash
docker run -i --rm mysql:8 mysql --default-character-set=utf8mb4 \
  -h "$RDS" -u sleep2skin -p sleep2skin < action_master_fix_encoding.sql
```

**두 파일 모두 `src/main/resources/db/seed/`에 있고 Git에 커밋돼 있다.**

**멱등하지 않다.** `INSERT`에 조건이 없어 두 번 실행하면 24행이 더 생기고, `action_master`에는 유니크 제약이 없어 DB가 막지 않는다. 추천 결과에 같은 항목이 중복으로 뜬다.

```sql
SELECT COUNT(*) FROM action_master;              -- 24 여야 한다
SELECT title FROM action_master WHERE id = 1;    -- '눈 비비기·문지르기' — ??? 면 인코딩 실패
```

**로컬도 같다.** `docker compose down -v` 뒤에는 시드가 함께 지워지므로 다시 넣는다. DB 이름·계정은 `.env`의 `DB_NAME`·`DB_USERNAME`이다.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 \
  -u root -p"$DB_ROOT_PASSWORD" "$DB_NAME" < src/main/resources/db/seed/action_master.sql
```

**엔티티를 파괴적으로 바꿔 DB를 다시 만들 때마다 이 단계가 따라온다**(§1). 잊으면 TODO 탭만 조용히 빈다.

### 에러로 어디까지 갔는지 읽는다

| 에러 | 어디서 멈췄나 |
|---|---|
| `socket '/var/run/mysqld/mysqld.sock'` | `-h`가 전달되지 않았다. **명령이 쪼개졌다** |
| `Unknown MySQL server host` | 주소 오타 — DNS에서 멈춤 |
| `Access denied for user` | **RDS까지 도달했다.** 계정·비밀번호·호스트만 남음 |
| `Unknown database 'sleep2skin'` | 접속은 됐는데 **DB가 없다** |
| 한참 멈춰 있다 | 보안그룹 또는 주소 오타 |

**`Access denied`가 나오면 절반은 성공이다** — 네트워크와 인스턴스는 정상이라는 뜻이다.

### 배포 후 확인

**유니크 제약 5개가 실제로 걸렸는지 본다**([erd.md](erd.md) §2 원칙 ④). 빠지면 중복 차단이 애플리케이션 코드에만 의존하게 되고 동시 요청에서 조용히 뚫린다.

```sql
SHOW CREATE TABLE sleep_session;   -- (user_id, sleep_date)
SHOW CREATE TABLE skin_forecast;   -- (user_id, base_date)
```

**액션 마스터가 채워져 있는지도 본다.** 비어 있으면 TODO 탭이 빈 배열로만 응답한다 — 에러가 아니라 로그에 안 남는다(위 "액션 마스터 시드").

```sql
SELECT COUNT(*) FROM action_master;   -- 24
```

### 앱은 nginx 뒤에 있다 (2026-08-09)

컨테이너는 **`127.0.0.1:8080`에만 바인딩**된다. 외부에서 오는 요청은 nginx가 받아 TLS를 끊고 평문 `http`로 앱에 넘긴다.

```
브라우저 ──https──▶ nginx ──http──▶ 앱 (127.0.0.1:8080)
```

**그래서 앱이 보는 스킴은 항상 `http`다.** 원래 요청이 `https`였다는 사실은 `X-Forwarded-Proto` 헤더로만 전해지고, 그걸 신뢰하려면 설정이 필요하다.

```yaml
server:
  forward-headers-strategy: framework
```

이게 없으면 앱이 자기가 `http`로 서비스되는 줄 알고 동작한다. **가장 먼저 드러나는 자리가 Swagger다** — springdoc이 문서의 `servers`를 `http://...`로 생성하고, `https`로 열린 Swagger 페이지에서 `http` 요청을 보내면 브라우저가 **혼합 콘텐츠로 차단**한다. 서버에 닿지도 못하고 끝나서 Swagger는 이유를 모른 채 이렇게만 띄운다.

```
Failed to fetch.
Possible Reasons: CORS / Network Failure / URL scheme must be "http" or "https" for CORS request.
```

⚠️ **CORS 설정으로는 풀리지 않는다.** Swagger UI는 앱이 직접 내려주므로 같은 출처이고 애초에 CORS 검사 대상이 아니다. `CORS_ALLOWED_ORIGINS`는 앱·웹 프론트가 **다른 출처에서** 부를 때 쓰는 값이다.

⚠️ **Swagger만의 문제도 아니다.** 리다이렉트 URL이나 `Location` 헤더에서도 같은 이유로 `http`가 새어 나간다.

**nginx 쪽 전제** — 헤더를 실제로 보내고 있어야 한다. 이 설정 파일은 저장소에 없으므로 서버에서 확인한다.

```bash
sudo grep -r "X-Forwarded" /etc/nginx/
```

`proxy_set_header X-Forwarded-Proto $scheme;`이 없으면 앱이 참고할 정보 자체가 없다.

**확인 방법** — 배포 후 문서의 `servers`가 `https`인지 본다.

```bash
curl -s https://sleep2skin.duckdns.org/v3/api-docs | head -c 300
```

### ⚠️ DB를 처음 붙이는 배포에서 특히 조심한다

`main`에 오래 DB 없는 버전이 떠 있었다면 **`app.env`에도 DB 값이 없다.** 배포하기 전에 넣어야 한다.

**`app.env`에 값을 넣는 것만으로는 부족하다.** RDS 쪽에 계정과 데이터베이스를 따로 만들어야 하며, 로컬과 달리 앱이 대신 만들어주지 않는다 → **§8**

**스키마는 `ddl-auto: update`가 만든다.** 테이블이 없으면 엔티티 그대로 생성되지만, **이미 있는 테이블의 제약을 완화하지는 못한다** — `NOT NULL` → `NULL 허용` 같은 변경은 반영되지 않는다. 로컬은 `docker compose down -v`로 넘어가지만 **RDS에는 대응하는 조치가 없다.** 데이터가 쌓이기 전에 스키마를 굳혀두는 편이 낫다.

**RDS 엔진은 8.0.16 이상**이어야 `CHECK` 제약이 실제로 동작한다. 그 미만은 문법만 받고 무시한다([erd.md](erd.md) §3.5).

**RDS 보안그룹은 EC2 보안그룹만 3306을 허용한다.** 개발자 IP를 막아두면 로컬 `.env`에 실수로 RDS 주소를 넣어도 접속 자체가 안 된다 — §1의 사고를 **사람에 기대지 않고** 막는 유일한 방법이다.

---

## 9. 참고

- 아키텍처: [architecture.md](architecture.md)
- 코딩 규칙: [conventions.md](conventions.md)
- 제품 요구사항: [prd.md](prd.md)
