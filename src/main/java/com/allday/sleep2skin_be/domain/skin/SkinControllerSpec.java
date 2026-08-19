package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * {@link SkinController}의 API 문서.
 */
@Tag(name = "Skin", description = "피부 예보 · 검증 · 개인 모델 API")
public interface SkinControllerSpec {

    @Operation(summary = "오늘의 피부 예보 조회 (HOME-03)", description = """
            그날 산출해둔 피부 예보를 조회한다.

            ### 언제 호출하나

            홈 화면을 그릴 때. 다만 **앱 시작 직후라면 이 API가 필요 없다** —
            `POST /api/v1/sleep/sessions`가 업로드 응답에 같은 예보를 실어 보낸다.
            여기는 **이미 받은 예보를 날짜를 바꿔 다시 볼 때**(어제 예보 등) 쓴다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

            **`baseDate`는 필수다.** 서버는 "오늘"이 언제인지 모른다 — 사용자의 타임존을 저장하지
            않기 때문이다. 서버 시각(UTC)으로 정하면 **한국 시간 오전 9시 이전에 날짜가 하루 밀린다.**
            앱이 자기 로컬 날짜를 `YYYY-MM-DD`로 보낸다.

            `baseDate`는 **기상일 기준**이다. 어젯밤 자고 오늘 아침 일어난 수면의 예보를 보려면
            오늘 날짜를 보낸다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "AVAILABLE",
                "message": null,
                "baseDate": "2026-08-07",
                "forecast": {
                  "darkCircle": { "score": 67, "grade": "NORMAL" },
                  "complexion": { "score": 62, "grade": "NORMAL" },
                  "barrier":    { "score": 81, "grade": "STABLE" },
                  "unavailable": []
                }
              } }
            ```

            **점수는 셋 다 0~100이고 높을수록 좋은 상태다.** `darkCircle`은 "다크서클이 심한 정도"가
            아니라 **"회복된 정도"** 다 — 각성이 많은 밤일수록 점수가 내려간다. 화면 표시명은
            "다크서클 회복"이다.

            | 등급 | 점수 | 의미 |
            |---|---|---|
            | `RISK` | 0~25 | 피부 컨디션 저하 가능성이 높음 |
            | `CAUTION` | 26~50 | 피부 컨디션 저하 가능성 있음 |
            | `NORMAL` | 51~75 | 일반적인 수준 |
            | `STABLE` | 76~100 | 수면으로 인한 피부 영향이 낮음 |

            ### 빈 상태 — 에러가 아니다

            **그날 수면 데이터가 없으면 `200`에 `status: NO_SLEEP_DATA`로 나간다.** 404가 아니다.
            신규 사용자나 앱을 아직 켜지 않은 사용자에게 일상적으로 발생하기 때문이다.

            ```jsonc
            { "success": true,
              "data": {
                "status": "NO_SLEEP_DATA",
                "message": "수면 데이터가 없어 오늘은 예보가 없습니다.",
                "baseDate": "2026-08-07",
                "forecast": null
              } }
            ```

            **`status`로 분기한다.** `message`는 그대로 보여줄 수 있는 문장이지만 다듬어질 수 있어
            분기 조건으로 쓰지 않는다.

            ### 예보는 있는데 일부 지표만 없을 수 있다

            **위의 빈 상태와는 다른 층위다.** `status`는 `AVAILABLE`이고 예보도 있는데, 그중 한
            지표만 산출하지 못한 경우다.

            ```jsonc
            "complexion": null,
            "unavailable": [ { "metric": "COMPLEXION", "reason": "MISSING_FEATURES" } ]
            ```

            | `reason` | 언제 | 앱이 보여줄 것 |
            |---|---|---|
            | `MISSING_FEATURES` | 워치 미착용 — HRV·안정시 심박 없음 | 워치를 착용하고 자면 다음날 산출된다 |
            | `NO_SLEEP_STAGES` | 단계 합이 0 — 피부 장벽 산출 불가 | 측정하지 못한 것이지 위험한 것이 아니다 |
            | `INSUFFICIENT_HISTORY` | 취침 규칙성 이력 3일 미만 | 기록이 쌓이면 산출된다 |

            **`darkCircle`은 항상 값이 있다.** 나머지 둘만 `null`이 될 수 있다.

            ### 예외

            실패 응답은 `{ "success": false, "error": { "code": ..., "message": ... } }` 모양이다.
            **분기는 `error.code`(문자열)로 한다.**

            | 코드 | `error.code` | 언제 | 앱이 할 일 |
            |---|---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate`가 없거나 `YYYY-MM-DD` 형식이 아님 | 요청 버그다 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 | 헤더를 넣고 다시 호출 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 | 시딩된 테스트 유저 ID인지 확인 |
            | `500` | `INTERNAL_ERROR` | 서버 오류 | 재시도 안내 |

            **여기에 `SKIN_FORECAST_NOT_FOUND`는 없다.** 예보가 없는 것은 위의 빈 상태로 처리한다.
            그 코드는 셀피 검증(`POST /skin/selfie`)처럼 **대조할 기준이 없으면 동작 자체가
            불가능한** 곳에서만 쓴다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **예보가 없는 경우도 여기에 해당한다** (`status: NO_SLEEP_DATA`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`INVALID_INPUT` — `baseDate` 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "INVALID_INPUT",
                    ref = "#/components/examples/INVALID_INPUT")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<SkinForecastQueryResponse> getForecast(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). **기상일 기준이며 앱의 로컬 날짜를 보낸다**",
                    required = true, example = "2026-08-07")
            LocalDate baseDate);

    @Operation(summary = "셀피 분석·검증·학습 (HOME-06→07→08)", description = """
            셀피를 분석해 피부 지표 3종을 실측하고, 그날 예보와 대조해 판정한 뒤,
            오차로 개인 가중치를 보정한다.

            ```
            멀티파트 수신 → 메모리에서 OpenAI Vision 호출     (HOME-06)
            → 지표 3종 실측 저장 → 예보와 대조·판정            (HOME-07)
            → 개인 가중치 보정 → 다음 예보에 즉시 반영          (HOME-08)
            ```

            ### 셀피 원본은 보관하지 않는다

            이미지는 **멀티파트 → 서버 메모리 → 외부 AI(OpenAI)** 로 흐르고, 저장되는 것은
            **지표 숫자 3개뿐**이다. 오브젝트 스토리지를 거치지 않으며 DB에 이미지 경로·URL
            컬럼 자체가 없다.

            사용자 고지 문구는 **주어를 생략하지 않는다**:
            *"분석을 위해 외부 AI(OpenAI)로 이미지가 전송됩니다. **서버에** 저장하지 않으며,
            분석 직후 즉시 삭제하고 얼굴을 복원할 수 있는 데이터를 보관하지 않습니다."*

            ### 요청

            `X-User-Id` 헤더 · `baseDate` 쿼리 파라미터 · `image` 멀티파트 파트.

            **동작 API인데도 `baseDate`를 받는다.** 이 요청은 **어느 날짜의 예보와 대조할지**를
            알아야 하는데 서버는 "오늘"이 언제인지 모른다 — 서버 시각으로 정하면 한국 시간
            오전 9시 이전에 하루 밀린 예보와 대조하게 되고, **값 범위는 정상이라 아무 제약에도
            안 걸리고 적중률만 무너진다.**

            **멀티파트 요청이라도 폼 필드가 아니라 쿼리 파라미터다.** 다른 API와 같은 자리다.

            **과거 날짜도 받는다.** 지난 날짜의 검증을 만들 수 있다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "baseDate": "2026-08-07",
                "analyzedAt": "2026-08-07T12:33:12Z",
                "verifications": [
                  { "metric": "DARK_CIRCLE",
                    "forecast": { "score": 67, "grade": "NORMAL" },
                    "measured": { "score": 61, "grade": "NORMAL" },
                    "difference": 6,
                    "verdict": "CLOSE" },
                  { "metric": "BARRIER",
                    "forecast": { "score": 81, "grade": "STABLE" },
                    "measured": { "score": 78, "grade": "STABLE" },
                    "difference": 3,
                    "verdict": "HIT" }
                ],
                "skipped": [
                  { "metric": "COMPLEXION",
                    "measured": { "score": 55, "grade": "NORMAL" },
                    "reason": "MISSING_FEATURES" }
                ],
                "hitRate": 87,
                "model": {
                  "updated": true,
                  "message": "야간 각성을(를) 조금 더 중요하게 보도록 학습했어요.",
                  "changes": [
                    { "feature": "AWAKE_COUNT", "metric": "DARK_CIRCLE", "label": "야간 각성",
                      "before": 1.0000, "after": 1.0110 },
                    { "feature": "TOTAL_SLEEP", "metric": "DARK_CIRCLE", "label": "총 수면 시간",
                      "before": 1.0000, "after": 0.9890 }
                  ]
                },
                "streakCount": 3,
                "exp": {
                  "gained": 10,
                  "reasons": [ { "reason": "VERIFICATION_STREAK", "amount": 10 } ],
                  "totalExp": 320, "level": 3, "levelUp": false, "nextLevelExp": 450
                }
              } }
            ```

            ### 연속 검증 보상 (HOME-04)

            **`streakCount`는 이번 검증을 포함한 값이다.** 팝업 문구("3일 연속!")와 지급액이 같은
            숫자에서 나와야 한다 — 검증 전 값을 쓰면 화면과 보상이 하루씩 어긋난다. 계산은
            HOME-09 배너·MY-01 프로필·출석 체크인과 같은 곳에서 나온다.

            | `streakCount` | `exp.gained` |
            |---|---|
            | `1` (첫 검증 또는 연속이 끊긴 뒤 첫 검증) | `0` — **보상 구간이 2일부터다** |
            | `2` / `3` / `4` | `+5` / `+10` / `+15` |
            | `5` 이상 | `+25` (상한 구간, 매일) |

            연속이 끊기면 다시 1일차(보상 없음)부터다. **오늘 미검증이 연속을 끊지 않는다** —
            오늘 또는 어제부터 이어져 있으면 유효하다.

            > **분석이 실패하면 행도 exp도 생기지 않는다.** 적립은 저장·검증이 끝난 뒤이며,
            > 검증은 하루 1회(`409`)라 재시도가 이중 지급으로 이어지지 않는다.

            **`difference`는 `예보 − 실측`이다.**

            | `verdict` | 차이 | 의미 |
            |---|---|---|
            | `HIT` | ±5 이내 | 거의 일치 |
            | `CLOSE` | ±6~15 | 비슷하게 예측 |
            | `UNDERESTIMATED` | −16 이하 | 점수를 낮게 예측 = **피부 위험을 과대평가** |
            | `OVERESTIMATED` | +16 이상 | 점수를 높게 예측 = **피부 위험을 과소평가** |

            **점수 축과 위험 축이 반대다.** 문구에서 어느 축인지 밝히지 않으면 뜻이 뒤집힌다.

            ### `hitRate`는 판정 개수가 아니라 정확도 평균이다

            지표마다 `|예보 − 실측|`을 로그 곡선으로 0~100 정확도에 대응시키고 **그 평균**을 낸다.

            | 오차 | 0 | 3 | 5 | 10 | 15 | 20 | 30 | 50 | 100 |
            |---|---|---|---|---|---|---|---|---|---|
            | 정확도 | 100 | 90 | 85 | 75 | 68 | 61 | 52 | 39 | 20 |

            - **`verdict`와 다른 축이다.** 판정은 ±5·±15로 잘린 라벨이고 적중률은 연속값이다.
              **적중(`HIT`)이어도 `100`이 아니다**
            - **`100`은 세 지표가 전부 정확히 맞아야 나오고, 완전히 빗나가도 `20` 밑으로는
              내려가지 않는다**
            - 예전에는 `HIT` 개수 ÷ 판정 수여서 지표가 3개면 `0`·`33`·`67`·`100`만 나왔다.
              **오차 6점과 60점이 똑같이 "적중 아님"으로 뭉개졌다**

            ### 대조하지 못한 지표가 있을 수 있다

            **실측은 항상 3종이 나온다.** 갈리는 것은 예보 쪽이다 — 워치를 안 찬 밤은 혈색이,
            단계가 안 잡힌 밤은 피부 장벽이 비어 있다. 그 지표는 `skipped`로 가고 **적중률 분모에서도
            빠진다.**

            **정확도를 평균낼 분모는 `verifications`의 길이이지 3이 아니다.** `skipped`에도
            `measured`가 실리므로 앱은 실측값 3종을 모두 보여줄 수 있다 — 판정만 못 한 것이지
            사진을 못 읽은 것이 아니다.

            `verifications`는 **비지 않는다** — 다크서클은 예보가 빈 상태가 될 수 없다.

            ### 개인 모델은 첫 검증부터 바로 움직인다

            오차를 부분점수 편차로 피처에 배분해 `personal_weight`를 보정하고, **다음 예보에
            즉시 반영**된다. 최소 검증 횟수를 두지 않는다 — 배수가 `0.5`~`2.0`으로 클램프되어
            우연한 오차 몇 번으로 한 피처가 지표를 지배하지 못한다.

            **`changes`는 값이 실제로 바뀐 행만 담는다.** 그날 참여하지 않은 피처(워치 미착용 등)와,
            참여했지만 보정량이 0이던 피처는 빠진다. **보정량 0은 버그가 아니다** — 두 피처의
            부분점수가 같으면 오차를 어느 쪽 탓으로 돌릴 근거가 없는 날이다.

            **첫 검증은 `changes`가 비어 있어도 `updated: true`다.** 그때 7행이 `1.0`으로
            만들어지고, **행의 존재 자체가 "개인화가 시작됐다"는 뜻**이기 때문이다.

            **한 피처가 올라가면 같은 지표의 다른 피처는 반드시 내려간다.** `message`가 올라간
            쪽만 말하는 이유다 — 내려간 쪽까지 말하면 같은 사실을 두 번 말하게 된다.

            `label`은 서버가 함께 내려준다. **앱이 한국어 이름을 따로 하드코딩하면 수면 통역
            카드(HOME-02)의 문구와 어긋난다.**

            ### 하루 1회다

            같은 날 두 번째 요청은 `409 VERIFICATION_ALREADY_DONE`이다. 셀피를 다시 찍는 것은
            다시 분석하는 것이고 실측값은 갱신되지 않는다.

            **실패하면 행이 생기지 않으므로 재시도는 안전하다.** 502·504를 받았다면 그대로 다시
            보내면 된다.

            ### 예외

            | 코드 | `error.code` | 언제 | 앱이 할 일 |
            |---|---|---|---|
            | `400` | `SELFIE_IMAGE_INVALID` | `image` 파트가 없거나 비었음 · 이미지가 아닌 타입 | 다시 촬영 |
            | `400` | `INVALID_INPUT` | `baseDate` 누락·형식 오류 · 파일이 상한(10MB) 초과 | 요청 버그 또는 리사이즈 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 누락 | 헤더를 넣고 재호출 |
            | `404` | `USER_NOT_FOUND` | 없는 사용자 | — |
            | `404` | `SKIN_FORECAST_NOT_FOUND` | 그날 예보가 없음 | **수면을 먼저 업로드해야 한다** |
            | `409` | `VERIFICATION_ALREADY_DONE` | 그날 이미 검증함 | 결과 화면으로 |
            | `502` | `SELFIE_ANALYSIS_FAILED` | LLM 호출·파싱 실패 | 재시도 |
            | `504` | `SELFIE_ANALYSIS_TIMEOUT` | LLM 응답 지연(30초 초과) | 재시도 |

            **여기서만 예보 부재가 에러다.** 조회 API(`GET /skin/forecast`)에서는 `200` + 빈
            상태이지만, 이 API는 대조할 기준이 없으면 동작 자체가 성립하지 않는다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "분석·검증 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`SELFIE_IMAGE_INVALID` — 이미지 파트가 없거나 이미지가 아님",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "SELFIE_IMAGE_INVALID",
                    ref = "#/components/examples/SELFIE_IMAGE_INVALID")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`SKIN_FORECAST_NOT_FOUND` — 그날 예보가 없어 대조 불가",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "SKIN_FORECAST_NOT_FOUND",
                    ref = "#/components/examples/SKIN_FORECAST_NOT_FOUND")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "`VERIFICATION_ALREADY_DONE` — 하루 1회 제약",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "VERIFICATION_ALREADY_DONE",
                    ref = "#/components/examples/VERIFICATION_ALREADY_DONE")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "502", description = "`SELFIE_ANALYSIS_FAILED` — 분석 실패. 재시도 가능",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "SELFIE_ANALYSIS_FAILED",
                    ref = "#/components/examples/SELFIE_ANALYSIS_FAILED")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "504", description = "`SELFIE_ANALYSIS_TIMEOUT` — 분석 지연. 재시도 가능",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "SELFIE_ANALYSIS_TIMEOUT",
                    ref = "#/components/examples/SELFIE_ANALYSIS_TIMEOUT")))
    ApiResponse<SelfieVerificationResponse> verifySelfie(
            @CurrentUserId Long userId,

            @Parameter(description = "대조할 예보의 기준일 (`YYYY-MM-DD`). **폼 필드가 아니라 쿼리다**",
                    required = true, example = "2026-08-07")
            LocalDate baseDate,

            @Parameter(description = "셀피 이미지. **서버에 저장되지 않는다** — 메모리에서 바로 분석된다")
            MultipartFile image);

    @Operation(summary = "적중률 · 연속 검증 배너 (HOME-09)", description = """
            최근 검증 1건 + 직전 검증 1건 + 누적 적중률 + 연속 검증 횟수.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "AVAILABLE",          // 또는 NO_VERIFICATION
                "message": null,
                "baseDate": "2026-08-10",
                "summary": {
                  "hitRate": 72,                // **누적**
                  "verificationCount": 5,
                  "streakCount": 3,
                  "previous": {                 // 직전 검증 1건. 검증이 1건뿐이면 null
                    "baseDate": "2026-08-08",
                    "hitRate": 63               // 그날치
                  },
                  "latest": {
                    "baseDate": "2026-08-09",
                    "hitRate": 94,              // 그날치
                    "verifications": [ /* POST /skin/selfie 와 같은 모양 */ ],
                    "skipped": [ ]
                  } } } }
            ```

            ### `hitRate`는 누적이다 — `latest.hitRate`와 다른 숫자다

            **최상위 `hitRate`는 지금까지 모든 판정의 정확도 평균이다.** 최근 1건만 쓰면 표본이
            최대 3이라 하루마다 요동친다. 배너가 말하려는 것은 **"예보가 얼마나 믿을 만한가"**
            이므로 표본이 쌓일수록 안정돼야 한다.

            **정확도 곡선은 `POST /skin/selfie`와 같다** — 판정 개수가 아니라 오차 크기를
            로그 곡선에 태운 연속값이다. 그래서 누적은 판정 하나하나가 한 표이고, **지표가
            2개뿐이던 날은 자동으로 가벼워진다.**

            **그날치가 필요하면 `latest.hitRate`를 쓴다.** 두 숫자를 바꿔 쓰면 화면이 다른 뜻을
            말하게 된다.

            **누적 분모에서도 빈 지표는 빠진다.** 그날 예보가 없던 지표는 판정 자체가 없었으므로
            세지 않는다 — `POST /skin/selfie`와 같은 규칙이다. **검증 일수 × 3이 아니다.**

            ### `previous` — "지난번 대비"의 기준선

            **상승폭은 서버가 내려주지 않는다.** `latest.hitRate − previous.hitRate`를 앱이
            뺀다 — 같은 응답 안의 두 필드로 나오는 값이라 서버가 함께 담으면 같은 사실을 말하는
            자리가 둘이 되어 어긋난다.

            **`previous.baseDate`는 전날이 아니라 바로 앞 검증일이다.** 하루 걸러 검증했으면
            이틀 전일 수 있다.

            **검증이 1건뿐이면 `previous`는 `null`이다** — 비교할 대상이 없다. 첫 검증에
            `0`을 주면 화면이 없던 상승폭을 그린다. **`null` 분기가 필요하다.**

            ⚠️ **`previous`와의 차이는 그날치끼리의 비교라 요동친다.** 표본이 최대 3이라 한 지표가
            10점 더 빗나가면 그것만으로 `5%p` 안팎이 움직인다. **"예보가 얼마나 믿을 만한가"는
            여전히 최상위 `hitRate`(누적)가 말한다** — 둘을 같은 뜻으로 쓰지 말 것.

            ### 연속 검증 횟수 — 오늘 미검증이 연속을 끊지 않는다

            `base_date`가 **오늘 또는 어제부터** 하루도 빠짐없이 이어진 날짜의 개수다.

            | 상황 | 결과 |
            |---|---|
            | 오늘 ✅ · 어제 ✅ · 그제 ❌ | `2` |
            | 오늘 ❌ · 어제 ✅ · 그제 ✅ | `2` — **끊기지 않는다** |
            | 오늘 ❌ · 어제 ❌ | `0` |
            | 오늘 첫 검증 | `1` |

            저녁에 검증하는 사용자가 아침에 앱을 열었을 때 어제까지 쌓은 연속이 `0`으로 보이면
            **아직 하지 않은 일로 사용자를 벌주는 것**처럼 읽힌다.

            **`baseDate`가 필수인 이유가 여기 있다.** 서버는 "오늘"을 모르므로 없이 계산하면
            연속이 하루 밀린다.

            ### 빈 상태 — 에러가 아니다

            검증 이력이 없으면 `200` + `status: NO_VERIFICATION`이다. 신규 사용자에게 일상적으로
            발생한다. **`status`로 분기한다.**
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **검증 이력이 없는 경우도 여기에 해당한다** (`status: NO_VERIFICATION`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`INVALID_INPUT` — `baseDate` 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "INVALID_INPUT",
                    ref = "#/components/examples/INVALID_INPUT")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<VerificationSummaryResponse> getVerificationSummary(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). **연속 횟수 계산에 필요하다**",
                    required = true, example = "2026-08-10")
            LocalDate baseDate);

    @Operation(summary = "내 모델 — 일반 vs 개인화 (REP-12)", description = """
            셀피 검증으로 학습된 개인 가중치를 일반 가중치와 비교해 보여준다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "AVAILABLE",          // 또는 NO_VERIFICATION (개인화 전)
                "message": null,
                "model": {
                  "verificationCount": 5,
                  "headline": "야간 각성에 1.6배 민감해요",
                  "metrics": [
                    { "metric": "DARK_CIRCLE",
                      "features": [
                        { "feature": "AWAKE_COUNT", "label": "야간 각성",
                          "generalShare": 0.5, "personalShare": 0.62, "ratio": 1.23 },
                        { "feature": "TOTAL_SLEEP", "label": "총 수면 시간",
                          "generalShare": 0.5, "personalShare": 0.38, "ratio": 0.77 }
                      ] }
                  ] } } }
            ```

            ### 비율은 같은 지표 안에서만 의미를 갖는다

            개인 가중치는 일반 가중치에 곱하는 배수이고, 곱한 뒤 **지표 내 합이 1이 되도록
            재정규화**된다. **절댓값 자체에는 의미가 없고 같은 지표의 다른 피처와의 비율만이
            의미를 갖는다.**

            **다른 지표의 숫자와 비교하지 말 것.** 재정규화 때문에 스케일이 달라 비교 자체가
            성립하지 않는다. `headline`도 **한 지표 안에서** 최대/최소 비가 가장 큰 곳을 골라
            만든 문장이다.

            | 필드 | 뜻 |
            |---|---|
            | `generalShare` | 개인화 전 기준선. 지표 내 균등이라 `1/n` |
            | `personalShare` | 학습된 비중. **예보가 실제로 쓰는 숫자와 같다** |
            | `ratio` | `personalShare / generalShare` — 일반 대비 몇 배 |

            **`ratio`가 전부 `1.0`인 것은 오류가 아니다.** 아직 배울 게 없었던 것이며 신규
            사용자에게 정상이다. 그때 `headline`은 "아직은 일반 모델과 같아요"가 된다.

            ### 신뢰도 등급은 클라이언트가 만든다

            서버는 `verificationCount`(누적 검증 횟수)를 **그대로** 준다. 下/中/上 같은 등급
            해석은 앱이 한다 — 컷오프를 서버에 두면 **바꿀 때마다 배포**해야 한다.

            ### `baseDate`를 받지 않는다

            누적 검증 횟수와 가중치는 "오늘"이 필요 없다. 날짜를 받는 다른 조회 API와 다르다.

            ### 빈 상태 — 에러가 아니다

            아직 검증한 적이 없으면 `200` + `status: NO_VERIFICATION`이다. **개인 가중치 행의
            존재 자체가 "개인화가 시작됐다"는 뜻**이다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **개인화 전인 경우도 여기에 해당한다** (`status: NO_VERIFICATION`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<PersonalModelResponse> getModel(@CurrentUserId Long userId);

}
