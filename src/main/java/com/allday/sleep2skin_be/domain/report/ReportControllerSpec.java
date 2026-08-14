package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

/**
 * {@link ReportController}의 API 문서.
 *
 * <p><b>{@link CurrentUserId}는 구현체 쪽에도 반드시 붙어 있어야 한다.</b> 파라미터 어노테이션은
 * 인터페이스에서 상속되지 않는다.
 */
@Tag(name = "Report", description = "일간·주간·월간 리포트 API (REP-02·03·04·05·06·07)")
public interface ReportControllerSpec {

    @Operation(summary = "일간 리포트 조회 (REP-02·04·05)", description = """
            그날의 수면 요약과 피부 예보(전일 대비)를 함께 보여준다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다. `baseDate`는 **기상일 기준**이다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "baseDate": "2026-08-14",
                "sleepSummary": {
                  "status": "AVAILABLE",
                  "message": null,
                  "summary": {
                    "totalSleepMinutes": 432,
                    "sleepScore": 70,
                    "deepSleepMinutes": 126,
                    "lightSleepMinutes": 71,
                    "awakeCount": 2,
                    "awakeMinutes": 7
                  }
                },
                "skinForecast": {
                  "status": "AVAILABLE",
                  "message": null,
                  "darkCircle": { "today": 44, "diffFromYesterday": 1 },
                  "complexion": { "today": 63, "diffFromYesterday": 7 },
                  "barrier": { "today": 79, "diffFromYesterday": null }
                }
              } }
            ```

            ### 두 섹션은 서로 독립적으로 빈 상태가 될 수 있다

            `sleepSummary`와 `skinForecast`는 각자 자기 자신의 `status`·`message`를 갖는다.
            **응답 전체를 하나의 상태로 감싸지 않는다** — 검증을 마친 날의 예보는 세션이 갱신돼도
            재산출되지 않는다는 정책 때문에, 두 섹션의 존재 여부가 항상 같이 가지 않는다. 한쪽이
            비었다고 다른 쪽까지 숨기면 있는 데이터를 못 보여준다.

            그날 수면 데이터가 없으면 `sleepSummary.status`가 `NO_SLEEP_DATA`이고 `summary`는
            `null`이다. 그날 예보가 없으면 `skinForecast.status`가 `NO_SLEEP_DATA`이고 세 지표
            모두 `today`·`diffFromYesterday`가 `null`이다.

            ### `sleepScore`는 예보 점수(HOME-03)와 다른 계산이다

            예보는 지표별 가중평균(§10.4, 개인 가중치 반영)이지만, 여기 `sleepScore`는 그날
            **참여한 수면 피처 부분점수(`s(f)`)의 단순 평균**이다(§10.8). "오늘 수면이 전반적으로
            몇 점이었나"를 보여주는 화면이라 지표별 가중치를 섞지 않는다. 참여 피처가 0개면
            `null`이지만, 세션이 있는 한 야간 각성·총 수면 두 피처는 항상 참여하므로 실무에서는
            일어나지 않는다.

            ### 지표별 `today`·`diffFromYesterday`

            `today`는 그날 이 지표를 산출하지 못했으면(워치 미착용 등, prd.md §10.6) `null`이다.
            `diffFromYesterday`는 `오늘 − 어제`이고, 오늘 값이 없거나 전날 예보 자체가 없거나
            전날에도 그 지표가 없었으면 `null`이다 — **어느 한쪽이 없는 차이는 "변화 없음"이 아니라
            "비교 불가"다.**

            **`lightSleepMinutes`는 `SleepSession.coreSleepMinutes`다.** 얕은 수면(HealthKit
            `asleepCore`)을 리포트에서 쓰는 이름으로 노출한 것이다.

            **`awakeCount`·`awakeMinutes`는 리포트에서 다시 계산하지 않는다.** 수면 정규화
            시점(5분 임계값)에 이미 확정된 `SleepSession`의 값을 그대로 쓴다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **두 섹션이 각자 빈 상태일 수 있고, 그 경우도 여기에 해당한다**")
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
    ApiResponse<DailyReportResponse> getDailyReport(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). **기상일 기준**",
                    required = true, example = "2026-08-14")
            LocalDate baseDate);

    @Operation(summary = "일간 수면 타임라인 조회 (REP-03)", description = """
            그날 밤의 수면 단계 구간을 시간순으로 보여준다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "AVAILABLE",
                "message": null,
                "baseDate": "2026-08-14",
                "sleepOnsetTime": "2026-08-13T23:40:00+09:00",
                "wakeTime": "2026-08-14T07:10:00+09:00",
                "segments": [
                  { "stage": "DEEP", "startTime": "...", "endTime": "..." },
                  { "stage": "AWAKE", "startTime": "...", "endTime": "..." }
                ]
              } }
            ```

            `segments`는 `startTime` 오름차순이다(리포지토리 조회가 보장). `stage`는 `DEEP`·`REM`·
            `CORE`·`AWAKE`·`UNSPECIFIED` 중 하나다 — `UNSPECIFIED`(단계 미상)도 그대로 나간다.
            **집계는 여기서 다시 계산하지 않는다.** `SleepStageSegment`는 렌더링 전용이고 합계는
            이미 `SleepSession`이 들고 있다(그 값은 `GET /report/daily`에서 나간다).

            ### 빈 상태 — 에러가 아니다

            그날 수면 데이터가 없으면 `200` + `status: NO_SLEEP_DATA`다. `sleepOnsetTime`·
            `wakeTime`은 `null`이고 `segments`는 빈 배열이다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **수면 데이터가 없는 경우도 여기에 해당한다** (`status: NO_SLEEP_DATA`)")
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
    ApiResponse<DailyTimelineResponse> getDailyTimeline(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). **기상일 기준**",
                    required = true, example = "2026-08-14")
            LocalDate baseDate);

    @Operation(summary = "주간 리포트 조회 (REP-06)", description = """
            최근 7일(`baseDate` 포함)의 하루치 수면 점수 추이와 적중률을 보여준다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "FULL",
                "periodStart": "2026-08-08",
                "periodEnd": "2026-08-14",
                "dailyScores": [
                  { "date": "2026-08-08", "sleepScore": 62 },
                  { "date": "2026-08-09", "sleepScore": null }
                ],
                "summary": { "avgSleepScore": 70, "hitRate": 86, "verifiedDays": 7 }
              } }
            ```

            ### 기간은 `baseDate` 기준으로 역산한다

            `periodStart = baseDate - 6`, `periodEnd = baseDate`다. 가입일에 고정된 창이 아니라
            호출할 때마다 최근 7일을 가리키므로, 매일 다시 부르면 창이 하루씩 밀린다.

            ### `status`는 최상위 하나다 — 일간 리포트와 다른 구조다

            일간 리포트(`GET /report/daily`)는 `sleepSummary`·`skinForecast`가 서로 독립적으로
            빌 수 있어 섹션마다 상태를 뒀지만, 이 API는 <b>기간 하나가 응답 전체의 성립 여부를
            가른다</b> — `GET /report/daily/timeline`과 같은 최상위 단일 `status` 구조다.
            값은 `FULL`·`INSUFFICIENT_DATA` 둘뿐이고 **일간 리포트의 `QueryStatus`
            (`AVAILABLE`·`NO_SLEEP_DATA` 등)와는 다른 값 집합**이다.

            ### `INSUFFICIENT_DATA`는 가입일 기준이지, "그 주에 기록이 있었는가"가 아니다

            가입 당일을 1일차로 세어(`가입일부터 baseDate까지의 일수 + 1`) 7일 미만이면 아직
            한 주 분량이 쌓일 수 없는 신규 사용자라 `INSUFFICIENT_DATA`다:

            ```jsonc
            { "success": true,
              "data": {
                "status": "INSUFFICIENT_DATA",
                "periodStart": "2026-08-08", "periodEnd": "2026-08-14",
                "dailyScores": [], "summary": null
              } }
            ```

            **가입한 지 오래됐지만 그 주에 안 잔 경우는 여전히 `FULL`이다.** 그때는
            `dailyScores`의 해당 날짜만 `sleepScore: null`로 나간다 — 데이터 품질 문제와 신규
            사용자 문제를 같은 상태로 묶지 않는다.

            ### `dailyScores`는 항상 7개다 (`FULL`일 때)

            세션이 없는 날짜는 `sleepScore: null`이다. `sleepScore`는 일간 리포트의 `sleepScore`와
            **같은 계산**이다(§10.8 — 그날 참여한 수면 피처 부분점수의 단순 평균, 예보 점수와
            다른 계산).

            ### `summary.avgSleepScore`

            `dailyScores` 중 `sleepScore`가 있는 날짜만의 평균, 반올림. 전부 `null`이면
            `avgSleepScore`도 `null`이다.

            ### `summary.hitRate`·`verifiedDays`

            **`hitRate`는 날짜 기준이 아니라 지표 기준이다.** 기간 안에서 검증(예보+실측)한 날의
            지표 3종(다크서클·혈색·장벽) 각각을 판정해 `HIT` 비율을 낸다 — 하루 안에서도 지표별로
            결과가 갈릴 수 있어서다. 판정 자체가 없으면(그 주에 검증이 없었으면) `hitRate`는
            `null`이다. `verifiedDays`는 기간 안에서 검증한 날짜 수다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **가입한 지 7일 미만이면 `status: INSUFFICIENT_DATA`로 여기에 해당한다**")
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
    ApiResponse<WeeklyReportResponse> getWeeklyReport(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). 기간의 마지막 날(`periodEnd`)이 된다",
                    required = true, example = "2026-08-14")
            LocalDate baseDate);

    @Operation(summary = "월간 리포트 조회 (REP-07)", description = """
            최근 28일을 7일씩 4주(`W1`~`W4`)로 나눠 주별 수면 점수 평균과 최고 주를 보여준다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "FULL",
                "periodStart": "2026-07-18",
                "periodEnd": "2026-08-14",
                "weeks": [
                  { "weekLabel": "W1", "avgSleepScore": 65, "isHighest": false },
                  { "weekLabel": "W2", "avgSleepScore": 58, "isHighest": false },
                  { "weekLabel": "W3", "avgSleepScore": 52, "isHighest": false },
                  { "weekLabel": "W4", "avgSleepScore": 70, "isHighest": true }
                ],
                "summary": { "avgSleepScore": 61, "hitRate": 82, "verifiedDays": 26 }
              } }
            ```

            ### 4주 구간은 `baseDate` 기준으로 역산한다 — 가입일 앵커가 아니다

            `periodStart = baseDate - 27`, `periodEnd = baseDate`다.

            | 주 | 범위 |
            |---|---|
            | `W1` | `baseDate-27` ~ `baseDate-21` (가장 과거) |
            | `W2` | `baseDate-20` ~ `baseDate-14` |
            | `W3` | `baseDate-13` ~ `baseDate-7` |
            | `W4` | `baseDate-6` ~ `baseDate` (`baseDate`를 포함한 최근 7일) |

            가입일을 앵커로 삼으면 매주 경계가 요일마다 달라져 "이번 주"라는 감각과 어긋난다.

            ### `status`는 최상위 하나다

            주간 리포트와 같은 이유로 최상위 단일 `status`(`FULL`·`INSUFFICIENT_DATA`)를 쓴다.

            ### `INSUFFICIENT_DATA`는 가입일 기준이다

            가입 당일을 1일차로 세어(`가입일부터 baseDate까지의 일수 + 1`) 28일 미만이면
            `INSUFFICIENT_DATA`다:

            ```jsonc
            { "success": true,
              "data": {
                "status": "INSUFFICIENT_DATA",
                "periodStart": "2026-07-18", "periodEnd": "2026-08-14",
                "weeks": [], "summary": null
              } }
            ```

            ### 각 주의 `avgSleepScore`

            그 주 7일 중 수면 점수가 있는 날짜만의 평균, 반올림. 7일 모두 결측이면 `null`이다.
            `sleepScore`는 일간 리포트와 같은 계산이다(§10.8).

            ### `isHighest`

            4주 중 `avgSleepScore`가 가장 높은 주(들)만 `true`다. **`null`인 주는 비교 대상에서
            빠지고, 최고값이 동점이면 해당하는 주 전부 `true`이며, 4주 모두 `null`이면 비교할 값
            자체가 없어 전부 `false`다.**

            ### `summary.avgSleepScore` — 주 평균의 평균이 아니다

            **28일 전체 일별 점수를 한 번에 평균낸 값**이다. 주마다 결측 일수가 다르면 "주
            평균 4개의 평균"과 이 값이 갈릴 수 있다(가중치가 달라진다) — 28일 전부 결측이어야
            `null`이라는 조건이 28일 단위로 걸려 있어 여기서도 28일 단위로 낸다.

            ### `summary.hitRate`·`verifiedDays`

            주간 리포트와 같은 방식이며 대상 기간만 최근 28일이다. **날짜 기준이 아니라 지표
            기준**이고, 판정이 없으면 `hitRate`는 `null`이다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **가입한 지 28일 미만이면 `status: INSUFFICIENT_DATA`로 여기에 해당한다**")
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
    ApiResponse<MonthlyReportResponse> getMonthlyReport(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). 기간의 마지막 날(`periodEnd`)이 된다",
                    required = true, example = "2026-08-14")
            LocalDate baseDate);

}
