# 코딩 컨벤션

sleep2skin 백엔드의 코드 규칙. 구조 설계는 [architecture.md](architecture.md) 참조.

---

## 1. API 응답 포맷

### 공통 래퍼

모든 응답은 `ApiResponse<T>`로 감싼다. 성공과 실패의 모양이 같아야 프론트가 분기를 하나만 두면 된다.

```json
// 성공
{
  "success": true,
  "data": { "darkCircle": 44, "complexion": 72, "barrier": 78 },
  "error": null
}

// 실패
{
  "success": false,
  "data": null,
  "error": {
    "code": "SLEEP_SESSION_NOT_FOUND",
    "message": "수면 데이터가 없어 예보를 산출할 수 없습니다."
  }
}
```

```java
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorResponse.from(errorCode));
    }
}
```

HTTP 상태 코드는 그대로 살린다. `success` 필드와 중복이지만 프론트·모니터링 양쪽에서 쓰이므로 둘 다 둔다.

### Controller 반환 타입

```java
@GetMapping("/forecast")
public ApiResponse<SkinForecastResponse> getForecast(@RequestParam Long userId) {
    return ApiResponse.success(skinForecastService.getForecast(userId, LocalDate.now()));
}
```

`ResponseEntity`는 상태 코드를 직접 제어해야 할 때만 쓴다. 대부분의 경우 불필요하다.

**헬스체크도 예외가 아니다.** `GET /api/v1/health`도 래퍼에 담아 반환한다.

### ⚠️ Swagger `@ApiResponse`와 이름이 겹친다

`io.swagger.v3.oas.annotations.responses.ApiResponse`와 우리 래퍼의 이름이 같다. 우리 것을 import하고 **Swagger 어노테이션을 완전 수식**한다.

```java
import com.allday.sleep2skin_be.global.response.ApiResponse;

@Operation(summary = "오늘의 피부 예보 조회")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
@GetMapping("/forecast")
public ApiResponse<SkinForecastResponse> getForecast(...) { ... }
```

반환 타입이 매번 등장하는 쪽을 짧게 두는 게 낫다. 모든 Controller에서 반복되는 패턴이니 미리 알아두자.

---

## 2. 에러 처리

### 에러 코드 체계

이 서비스는 **빈 상태가 정상 흐름**이다 — 수면 데이터가 없거나, 검증 이력이 없거나, 기록이 7일 미만인 상황이 예외가 아니라 일상이다. 이걸 전부 에러 코드로 관리해 프론트가 빈 상태 UI를 정확히 분기하게 한다.

실제 코드는 **`global/exception/ErrorCode.java`가 단일 출처**다. 여기에 목록을 복사해두지 않는다 — 두 곳에 있으면 어긋난다.

```java
@Getter
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    ...
    // ===== 수면 =====
    SLEEP_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "수면 데이터가 없습니다. 앱에서 수면 기록을 동기화해주세요."),
    ...

    private final HttpStatus status;
    private final String message;
}
```

**네이밍**: `{도메인}_{상황}`. 대문자 스네이크. 프론트가 문자열로 분기하므로 한번 정하면 함부로 바꾸지 않는다.

**도메인별 구역을 지켜 추가한다.** 여러 명이 동시에 건드리면 충돌이 잦은 파일이다.

### 빈 상태는 200, 에러는 4xx

**엔드포인트의 성격에 따라 나눈다.**

| 상황 | 처리 | 예 |
|---|---|---|
| **아직 데이터가 없다** — 조회 API | **200 + 상태 필드** | `GET /skin/forecast` 수면 데이터 없음 |
| **있어야 할 게 없다** — 동작 API | **4xx + ErrorCode** | `POST /skin/selfie` 대조할 예보 없음 |

같은 "예보 없음"도 맥락에 따라 갈린다. 조회는 안내 문구를 보여주면 되지만, 검증은 대조 기준이 없어 **동작 자체가 불가능**하다.

```json
// 조회 — 빈 상태 (200)
{
  "success": true,
  "data": {
    "status": "NO_SLEEP_DATA",
    "message": "수면 데이터가 없어 오늘은 예보가 없습니다.",
    "baseDate": "2026-08-05",
    "forecast": null
  },
  "error": null
}

// 조회 — 정상 (200)
{
  "success": true,
  "data": {
    "status": "AVAILABLE",
    "message": null,
    "baseDate": "2026-08-05",
    "forecast": { "darkCircle": 44, "complexion": 72, "barrier": 78 }
  },
  "error": null
}
```

**모든 조회 API가 `{status, message, 페이로드}` 형태를 공유한다.** 리포트(REP-06 기록 7일 미만), 배너(HOME-09 검증 이력 없음)도 같은 모양을 쓴다. 화면마다 다른 스키마가 생기지 않게 하는 것이 이 규칙의 핵심이다.

**빈 상태를 4xx로 내보내지 않는 이유**는 신규 사용자와 미연결 사용자에게 **일상적으로 발생**하기 때문이다. 404로 내리면 경로 오타·잘못된 userId와 섞여, 모니터링에서 신규 유입이 에러 급증으로 보인다.

`SLEEP_SESSION_NOT_FOUND` 같은 코드는 **지우지 않는다.** 동작 API에서는 여전히 진짜 에러다. 코드는 그대로 두고 쓰는 자리만 구분한다.

**메시지는 사용자에게 그대로 보여줄 수 있는 한국어 문장**으로 쓴다. 개발자용 상세 정보는 로그로 남긴다.

### 예외 클래스

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

도메인별 하위 예외를 만들지 않는다. `ErrorCode` enum이 이미 도메인을 구분하므로 클래스를 늘리면 중복이다.

```java
// Service에서
SkinForecast forecast = forecastRepository.findByUserIdAndBaseDate(userId, today)
        .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_FORECAST_NOT_FOUND));
```

### 전역 예외 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(...) { ... }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);   // 스택트레이스는 로그로만
        return ...;                          // 응답에는 INTERNAL_ERROR만
    }
}
```

**예외 스택트레이스나 내부 메시지를 응답에 노출하지 않는다.**

---

## 3. DTO

### record로 통일

모든 DTO는 `record`다. Lombok `@Getter`/`@Builder`를 쓴 DTO 클래스를 만들지 않는다.

```java
@Schema(description = "오늘의 피부 예보")
public record SkinForecastResponse(

        @Schema(description = "다크서클 회복 점수 (0~100, 높을수록 맑음)", example = "44")
        int darkCircle,

        @Schema(description = "혈색 점수 (0~100, 높을수록 생기 있음)", example = "72")
        int complexion,

        @Schema(description = "장벽 점수 (0~100, 높을수록 튼튼함)", example = "78")
        int barrier,

        @Schema(description = "산출 기준일", example = "2026-08-04")
        LocalDate baseDate
) {
    public static SkinForecastResponse from(SkinForecast entity) {
        return new SkinForecastResponse(
                entity.getDarkCircle(), entity.getComplexion(),
                entity.getBarrier(), entity.getBaseDate());
    }
}
```

**정적 팩토리 메서드로 Entity → DTO 변환을 담는다.** `HealthCheckResponse.up()`이 이미 이 패턴이다. 별도 Mapper 클래스를 만들지 않는다.

### 패키지 배치

```
domain/{도메인}/dto/request/     요청 DTO
domain/{도메인}/dto/response/    응답 DTO
```

DTO가 적은 도메인은 `dto/` 하나로 둬도 된다. `health` 도메인이 그 예다.

### 네이밍

| 용도 | 접미사 | 예 |
|---|---|---|
| 요청 | `Request` | `SleepSessionUploadRequest` |
| 응답 | `Response` | `SkinForecastResponse` |
| 서비스 내부 전달 | `Command` / `Result` | `ForecastCommand` |

### 검증

요청 DTO에 Bean Validation을 붙인다.

```java
public record SleepSessionUploadRequest(
        @NotNull(message = "수면 일자는 필수입니다.")
        LocalDate sleepDate,

        @Min(value = 0, message = "깊은 수면 시간은 0 이상이어야 합니다.")
        int deepSleepMinutes
) {}
```

> 각성 횟수·각성 총 시간은 **요청 DTO에 넣지 않는다.** 앱이 보낸 단계 구간에서 서버가 5분 임계값으로 계산한다 ([erd.md](erd.md) §3.3).

Controller에서 `@Valid`를 붙인다. Service에서 다시 null 체크하지 않는다.

---

## 4. Entity

### 기본 규칙

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "skin_forecast",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "base_date"})
)
public class SkinForecast extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate baseDate;

    private int darkCircle;
    private int complexion;
    private int barrier;

    @Builder
    private SkinForecast(Long userId, LocalDate baseDate, int darkCircle, int complexion, int barrier) { ... }
}
```

| 규칙 | 이유 |
|---|---|
| `@Setter` 금지 | 상태 변경은 의미 있는 이름의 메서드로 (`updateScores()` 등) |
| `@NoArgsConstructor(PROTECTED)` | JPA가 요구하지만 외부에서 못 쓰게 |
| 생성자는 `private` + `@Builder` | 유효하지 않은 상태의 객체 생성 차단 |
| `@Data`, `@ToString` 금지 | 연관관계 순환 참조로 무한 루프 위험 |
| 연관관계는 `LAZY` | `@ManyToOne(fetch = FetchType.LAZY)` |

### 연관관계 최소화

인증이 없어 `userId`를 `Long`으로 직접 들고 있어도 충분하다. **JPA 연관관계를 남발하지 않는다** — N+1과 순환 참조 비용이 이득보다 크다. 조인이 꼭 필요한 곳만 연관관계로 맺는다.

### ID 생성 전략

MySQL이므로 `GenerationType.IDENTITY`를 쓴다.

---

## 5. Lombok 사용 범위

허용 범위를 좁게 잡는다. Lombok이 만든 코드는 리뷰에서 안 보이기 때문이다.

| 어노테이션 | 사용처 | 비고 |
|---|---|---|
| `@Getter` | Entity | 클래스 레벨 |
| `@NoArgsConstructor(PROTECTED)` | Entity | JPA 요구사항 |
| `@Builder` | Entity 생성자 | 생성자에만, 클래스에 붙이지 않음 |
| `@RequiredArgsConstructor` | Service, Controller | 생성자 주입용 |
| `@Slf4j` | 로깅이 필요한 곳 | |

**금지**: `@Data`, `@Setter`, `@AllArgsConstructor`, `@ToString`, `@EqualsAndHashCode`, `@Value`

**DTO에는 Lombok을 쓰지 않는다.** record가 대신한다.

---

## 6. 의존성 주입

생성자 주입만 사용한다. `@Autowired` 필드 주입을 쓰지 않는다.

```java
@Service
@RequiredArgsConstructor
public class SkinForecastService {
    private final SkinForecastRepository forecastRepository;
    private final SleepSessionRepository sleepSessionRepository;
}
```

생성자가 하나면 `@Autowired`도 생략한다. `HealthCheckController`가 `@Value` 주입을 생성자로 받는 것과 같은 방식이다.

---

## 7. 트랜잭션

```java
@Service
@Transactional(readOnly = true)      // 클래스 기본값: 읽기 전용
@RequiredArgsConstructor
public class SkinForecastService {

    @Transactional                    // 쓰기 메서드에만 재선언
    public SkinForecastResponse createForecast(...) { ... }

    public SkinForecastResponse getForecast(...) { ... }   // 읽기 — 기본값 적용
}
```

**외부 API 호출을 트랜잭션 안에 넣지 않는다.** OpenAI Vision 호출은 최대 30초가 걸릴 수 있어 커넥션을 붙잡으면 커넥션 풀이 고갈된다. 분석 → 저장을 분리한다.

---

## 8. API 설계

### 경로 규칙

```
/api/v1/{도메인}/{리소스}
```

| 기능 | 메서드 | 경로 |
|---|---|---|
| 헬스체크 | GET | `/api/v1/health` |
| 수면 세션 업로드 | POST | `/api/v1/sleep/sessions` |
| 오늘의 예보 | GET | `/api/v1/skin/forecast` |
| 셀피 분석·검증 | POST | `/api/v1/skin/selfie` |
| 오늘의 TODO | GET | `/api/v1/todo` |
| TODO 체크 | PATCH | `/api/v1/todo/{id}/complete` |
| 일간 리포트 | GET | `/api/v1/report/daily` |
| 주간 리포트 | GET | `/api/v1/report/weekly` |

- 리소스는 복수형 (`sessions`), 단일 개념은 단수 (`forecast`)
- 동사를 경로에 넣지 않는다 — 상태 변경은 HTTP 메서드로 표현
- `@RequestMapping("/api/v1/skin")`을 클래스에 두고 메서드에 하위 경로를 붙인다

### Swagger 문서화

모든 Controller와 DTO에 문서화 어노테이션을 붙인다. **한국어로 쓴다.**

```java
@Tag(name = "Skin", description = "피부 예보 및 셀피 검증 API")
@RestController
@RequestMapping("/api/v1/skin")
public class SkinController {

    @Operation(summary = "오늘의 피부 예보 조회",
               description = "직전 수면 데이터를 기반으로 산출된 지표 3종을 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "수면 데이터 없음")
    @GetMapping("/forecast")
    public ApiResponse<SkinForecastResponse> getForecast(...) { ... }
}
```

DTO 필드에는 `@Schema(description = ..., example = ...)`를 붙인다. **example을 반드시 채운다** — 프론트가 Swagger UI만 보고 개발할 수 있어야 한다.

`SwaggerConfig`와 `HealthCheckController`가 이 패턴의 기준이다.

---

## 9. 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| 패키지 | 소문자, 단수 | `skin`, `sleep`, `report` |
| 클래스 | PascalCase | `SkinForecastService` |
| 메서드·변수 | camelCase | `calculateForecast` |
| 상수·enum | UPPER_SNAKE_CASE | `DARK_CIRCLE`, `SKIN_FORECAST_NOT_FOUND` |
| DB 테이블·컬럼 | snake_case | `skin_forecast`, `base_date` |

### 도메인 용어 고정

코드 전반에서 같은 개념에 같은 단어를 쓴다.

| 개념 | 코드 용어 | 쓰지 말 것 |
|---|---|---|
| 피부 예보 (수면 기반 예측값) | `forecast` | prediction, estimate |
| 셀피 실측값 | `measurement` | actual, measured |
| 예보 vs 실측 대조 | `verification` | validation, check |
| 개인 가중치 | `weight` | factor, coefficient |
| 기준일 | `baseDate` | targetDate, date |
| 수면 일자 | `sleepDate` | — |

### 지표 enum

```java
public enum SkinMetric {
    DARK_CIRCLE,   // 다크서클 회복
    COMPLEXION,    // 혈색
    BARRIER        // 장벽
}
```

**셋 다 0~100이고 높을수록 좋은 상태다.** `DARK_CIRCLE`은 "다크서클이 심한 정도"가 아니라 **"회복된 정도"**다 — 각성이 많은 밤일수록 점수가 내려간다. UI 표시명이 "다크서클 회복"인 이유다. 예보·실측 양쪽이 같은 방향이어야 HOME-07 대조가 성립한다.

**3종 고정이다.** 유분(sebum), 칙칙함(dullness), 색소침착(pigmentation)은 기능명세서 초안에 등장하지만 최종 확정에서 제외됐다. 코드에 추가하지 않는다.

### 피처 enum

```java
public enum SleepFeature {
    AWAKE_COUNT,          // 야간 각성 횟수      → DARK_CIRCLE
    TOTAL_SLEEP,          // 총 수면 시간        → DARK_CIRCLE
    DEEP_SLEEP,           // 깊은 수면 시간      → BARRIER
    REM_SLEEP,            // REM 수면 시간       → BARRIER
    BEDTIME_REGULARITY,   // 취침 규칙성         → COMPLEXION
    HRV,                  // 심박변이도          → COMPLEXION  (결측 가능)
    RESTING_HEART_RATE    // 안정시 심박         → COMPLEXION  (결측 가능)
}
```

**저장하는 값과 피처는 다르다.** 코어 수면·기상 시각은 `sleep_session`에 저장하지만 피처가 아니다. 반대로 `BEDTIME_REGULARITY`는 컬럼이 아니라 `sleep_onset_time`에서 파생된다. → [prd.md](prd.md) §4.1 세 층위 표

**피처 → 지표 매핑을 `SleepFeature`에 필드로 박지 않는다** (PRD §10.3). `ScoringPolicy`가 매핑을 들고 있게 한다 — 확정값이 됐어도 조정될 수 있고, 그때 enum이 아니라 정책 한 파일만 바뀌어야 한다. **정규화 구간(§10.5)도 같은 이유로 enum이 아니라 `ScoringPolicy`에 둔다.**

**`AWAKE_MINUTES`(각성 총 시간)는 여기 없다.** 수집·저장은 하지만 **표시 전용으로 확정**되어 피처가 아니다 (PRD §10.3). 각성 횟수와 중복 상관이 강해 넣지 않기로 한 것이지 배정을 미룬 게 아니다 — **추가하지 말 것.**

**`DEEP_SLEEP`·`REM_SLEEP`에 넘기는 값은 분이 아니라 총 수면 대비 비율(%)이다** (PRD §10.5). enum 이름은 분처럼 읽히지만 정규화 입력 단위가 다르다 — 이 지점이 헷갈리면 장벽 점수만 조용히 틀린다.

---

## 10. 주석과 언어

| 대상 | 언어 |
|---|---|
| 주석 | 한국어 |
| Swagger 설명 (`@Tag`, `@Operation`, `@Schema`) | 한국어 |
| 에러 메시지 | 한국어 |
| 클래스·메서드·변수명 | 영어 |
| 커밋 메시지 | 한국어 |

### 주석을 쓸 때

코드가 **무엇을 하는지**는 쓰지 않는다. **왜 그렇게 했는지**를 쓴다.

```java
// 나쁨 — 코드를 그대로 읽은 것
// 예보를 조회한다
SkinForecast forecast = forecastRepository.findByUserIdAndBaseDate(...);

// 좋음 — 코드에 안 드러나는 이유
// 예보 없이는 대조 기준이 없어 검증 자체가 성립하지 않는다
SkinForecast forecast = forecastRepository.findByUserIdAndBaseDate(...)
        .orElseThrow(() -> new BusinessException(SKIN_FORECAST_NOT_FOUND));
```

산출 공식·임계값처럼 **근거가 코드 밖에 있는 것**에는 주석으로 출처를 남긴다.

```java
// 야간 각성 1회당 다크서클 점수 -8점 (PRD §4.4 REP-07 상관 쌍 기준)
```

---

## 11. 테스트

### 최소 기준

- 스코어링·판정·학습 로직은 **단위 테스트 필수** — 숫자가 틀리면 제품 전체가 틀린다
- Controller는 `@WebMvcTest`로 요청·응답 계약 검증
- 외부 연동(OpenAI Vision)은 스텁으로 대체. 실제 API를 테스트에서 호출하지 않는다 — `SkinVisionClient` 인터페이스가 그 자리다

```
src/test/java/com/allday/sleep2skin_be/
├── domain/
│   └── skin/
│       ├── SkinForecastServiceTest.java    스코어링 로직
│       └── SkinControllerTest.java         @WebMvcTest
└── Sleep2skinBeApplicationTests.java       컨텍스트 로딩
```

`HealthCheckControllerTest`가 기존 패턴이다.

### 테스트 메서드 이름

한국어로 무엇을 검증하는지 쓴다.

```java
@Test
@DisplayName("야간 각성이 많을수록 다크서클 점수가 낮아진다")
void 야간_각성이_많으면_다크서클_점수가_낮다() { ... }
```

---

## 12. 참고

- 아키텍처: [architecture.md](architecture.md)
- 브랜치·협업: [workflow.md](workflow.md)
- 제품 요구사항: [prd.md](prd.md)
