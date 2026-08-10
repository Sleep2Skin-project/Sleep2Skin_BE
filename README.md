# Sleep2Skin_BE

## 환경변수 준비

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다. `.env`는 git에 커밋하지 않습니다.

```bash
# Bash
cp .env.example .env
```

```powershell
# PowerShell
Copy-Item .env.example .env
```

## MySQL 실행 (Docker Compose)

```bash
docker compose up -d mysql
```

로컬 스택은 **`docker-compose.local.yml`** 입니다. 자동 인식되는 이름이 아니라서 `.env`의 `COMPOSE_FILE`이 이 파일을 가리키고, 덕분에 `-f` 없이 위 명령이 동작합니다.

> `no configuration file provided` 오류가 나면 **`.env`를 아직 안 만드신 겁니다.** 위의 `cp .env.example .env`를 먼저 하시거나, `-f docker-compose.local.yml`을 직접 붙이세요.

**이 파일은 로컬 전용입니다.** 운영 배포는 Compose를 쓰지 않고 CD가 EC2에서 `docker run --env-file`로 직접 실행하며, DB도 컨테이너가 아니라 AWS RDS를 바라봅니다.

## 로컬에서 애플리케이션 실행 (`./gradlew bootRun`)

`application.yml`이 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수를 참조하므로, 실행 전 현재 셸에 `.env` 값을 주입해야 합니다.

`OPENAI_API_KEY`는 **없어도 앱이 뜹니다.** 비어 있으면 기동 로그에 WARN이 남고, 셀피 분석(`POST /api/v1/skin/selfie`)만 502로 실패합니다 — 수면·예보 쪽을 개발할 때는 키가 필요 없습니다.

```bash
# Bash
set -a
source .env
set +a
./gradlew bootRun
```

```powershell
# PowerShell
Get-Content .env | ForEach-Object {
    if ($_.Trim() -eq '' -or $_.Trim().StartsWith('#')) { return }
    $key, $value = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
}
./gradlew bootRun
```

## 로컬에서 테스트 실행 (`./gradlew test`)

**MySQL도 환경변수도 필요 없습니다.** 그냥 실행하면 됩니다.

```bash
# Bash
./gradlew test
```
```powershell
# PowerShell
.\gradlew.bat test
```

테스트는 `test` 프로파일(`src/test/resources/application-test.yml`)이 H2 인메모리 DB를 물려줍니다. 컨테이너를 띄우지 않아도 되고 빠릅니다.

- 컨텍스트 로딩 테스트 — `@SpringBootTest` + `@ActiveProfiles("test")` → H2 사용
- Controller 테스트 — `@WebMvcTest` → 웹 계층만 뜨므로 DB 자체가 필요 없음

> H2는 `MODE=MySQL`로 띄우지만 방언 차이가 완전히 사라지지는 않습니다. MySQL 고유 문법에 의존하는 쿼리를 쓰게 되면 그 테스트만 실제 MySQL로 돌리도록 따로 다뤄야 합니다.

CI(GitHub Actions)도 **DB 없이 돕니다.** 테스트가 전부 H2 아니면 `@WebMvcTest`라 DB 서비스가 필요 없습니다 (`.github/workflows/ci.yml` 참고).

## Docker

```bash
# 이미지 빌드만 (.env 불필요)
docker build -t sleep2skin-be .

# app + MySQL 전체 스택 실행
docker compose up --build
```

`app` 컨테이너는 `mysql` 서비스의 healthcheck(`mysqladmin ping`)가 통과한 뒤에 시작됩니다. DB 값은 이미지 빌드 시점이 아니라 컨테이너 실행 시점에 `.env`로부터 주입됩니다.
