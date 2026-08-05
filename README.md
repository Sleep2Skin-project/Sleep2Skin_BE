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

## 로컬에서 애플리케이션 실행 (`./gradlew bootRun`)

`application.yml`이 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION` 환경변수를 참조하므로, 실행 전 현재 셸에 `.env` 값을 주입해야 합니다.

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

> ⚠️ JPA 의존성으로 인해 `@SpringBootTest` 테스트는 실제 MySQL 연결을 필요로 합니다. **MySQL이 실행 중이지 않거나 DB/JWT 환경변수가 현재 셸에 주입되어 있지 않으면 테스트가 실패합니다.**

1. MySQL 먼저 기동
   ```bash
   docker compose up -d mysql
   ```
2. 현재 셸에 `.env` 환경변수 주입 (위 "로컬에서 애플리케이션 실행" 항목의 Bash/PowerShell 스니펫과 동일)
3. 테스트 실행
   ```bash
   # Bash
   ./gradlew test --no-daemon
   ```
   ```powershell
   # PowerShell
   .\gradlew.bat test --no-daemon
   ```

CI(GitHub Actions)에서는 위 과정을 GitHub Actions MySQL service + 워크플로 환경변수로 대체합니다 (`.github/workflows/ci.yml` 참고).

## Docker

```bash
# 이미지 빌드만 (.env 불필요)
docker build -t sleep2skin-be .

# app + MySQL 전체 스택 실행
docker compose up --build
```

`app` 컨테이너는 `mysql` 서비스의 healthcheck(`mysqladmin ping`)가 통과한 뒤에 시작됩니다. DB/JWT 값은 이미지 빌드 시점이 아니라 컨테이너 실행 시점에 `.env`로부터 주입됩니다.
