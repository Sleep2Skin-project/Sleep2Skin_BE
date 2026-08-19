package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
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
                    "awakeMinutes": 7,
                    "remSleepMinutes": 36,
                    "hrv": 42.0,
                    "restingHeartRate": 55
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

            **`remSleepMinutes`·`hrv`·`restingHeartRate`는 `SleepSession`의 값을 그대로 옮긴다.**
            `hrv`·`restingHeartRate`는 워치 미착용으로 그 밤에 결측이면 `null`이다(§10.6).

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
            최근 7일(`baseDate` 포함)의 하루치 수면 점수 추이를 보여준다.

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
                "summary": { "avgSleepScore": 70, "avgDeepSleepMinutes": 126 },
                "correlations": [
                  { "skinMetric": "DARK_CIRCLE",
                    "topCorrelation": { "sleepFeature": "AWAKE_COUNT", "featureLabel": "야간 각성",
                      "skinMetric": "DARK_CIRCLE", "metricLabel": "다크서클 회복",
                      "strength": "VERY_STRONG", "sampleSize": 6, "insufficientSample": false } },
                  { "skinMetric": "COMPLEXION",
                    "topCorrelation": { "sleepFeature": "HRV", "featureLabel": "심박변이도",
                      "skinMetric": "COMPLEXION", "metricLabel": "혈색",
                      "strength": null, "sampleSize": 2, "insufficientSample": true } },
                  { "skinMetric": "BARRIER",
                    "topCorrelation": { "sleepFeature": "DEEP_SLEEP", "featureLabel": "깊은 수면",
                      "skinMetric": "BARRIER", "metricLabel": "피부 장벽",
                      "strength": "MODERATE", "sampleSize": 6, "insufficientSample": false } }
                ]
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
                "dailyScores": [], "summary": null, "correlations": []
              } }
            ```

            **가입한 지 오래됐지만 그 주에 안 잔 경우는 여전히 `FULL`이다.** 그때는
            `dailyScores`의 해당 날짜만 `sleepScore: null`로 나간다 — 데이터 품질 문제와 신규
            사용자 문제를 같은 상태로 묶지 않는다.

            ### `dailyScores`는 항상 7개다 (`FULL`일 때)

            세션이 없는 날짜는 `sleepScore: null`이다. `sleepScore`는 일간 리포트의 `sleepScore`와
            **같은 계산**이다(§10.8 — 그날 참여한 수면 피처 부분점수의 단순 평균, 예보 점수와
            다른 계산).

            ### `summary.avgSleepScore` · `summary.avgDeepSleepMinutes`

            `dailyScores` 중 `sleepScore`가 있는 날짜만의 평균, 반올림. 전부 `null`이면
            `avgSleepScore`도 `null`이다.

            `avgDeepSleepMinutes`는 <b>같은 결측 처리를 쓰는 별도 평균</b>이다 — 세션이 있는
            날짜의 `deepSleepMinutes`만 평균내고, 세션이 없는 날은 분모에서 빠진다. 기간 전체에
            세션이 하나도 없으면 `null`이다.

            ### `correlations` — 수면 피처 7종과 피부 지표의 상관 강도

            **예보값이 아니라 실측값(셀피 검증)과 비교한다.** 예보값은 이 피처들로 계산된
            값이라 예보와 비교하면 항상 강한 상관이 나오는 순환 논증이 된다. 그래서 <b>기간
            안에서 수면 세션과 셀피 검증이 둘 다 있는 날짜만</b> 짝으로 삼는다 — 검증하지 않은
            날은 계산에서 빠진다.

            | `sleepFeature` | 원본값 | → `skinMetric` |
            |---|---|---|
            | `AWAKE_COUNT` | 야간 각성 횟수 | `DARK_CIRCLE` |
            | `TOTAL_SLEEP` | 총 수면 시간(분) | `DARK_CIRCLE` |
            | `DEEP_SLEEP` | 깊은 수면 비율(%) | `BARRIER` |
            | `REM_SLEEP` | REM 수면 비율(%) | `BARRIER` |
            | `BEDTIME_REGULARITY` | 취침 규칙성(표준편차, 분) | `COMPLEXION` |
            | `HRV` | 심박변이도 | `COMPLEXION` |
            | `RESTING_HEART_RATE` | 안정시 심박 | `COMPLEXION` |

            **"원본값"이지 예보 산출의 0~100 부분점수가 아니다.** 부분점수는 이미 정규화된
            값이라 그것끼리 상관을 내면 정규화 곡선의 모양이 상관계수에 섞여 들어간다.

            표본이 5개 미만(`insufficientSample: true`)이면 상관계수를 계산하지 않고
            `strength`가 `null`이다 — 극단값 하나에도 크게 흔들리는 표본으로 "강한 상관"이라고
            말하지 않기 위해서다. 그 피처만 결측인 날(워치 미착용의 HRV·안정시 심박, 이력
            3일 미만의 취침 규칙성, 단계 미상의 비율)은 그 피처의 계산에서만 빠지고 다른
            피처-지표 쌍에는 영향이 없다.

            **`correlations`는 `skinMetric` 기준 3그룹(`DARK_CIRCLE`·`COMPLEXION`·`BARRIER`)의
            배열이고, 그룹마다 대표 상관 1개(`topCorrelation`)만 담는다**(2026-08-19, 프론트
            요청으로 배열→단일 객체 변경). 계산 자체는 여전히 7쌍 전부를 낸다.

            **정렬은 상관계수 절댓값 내림차순이고 표본 부족은 값과 무관하게 맨 뒤로 가는 규칙
            그대로다.** 동률이면 `sleepFeature` 선언 순서(위 표 순서)가 기준이고, 대표는 그
            정렬의 1위다. **표본이 부족해도(`insufficientSample: true`) 대표에서 빠지지
            않는다** — 그 지표에서 가장 유력한 피처가 무엇인지는 표본 부족과 무관하게 의미가
            있다. 그룹은 항상 3개 전부 반환한다.

            ⚠️ 강도 구간(`VERY_STRONG` 0.7 / `STRONG` 0.4 / `MODERATE` 0.2)과 표본 하한(5개)은
            <b>임시값이다</b> — 통계학에서 흔히 쓰는 구간을 참고해 채택했을 뿐 이 서비스의
            데이터로 검증되지 않았다(`CorrelationPolicy` 참고). 팀이 재확인하기 전까지는
            바뀔 수 있다.

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
                  { "weekLabel": "W1", "avgSleepScore": 65, "avgDeepSleepMinutes": 110, "isHighest": false },
                  { "weekLabel": "W2", "avgSleepScore": 58, "avgDeepSleepMinutes": 105, "isHighest": false },
                  { "weekLabel": "W3", "avgSleepScore": 52, "avgDeepSleepMinutes": 98, "isHighest": false },
                  { "weekLabel": "W4", "avgSleepScore": 70, "avgDeepSleepMinutes": 132, "isHighest": true }
                ],
                "summary": { "avgSleepScore": 61, "avgDeepSleepMinutes": 118 },
                "correlations": [
                  { "skinMetric": "DARK_CIRCLE",
                    "topCorrelation": { "sleepFeature": "AWAKE_COUNT", "featureLabel": "야간 각성",
                      "skinMetric": "DARK_CIRCLE", "metricLabel": "다크서클 회복",
                      "strength": "STRONG", "sampleSize": 22, "insufficientSample": false } },
                  { "skinMetric": "COMPLEXION",
                    "topCorrelation": { "sleepFeature": "HRV", "featureLabel": "심박변이도",
                      "skinMetric": "COMPLEXION", "metricLabel": "혈색",
                      "strength": "WEAK", "sampleSize": 22, "insufficientSample": false } },
                  { "skinMetric": "BARRIER",
                    "topCorrelation": { "sleepFeature": "DEEP_SLEEP", "featureLabel": "깊은 수면",
                      "skinMetric": "BARRIER", "metricLabel": "피부 장벽",
                      "strength": "MODERATE", "sampleSize": 22, "insufficientSample": false } }
                ]
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
                "weeks": [], "summary": null, "correlations": []
              } }
            ```

            ### 각 주의 `avgSleepScore` · `avgDeepSleepMinutes`

            그 주 7일 중 수면 점수가 있는 날짜만의 평균, 반올림. 7일 모두 결측이면 `null`이다.
            `sleepScore`는 일간 리포트와 같은 계산이다(§10.8).

            `avgDeepSleepMinutes`도 같은 방식(그 주 7일 중 세션이 있는 날짜만의
            `deepSleepMinutes` 평균)이며, `avgSleepScore`와 별개로 계산되고 별개로 결측 처리된다.

            ### `isHighest`

            4주 중 `avgSleepScore`가 가장 높은 주(들)만 `true`다. **`null`인 주는 비교 대상에서
            빠지고, 최고값이 동점이면 해당하는 주 전부 `true`이며, 4주 모두 `null`이면 비교할 값
            자체가 없어 전부 `false`다.** <b>`avgDeepSleepMinutes`는 이 판정에 관여하지 않는다</b>
            — 최고 주는 어디까지나 수면 점수 기준이다.

            ### `summary.avgSleepScore` · `summary.avgDeepSleepMinutes` — 주 평균의 평균이 아니다

            **28일 전체 일별 값을 한 번에 평균낸 것**이다(둘 다 마찬가지다). 주마다 결측 일수가
            다르면 "주 평균 4개의 평균"과 이 값이 갈릴 수 있다(가중치가 달라진다) — 28일 전부
            결측이어야 `null`이라는 조건이 28일 단위로 걸려 있어 여기서도 28일 단위로 낸다.

            ### `correlations` — 수면 피처 7종과 피부 지표의 상관 강도

            주간 리포트와 완전히 같은 방식이며 대상 기간만 최근 28일이다(`periodStart`~
            `periodEnd`). **예보값이 아니라 실측값(셀피 검증)과 비교**하고, 세션과 검증이
            둘 다 있는 날짜만 짝으로 삼는다.

            | `sleepFeature` | 원본값 | → `skinMetric` |
            |---|---|---|
            | `AWAKE_COUNT` | 야간 각성 횟수 | `DARK_CIRCLE` |
            | `TOTAL_SLEEP` | 총 수면 시간(분) | `DARK_CIRCLE` |
            | `DEEP_SLEEP` | 깊은 수면 비율(%) | `BARRIER` |
            | `REM_SLEEP` | REM 수면 비율(%) | `BARRIER` |
            | `BEDTIME_REGULARITY` | 취침 규칙성(표준편차, 분) | `COMPLEXION` |
            | `HRV` | 심박변이도 | `COMPLEXION` |
            | `RESTING_HEART_RATE` | 안정시 심박 | `COMPLEXION` |

            표본이 5개 미만이면 `insufficientSample: true`이고 `strength`는 `null`이다.
            `correlations`는 주간과 같이 `skinMetric` 기준 3그룹의 배열이고, 그룹마다 대표
            상관 1개(`topCorrelation`)만 담는다 — **계산 자체는 여전히 7쌍 전부**를 낸다.
            28일 기간이라 주간보다 표본이 커지기 쉬워 계산되는 비율이 더 높다.

            ⚠️ 강도 구간과 표본 하한은 **임시값**이다 — 자세한 근거는 `GET /report/weekly`
            문서와 `CorrelationPolicy`를 참고한다.

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

    @Operation(summary = "종합 리포트 조회 (REP-09~11)", description = """
            예보 지표 3종(다크서클·혈색·피부 장벽) 각각의 최근 3주 추세와, 클리닉이 필요할 수 있는
            신호를 함께 보여준다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "FULL",
                "message": null,
                "periodStart": "2026-07-25",
                "periodEnd": "2026-08-14",
                "trends": {
                  "darkCircle": {
                    "trend": "IMPROVED", "volatileDirection": null,
                    "w1Average": 48, "w3Average": 79
                  },
                  "complexion": {
                    "trend": "VOLATILE", "volatileDirection": "RISE_THEN_FALL",
                    "w1Average": 61, "w3Average": 58
                  },
                  "barrier": {
                    "trend": "INSUFFICIENT_SAMPLE", "volatileDirection": null,
                    "w1Average": null, "w3Average": 65
                  }
                },
                "appManaged": ["DARK_CIRCLE", "COMPLEXION", "BARRIER"],
                "clinicNeeded": {
                  "pigmentationDetected": false,
                  "acneScarDetected": false,
                  "agingDetected": true
                },
                "clinicLink": "https://amredclinic.com/ko"
              } }
            ```

            ### 문장을 만들지 않는다

            `trends`는 판정 결과(추세 값·변동 방향·구간 평균)만 담는다. "혈색이 오르다 다시
            내렸어요" 같은 안내 문구는 서버가 만들지 않는다 — 프론트가 판정값을 보고 문구를
            구성한다.

            ### 발동 조건이 없다 — 항상 지표 3종 전부를 보여준다(2026-08-19)

            이전에는 "수면 점수 추세가 STABLE·RISING이고 정체 지표가 있을 때만" `triage`를
            노출하는 발동(트리거) 구조였다. **그 게이팅을 완전히 없앴다** — `triage.triggered`
            필드도 함께 사라졌다. 이제 `status`가 `FULL`인 한 `trends`는 항상 다크서클·혈색·피부 장벽
            셋 다 채워진다.

            ### 관찰 창 — W1/W2/W3 각 7일

            21일(`baseDate`로 끝난다)을 겹치지 않는 세 구간으로 나눈다.

            | 구간 | 범위 |
            |---|---|
            | `W1` | `baseDate-20` ~ `baseDate-14` (가장 과거) |
            | `W2` | `baseDate-13` ~ `baseDate-7` (중간 — 방향 일관성 체크에만 쓰고 응답에는 안 실린다) |
            | `W3` | `baseDate-6` ~ `baseDate` (가장 최근) |

            각 구간의 평균은 그 지표의 예보 점수 평균이다 — 그날 예보가 없으면(그 지표가 빈
            상태였던 날 포함) 분모에서 뺀다. 구간 7일이 전부 결측이면 그 구간 평균은 `null`이다.

            ### `trend` 판정 기준

            | `trend` | 조건 |
            |---|---|
            | `INSUFFICIENT_SAMPLE` | `w1Average` 또는 `w3Average`가 `null`(그 구간 7일 전부 결측) |
            | `VOLATILE` | W1→W2, W2→W3의 방향이 정확히 반대(한쪽 양수·한쪽 음수) — `volatileDirection`이 어느 쪽인지 함께 나간다 |
            | `IMPROVED` | 위에 안 걸리고 `w3Average − w1Average` > 0 |
            | `WORSENED` | 위에 안 걸리고 `w3Average − w1Average` < 0 |
            | `MAINTAINED` | 위에 안 걸리고 `w3Average − w1Average` = 0 |

            **W1↔W2, W2↔W3 둘 중 하나라도 변화가 정확히 0이면 `VOLATILE`로 보지 않는다** —
            "정확히 반대 방향"이어야 변동성으로 판정한다. **W2 평균이 결측이면 `VOLATILE` 판정
            자체를 생략하고 `w3Average − w1Average`만으로 나머지 셋 중 하나를 정한다** — 중간
            구간이 없으면 "오르다 내렸다"를 말할 근거가 없다.

            `volatileDirection`은 `trend`가 `VOLATILE`일 때만 값이 있다. 그 외에는 항상 `null`이다.

            ### `status`는 가입일 기준이다 — REP-06·07과 같은 규칙

            가입 당일을 1일차로 세어(`가입일부터 baseDate까지의 일수 + 1`) **21일 미만이면
            `status: INSUFFICIENT_DATA`이고 `trends`가 `null`이다.** 21일 이상이면 항상
            `FULL`이며, 특정 지표만 표본이 부족한 경우는 그 지표의 `trend`만
            `INSUFFICIENT_SAMPLE`로 표시한다 — **리포트 전체를 부족 처리하지 않는다.**

            ```jsonc
            { "success": true,
              "data": {
                "status": "INSUFFICIENT_DATA",
                "message": "아직 종합 리포트를 만들기에 기록이 부족합니다.",
                "periodStart": "2026-07-25", "periodEnd": "2026-08-14",
                "trends": null,
                "appManaged": ["DARK_CIRCLE", "COMPLEXION", "BARRIER"],
                "clinicNeeded": null,
                "clinicLink": "https://amredclinic.com/ko"
              } }
            ```

            ### `appManaged`

            앱이 관리하는 예보 지표 3종의 고정 목록이다. 점수·등급 없이 이름만 나간다.

            ### `clinicNeeded` — 가입일 게이트와 무관하다

            `baseDate` 이하 범위에서 **가장 최근 실측 1건**의 감지 플래그 3종을 그대로 옮긴다 —
            비교·트렌드는 없다. 셀피 검증 이력이 전혀 없으면 `clinicNeeded` 전체가 `null`이다
            (감지되지 않음과 측정한 적이 없음을 구분한다). **`status`가 `INSUFFICIENT_DATA`여도
            실측 이력이 있으면 그대로 나간다** — 실측 이력 유무로만 결정되는 별개의 신호라
            21일 관찰 창(`trends`가 필요로 하는 조건)과 무관하다.

            **실측 행은 있는데 필드 하나만 `null`인 경우도 있다** — 그 실측 행이 이 컬럼 도입
            이전에 만들어졌다는 뜻이다. 그 필드만 `null`이고 나머지 필드는 실제 값이 그대로
            나간다. 이때도 `false`(실제 미검출)로 채우지 않는다 — 컬럼이 없던 시절의 데이터와
            "셋 다 감지 안 됨"을 구분해야 하기 때문이다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공. **가입한 지 21일 미만이면 `status: INSUFFICIENT_DATA`로 여기에 해당한다**")
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
    ApiResponse<OverallReportResponse> getOverallReport(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). 관찰 기간의 마지막 날(`periodEnd`)이 된다",
                    required = true, example = "2026-08-14")
            LocalDate baseDate);

}
