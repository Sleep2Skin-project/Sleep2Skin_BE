# 코딩 컨벤션

sleep2skin 백엔드의 코드 규칙. 구조 설계는 [architecture.md](architecture.md) 참조.

---

## 1. API 응답 포맷

### 공통 래퍼

모든 응답은 `ApiResponse<T>`로 감싼다. 프론트는 `success` 하나로 분기한다.

```json
// 성공
{
  "success": true,
  "data": { "darkCircle": 44, "complexion": 72, "barrier": 78 }
}

// 실패
{
  "success": false,
  "error": {
    "code": "SLEEP_SESSION_NOT_FOUND",
    "message": "수면 데이터가 없어 예보를 산출할 수 없습니다."
  }
}
```

**비어 있는 쪽은 직렬화하지 않는다.** `success`가 이미 같은 정보를 담고 있어 `"error": null`은 중복이다. `ApiResponse`에 붙은 `@JsonInclude(NON_NULL)`이 이 동작을 만든다.

⚠️ **이 설정을 전역(`spring.jackson.default-property-inclusion`)으로 올리지 말 것.** 그러면 중첩 DTO의 `null`까지 사라지는데, 이 서비스에서 페이로드의 `null`은 의미 있는 값이다 — 아래 빈 상태 예시의 `"forecast": null`·`"message": null`이 그렇고, 예보 응답의 `"complexion": null`은 "그 지표를 산출할 수 없었다"는 뜻이라 `unavailable`의 사유와 짝을 이룬다([api.md](api.md) §3). 키가 통째로 없어지면 클라이언트가 **산출 불가와 키 이름 오류를 구분할 수 없다.** 클래스 단위 어노테이션이라 래퍼의 두 필드에만 적용된다.

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

> **`GET /api/v1/todo`가 한때 예외였다** (2026-08-13 → 08-14 수정). 목록 생성을 겸한다는 이유로 예보가 없을 때 `404`를 냈는데, **수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면 매번 받는 응답**이라 위 표의 "일상적으로 발생한다"에 정확히 해당했다. 지금은 `200` + `NO_SLEEP_DATA`다.
>
> **엔드포인트가 쓰기를 겸한다는 것은 예외의 근거가 되지 못한다.** 갈리는 기준은 "무엇을 하는 API인가"가 아니라 **"그 빈 상태가 사용자에게 일상적인가"** 다.

```json
// 조회 — 빈 상태 (200)
{
  "success": true,
  "data": {
    "status": "NO_SLEEP_DATA",
    "message": "수면 데이터가 없어 오늘은 예보가 없습니다.",
    "baseDate": "2026-08-05",
    "forecast": null
  }
}

// 조회 — 정상 (200)
{
  "success": true,
  "data": {
    "status": "AVAILABLE",
    "message": null,
    "baseDate": "2026-08-05",
    "forecast": { "darkCircle": 44, "complexion": 72, "barrier": 78 }
  }
}
```

두 예시 안쪽의 `"forecast": null`·`"message": null`은 **그대로 나온다.** 사라지는 것은 래퍼의 `error`뿐이다.

**모든 조회 API가 `{status, message, 페이로드}` 형태를 공유한다.** 배너(HOME-09 검증 이력 없음), 리포트도 같은 모양을 쓴다. 화면마다 다른 스키마가 생기지 않게 하는 것이 이 규칙의 핵심이다.

#### 변형이 둘 있다 — 모양은 같고 위치·값 집합만 다르다 (2026-08-15, `report`)

**① 상태가 응답 최상위가 아니라 섹션마다 붙는 경우.** `GET /report/daily`는 `sleepSummary`·`skinForecast` 두 섹션이 각자 `{status, message, 페이로드}`를 갖는다. **두 섹션이 서로 무관하게 비기 때문**이다 — 검증을 마친 날의 예보는 세션이 갱신돼도 재산출되지 않아서, 세션 유무와 예보 유무가 항상 같이 가지 않는다. **하나로 감싸면 한쪽이 없다는 이유로 있는 쪽 데이터까지 숨긴다.**

> **섹션을 나누는 기준은 화면이 아니라 "독립적으로 빌 수 있는가"다.** 한 기간·한 세션의 존재 여부가 응답 전체를 가르면(`GET /report/daily/timeline`) 최상위 상태 하나로 충분하다.

**② `QueryStatus`가 아닌 별도 enum을 쓰는 경우.** 주간·월간은 `ReportPeriodStatus`(`FULL`·`INSUFFICIENT_DATA`)다. **값 이름 자체가 "가입 후 그 기간만큼 지났는가"라는 이 두 API 고유의 의미**라 `QueryStatus`의 기존 값 중 대응하는 것이 없다. **`NO_SLEEP_DATA`로 뭉뚱그리지 않는다** — 그건 "그날 안 잤다"이고 이건 "아직 신규 사용자다"라서, 앱이 보여줄 문구와 다음 행동이 다르다.

**억지로 한 enum에 몰지 말 것.** 값이 늘어나면 어느 API가 어느 값을 낼 수 있는지 문서로만 알 수 있게 되고, 앱은 나올 리 없는 분기까지 다 처리하게 된다.

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

- 리소스는 복수형 (`sessions`), 단일 개념은 단수 (`forecast`)
- **동사를 경로에 넣지 않는다** — 상태 변경은 HTTP 메서드와 본문으로 표현한다
- `@RequestMapping("/api/v1/skin")`을 클래스에 두고 메서드에 하위 경로를 붙인다

### 공통 규약

| 규약 | 내용 |
|---|---|
| **사용자 식별** | `X-User-Id` 헤더 — 모든 API 공통 |
| **기준일** | `baseDate` 쿼리 파라미터 — 날짜가 필요한 API 전부 (조회·동작 무관) |
| **시각 형식** | ISO 8601 **오프셋 포함** (`2026-08-07T07:10:00+09:00`) |

**헤더로 받는 이유는 JWT 도입 비용이다.** 나중에 토큰에서 `userId`를 꺼내게 되면 **헤더를 읽던 자리만 바뀌고** 경로·시그니처·DTO는 그대로다. 쿼리 파라미터로 받으면 API마다 파라미터를 지워야 하고, 경로 변수로 받으면 전 경로를 갈아야 한다. Swagger에는 전역 헤더 파라미터로 등록해 UI에서 한 번만 입력하게 한다.

**`baseDate`를 받는 이유는 서버가 "오늘"을 모르기 때문이다.** `users`에 `time_zone`을 두지 않기로 했으므로([erd.md](erd.md) §3.1) 서버 시각(UTC)으로 계산하면 한국 시간 오전 9시 이전에 날짜가 하루 밀린다. `LocalDate`로 받는다.

**동작 API도 날짜가 필요하면 같은 방식으로 받는다.** `POST /skin/selfie`가 그렇다 — 어느 날짜의 예보와 대조할지를 알아야 하는데, 이유는 조회 API와 동일하다. 멀티파트 요청이라도 폼 필드가 아니라 쿼리 파라미터에 둔다. **받는 자리가 API마다 다르면 규약이 아니다.**

**시각에 오프셋이 없으면** 서버가 UTC로 해석해 `sleepDate`가 밀리고, 그 날짜로 조인되는 예보·검증이 전부 어긋난다.

### 엔드포인트 목록은 [api.md](api.md)에 있다

**경로·요청·응답의 유일한 출처는 [api.md](api.md)다.** 이 문서에는 옮겨 적지 않는다 — 두 곳에 두면 어긋난다.

api.md에 있는 것: 도메인별 엔드포인트 19개 · 각 API 설명 · `POST /sleep/sessions` 상세 규격 · 구현 순서와 **남은 정리 작업** · **MVP에서 만들지 않는 것**

### Swagger 문서화

**프론트는 Swagger UI만 보고 개발한다.** 그래서 문서가 틀리면 앱이 틀리게 만들어진다. 아래 규칙은 전부 실제로 한 번씩 어긋났던 자리다 — `UserControllerSpec`과 `SwaggerConfig`가 기준 구현이다.

한국어로 쓴다.

#### 문서는 `{도메인}ControllerSpec` 인터페이스에 둔다

Controller는 매핑과 위임만 갖고, `@Tag`·`@Operation`·`@ApiResponse`는 인터페이스로 뺀다. 설명이 길어질 수밖에 없어서 컨트롤러에 두면 실제 코드가 어노테이션에 파묻힌다.

```java
@Tag(name = "User", description = "사용자 · 동의 · 온보딩 API")
public interface UserControllerSpec {
    @Operation(summary = "...", description = "...")
    ApiResponse<Xxx> doSomething(@CurrentUserId Long userId);
}

@RestController
public class UserController implements UserControllerSpec {
    @Override @PostMapping("/me/consents")
    public ApiResponse<Xxx> doSomething(@CurrentUserId Long userId) { ... }   // ← 다시 붙인다
}
```

⚠️ **`@CurrentUserId`는 구현체에도 반드시 붙인다.** 파라미터 어노테이션은 **인터페이스에서 상속되지 않아** 인터페이스에만 두면 리졸버가 파라미터를 인식하지 못하고 헤더가 주입되지 않는다.

⚠️ **springdoc이 인터페이스 어노테이션을 못 찾으면 설명만 사라지고 API는 멀쩡히 뜬다.** 컴파일도 테스트도 통과하므로 프론트가 빈 문서를 볼 때까지 아무도 모른다. `SwaggerConfigTest`가 이걸 지킨다.

#### `@Tag` 설명은 한 줄이다

`@Tag` 설명은 Swagger UI에서 태그를 접어도 **계속 펼쳐진 채 목록 맨 위를 차지한다.** 공통 규약을 거기 넣으면 API 목록을 훑기가 불편해진다.

**공통 규약도 API마다 되풀이해 `@Operation` 설명에 적는다.** 읽는 사람은 자기가 쓸 API 하나만 펼치므로 그 안에 다 있는 편이 낫다. 중복이지만 이건 의도된 중복이다.

`@Operation` 설명에 넣을 것: **언제 호출하나 · 요청(헤더·본문) · 응답(코드별 의미) · 재호출 안전성 · 예외 표**.

#### 에러 예시는 손으로 적지 않는다

`SwaggerConfig`가 `ErrorCode`를 순회해 코드별 예시를 `components.examples`에 등록해 둔다. 각 API는 **어떤 코드가 나오는지만** 고른다.

```java
@ApiResponse(responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
        content = @Content(mediaType = "application/json",
                examples = @ExampleObject(name = "USER_NOT_FOUND",
                        ref = "#/components/examples/USER_NOT_FOUND")))
```

⚠️ **`ErrorResponse`에 `@Schema(example = ...)`를 다시 넣지 말 것.** 이 레코드는 모든 API가 공유하는 스키마 하나라, 예시를 박으면 도메인·상황과 무관하게 전부 같은 값이 나온다. 실제로 user API 문서에 `SLEEP_SESSION_NOT_FOUND`가 떴었고, **그 메시지는 `ErrorCode`의 실제 문구와 달라 서버가 한 번도 보낸 적 없는 문장이었다.**

같은 이유로 `@ExampleObject`에 JSON을 직접 쓰지 않는다. 메시지를 복붙하는 순간 `ErrorCode`가 바뀌면 문서만 조용히 틀린다.

#### springdoc이 알아서 해주지 않는 것

| 함정 | 무슨 일이 나나 | 대응 |
|---|---|---|
| `@ApiResponse`에 `content`가 없다 | **선언한 상태 코드 전부를 메서드 반환 타입으로 채운다.** 404를 펼치면 `data`가 채워진 성공 예시가 나온다 | 에러 응답에 예시 `ref`를 붙인다. `SwaggerConfig`가 스키마도 실패 래퍼로 맞춘다 |
| `@JsonInclude(NON_NULL)` | **런타임 직렬화만 바꾸고 springdoc은 읽지 않는다.** 실제로 안 나가는 필드가 스키마에 남는다 | `SwaggerConfig`에서 스키마를 손본다 |
| `produces` 미선언 | 응답 미디어 타입이 `*/*`로 문서화된다 | `springdoc.default-produces-media-type`으로 이미 설정돼 있다. **컨트롤러에 `produces`를 붙이지 말 것** — 콘텐츠 협상까지 제약해 동작이 바뀐다 |

#### DTO

DTO 필드에는 `@Schema(description = ..., example = ...)`를 붙이고 **example을 반드시 채운다.** 단, 위의 `ErrorResponse`처럼 **여러 API가 공유하는 DTO**는 예외다 — 한 곳에 박은 예시가 모든 API에 나간다.

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

**`DEEP_SLEEP`·`REM_SLEEP`에 넘기는 값은 분이 아니라 비율(%)이고, 분모는 `deep + rem + core`다** (PRD §10.5). `total_sleep_minutes`가 아니다 — 단계 미상 구간이 분모에 들어가면 측정 못 한 시간이 "깊은 수면이 아니었던 시간"으로 계산된다. enum 이름은 분처럼 읽히지만 정규화 입력 단위가 다르다 — 이 지점이 헷갈리면 장벽 점수만 조용히 틀린다.

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

### API 문서도 테스트로 지킨다

**문서가 어긋나도 서버 테스트는 전부 통과한다.** 문서는 프론트만 보기 때문에, 앱이 잘못 구현한 뒤에야 드러난다. `/v3/api-docs`를 직접 읽는 테스트를 **두 층**으로 둔다.

| 테스트 | 무엇을 보나 | 새 API를 추가하면 |
|---|---|---|
| `global/config/SwaggerConfigTest` | 도메인 무관 규칙 — 모든 4xx가 실패 스키마를 가리키는가 · 모든 응답이 `application/json`인가 · 성공 래퍼에 `error`가 없는가 · 예시가 `ErrorCode`에서 생성됐는가 | **손대지 않는다.** 경로를 짚지 않고 문서 전체를 순회하므로 자동으로 검증 대상이 된다 |
| `domain/{도메인}/{도메인}ApiDocsTest` | 그 도메인만의 것 — 설명이 실렸는가 · 어떤 에러 예시를 골랐는가 · 헤더가 붙었는가 | **여기에 추가한다** |

**도메인별로 나눈 이유는 나중에 도메인 단위로 작업을 나누기 위해서다**(workflow.md §4). 한 파일에 모아두면 두 사람이 계속 같은 파일을 건드린다.

예시 문구는 **`ErrorCode`의 값과 대조한다.** 테스트에 문자열을 손으로 적으면 그 문자열도 같이 낡는다.

⚠️ **문서 전체를 순회하는 검사는 순회가 비면 통과한다.** 검사한 개수가 0이 아닌지 함께 단언한다.

```java
.andExpect(jsonPath("$.components.examples.USER_NOT_FOUND.value.error.message")
        .value(ErrorCode.USER_NOT_FOUND.getMessage()))
```

### ⚠️ `doesNotExist()`가 통과했다고 검증된 게 아니다

두 가지 경우에 **아무것도 검증하지 않고 통과한다.**

- **값이 명시적 `null`일 때** — JsonPath가 `null`을 부재로 취급한다. `"error": null`이 그대로 실려 나가도 `jsonPath("$.error").doesNotExist()`는 통과한다. 키가 정말 없는지 보려면 **응답 원문**을 본다.
- **경로 표현식이 틀렸을 때** — 오타 하나로 검증이 조용히 무력해진다. 같은 경로에 **양성 단언을 먼저** 둬서 경로가 실제로 잡히는지 확인한다.

```java
// 키 자체가 없는지 — 원문을 본다
.andExpect(content().string(not(containsString("\"error\""))))

// 경로가 잡히는지 먼저 확인한 뒤 부재를 단언한다
.andExpect(jsonPath("$.paths.['/api/v1/health'].get.operationId").exists())
.andExpect(jsonPath("$.paths.['/api/v1/health'].get.parameters").doesNotExist())
```

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
