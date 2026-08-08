package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

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
            | `NO_SLEEP_STAGES` | 단계 합이 0 — 장벽 산출 불가 | 측정하지 못한 것이지 위험한 것이 아니다 |
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

}
