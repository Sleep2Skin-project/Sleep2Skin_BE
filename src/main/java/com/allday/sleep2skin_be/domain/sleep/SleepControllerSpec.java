package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.sleep.dto.request.SleepSessionUploadRequest;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

/**
 * {@link SleepController}의 API 문서.
 *
 * <p>Swagger 어노테이션을 컨트롤러에서 분리한 자리다 — 프론트가 Swagger UI만 보고 개발하므로
 * 설명이 길어질 수밖에 없는데, 그게 컨트롤러에 붙으면 실제 코드가 어노테이션에 파묻힌다.
 */
@Tag(name = "Sleep", description = "수면 수집 · 해석 API")
public interface SleepControllerSpec {

    @Operation(summary = "수면 세션 업로드 → 오늘의 예보 (ONB-03 · HOME-03)", description = """
            앱이 읽은 수면 단계 구간을 보내면 서버가 세션 한 건으로 정리하고 **그 자리에서 피부 예보까지
            산출해 돌려준다.** 앱은 이 한 번의 호출로 홈 화면을 그린다.

            ### 언제 호출하나

            **앱이 시작될 때마다** 호출한다. 새 수면 데이터가 없어도 그냥 호출하면 된다 — 서버가
            같은 데이터인지 판별해 재처리를 건너뛴다. 호출 전에 "새 데이터가 있는지" 앱이 판단할
            필요가 없다.

            ### 요청

            `X-User-Id` 헤더가 필요하다. **`baseDate`는 받지 않는다** — 다른 API와 달리 기준일을
            서버가 정한다(기상 시각의 날짜).

            ```jsonc
            {
              "segments": [
                { "stage": "AWAKE", "startTime": "2026-08-06T23:35:00+09:00", "endTime": "2026-08-06T23:40:00+09:00" },
                { "stage": "CORE",  "startTime": "2026-08-06T23:40:00+09:00", "endTime": "2026-08-07T00:55:00+09:00" },
                { "stage": "DEEP",  "startTime": "2026-08-07T00:55:00+09:00", "endTime": "2026-08-07T01:32:00+09:00" }
              ],
              "hrv": 41.2,
              "restingHeartRate": 63
            }
            ```

            **집계값(총 수면·단계별 분·각성 횟수)을 보내지 않는다.** 서버가 세션을 첫 기상에서 자르기
            때문에 앱이 보고한 총합에는 그 뒤의 낮잠이 섞여 있을 수 있다. 서버가 자를 거면 서버가
            세는 것이 맞다.

            ### 앱이 반드시 지킬 세 가지

            | # | 내용 | 어기면 |
            |---|---|---|
            | 1 | **`UNSPECIFIED`를 `CORE`로 바꿔 보내지 말 것** | 비율 분모가 오염되어 **피부 장벽 점수만 조용히 틀린다** |
            | 2 | **시각에 오프셋을 반드시 포함할 것** | 역직렬화가 실패해 `400`이 난다 |
            | 3 | **`inBed`는 보내지 말 것** | 서버가 무시한다 |

            ### 서버가 하는 일

            ```
            1. 시간순 정렬 · 구간 겹침 검사
            2. 세션 경계 자르기   연속 AWAKE 60분 이상 → 첫 기상. 이후 구간은 낮잠이므로 버린다
            3. 집계               총 수면 = 수면 구간 합 (UNSPECIFIED 포함)
                                  각성 = 5분 이상 구간의 개수와 합
            4. sleepDate 결정      기상 시각의 날짜
            5. 해시 비교          같은 수면이면 여기서 중단
            6. 저장 → 스코어링 → 예보 응답
            ```

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "processed": true,
                "sleepDate": "2026-08-07",
                "sleep": {
                  "sleepOnsetTime": "2026-08-06T14:40:00Z",
                  "wakeTime": "2026-08-06T22:10:00Z",
                  "totalSleepMinutes": 402, "deepSleepMinutes": 54,
                  "remSleepMinutes": 71, "coreSleepMinutes": 277,
                  "awakeCount": 3, "awakeMinutes": 21,
                  "sleepScore": 78            // 참여 피처가 0개면 null
                },
                "forecast": {
                  "darkCircle": { "score": 68, "grade": "NORMAL" },
                  "complexion": { "score": 69, "grade": "NORMAL" },
                  "barrier":    { "score": 98, "grade": "STABLE" },
                  "unavailable": []
                },
                "exp": {
                  "gained": 26,
                  "reasons": [ { "reason": "SLEEP_SCORE_IMPROVED", "amount": 26 } ],
                  "totalExp": 320, "level": 3, "levelUp": false, "nextLevelExp": 450
                }
              } }
            ```

            **시각은 UTC(`Z`)로 나간다.** 보낸 오프셋과 표기는 다르지만 가리키는 순간은 같다.
            앱의 타임존으로 변환해 표시하면 된다.

            ### `sleepScore`와 exp 적립 (HOME-04)

            **`sleepScore`는 그날 스코어링에 참여한 피처의 부분점수 평균이다.** 저장하지 않고 매번
            계산하며, **피부 예보 점수와 다른 값이다** — 이쪽은 수면 자체의 질이다. 두 숫자가 화면에
            나란히 보이므로 라벨을 섞지 않는다. 참여 피처가 0개인 날은 `null`이다(0점이 아니다).

            수면 점수 보상 두 종이 여기서 지급된다.

            | `reason` | 조건 | 양 |
            |---|---|---|
            | `SLEEP_SCORE_IMPROVED` | 전날 수면 점수보다 올랐음 | `(오늘 − 어제) × 2` |
            | `SLEEP_SCORE_HIGH` | 오늘 수면 점수 `90` 이상 | `+10` |

            **둘은 겹칠 수 있다** — 90점을 넘기며 오른 날은 `reasons`에 둘 다 실린다. 90점 보상은
            증가 여부와 무관하다(95점을 유지하는 사용자가 보상을 못 받는 일이 없어야 한다).

            **`processed: false`면 적립하지 않는다.** 재처리를 하지 않은 요청이라 새로 산출된 점수가
            없다 — 앱이 시작할 때마다 호출하므로 여기서 매번 적립하면 앱을 다섯 번 켤 때 다섯 번
            붙는다. 그때도 `exp` 객체는 나가며 `gained: 0` · `reasons: []`다.

            **전날 수면 점수가 없으면 `SLEEP_SCORE_IMPROVED`는 지급되지 않는다.** 비교 대상이 없는
            것이지 0점에서 오른 것이 아니다. `SLEEP_SCORE_HIGH`는 전날과 무관하므로 첫날에도 지급된다.

            > ⚠️ **`processed: true`인데 이미 지급된 경우가 있다.** 해시가 다르고 검증 전이면 같은 날
            > 두 번째 재산출이 일어나고, 그때 점수가 바뀌면 조건이 다시 성립한다. 서버가 하루 1회로
            > 막으며 그 경우 `exp.gained`는 `0`이고 `reasons`는 `[]`다.

            ⚠️ **앱은 `exp.gained`의 부호를 그대로 반영해야 한다.** 양수로 가정하고 더하면 서버가
            막은 무한 적립이 화면에서 되살아난다.

            ### 같은 데이터를 다시 보내도 안전하다

            하루에 앱을 다섯 번 켜면 같은 데이터가 다섯 번 온다. 서버는 정규화 결과의 해시를 비교해
            중복을 걸러낸다.

            | 상황 | 코드 | `processed` | 서버가 한 일 |
            |---|---|---|---|
            | 그날 첫 수신 | `201` | `true` | 저장 + 스코어링 |
            | 같은 데이터 재수신 | `200` | `false` | **아무것도 하지 않고** 기존 예보 반환 |
            | 다른 데이터 + 그날 셀피 검증 완료 | `200` | `false` | 갱신하지 않고 기존 예보 반환 |
            | 다른 데이터 + 검증 전 | `200` | `true` | 갱신 + 재산출 |

            **넷 다 성공이고 예보는 항상 실려 나간다.** `processed`는 서버 상태가 바뀌었는지만
            알려주며, 화면을 나눌 필요가 없다면 무시해도 된다.

            **검증을 마친 날의 예보는 절대 바뀌지 않는다.** 바뀌면 이미 끝난 검증의 대조 기준이
            사후에 달라져 적중률이 훼손된다.

            ### 지표가 비어 있을 수 있다

            수면 데이터가 정상이어도 **일부 지표를 산출하지 못하는 밤이 있다.** 에러가 아니라
            정상 응답이며, 신규 사용자에게 일상적으로 발생한다.

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
            | `400` | `INVALID_INPUT` | `segments`가 비었거나, 알 수 없는 `stage`, 시각에 오프셋 누락 | 페이로드 버그다 |
            | `400` | `SLEEP_TIME_INVALID` | 구간의 시작이 종료보다 늦거나 같음 | 페이로드 버그다 |
            | `400` | `SLEEP_STAGE_INVALID` | 구간이 서로 겹침 | 페이로드 버그다 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 | 헤더를 넣고 다시 호출 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 | 시딩된 테스트 유저 ID인지 확인 |
            | `500` | `INTERNAL_ERROR` | 서버 오류 | 재시도 안내 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "그날 첫 수신 — 저장하고 예보를 산출했다 (`processed: true`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "이미 그날 세션이 있다. 재수신이거나 갱신이며 예보는 그대로 실려 나간다")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`INVALID_INPUT` — 구간이 비었거나 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "INVALID_INPUT",
                    ref = "#/components/examples/INVALID_INPUT")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ResponseEntity<ApiResponse<SleepSessionUploadResponse>> uploadSession(
            @CurrentUserId Long userId,
            @Valid SleepSessionUploadRequest request);

    @Operation(summary = "어젯밤 수면 통역 카드 (HOME-02)", description = """
            어젯밤 수면을 한 문장으로 읽어준다. 기준치에서 **가장 멀어진 지표 1개**를 골라
            헤드라인을 만든다.

            ### 언제 호출하나

            홈 화면 상단 카드를 그릴 때. 예보(`GET /skin/forecast`)와 **같은 날짜로 함께 호출**하면
            된다.

            ### 요청

            `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다. `baseDate`는 **기상일 기준**이며,
            서버는 "오늘"을 모르므로 앱이 자기 로컬 날짜를 `YYYY-MM-DD`로 보낸다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "status": "AVAILABLE",
                "message": null,
                "baseDate": "2026-08-07",
                "interpretation": {
                  "tone": "IMPROVE",
                  "headline": "밤중에 3번 깼어요. 다크서클 회복이 더뎌질 수 있어요.",
                  "focus": { "feature": "AWAKE_COUNT", "label": "야간 각성", "score": 50 }
                }
              } }
            ```

            **`headline`은 그대로 보여줄 수 있는 문장이다.** 다만 문구는 다듬어질 수 있으므로
            분기는 `tone`과 `focus.feature`로 한다.

            | `tone` | 언제 | `focus` |
            |---|---|---|
            | `IMPROVE` | 기준치에서 멀어진 지표가 있다 | 그 지표 |
            | `PRAISE` | **모든 지표가 안정 구간이다** | `null` |

            **`PRAISE`일 때 `focus`는 `null`이다.** 잘 잔 밤에도 억지로 무언가를 지적하지 않는다.

            ### 예보와 같은 근거를 쓴다

            `focus.score`는 **예보 산출에 쓰인 것과 같은 부분점수**다. 기준을 따로 두지 않았기
            때문에, 카드가 "충분히 주무셨어요"라고 하는데 예보는 총 수면을 감점한 상태 같은
            어긋남이 생기지 않는다.

            **점수는 높을수록 좋다.** 야간 각성은 많이 깰수록 점수가 내려간다.

            ### 빈 상태 — 에러가 아니다

            그날 수면 데이터가 없으면 `200`에 `status: NO_SLEEP_DATA`로 나가고
            `interpretation`이 `null`이다. 신규 사용자에게 일상적으로 발생한다.

            ### 예외

            | 코드 | `error.code` | 언제 |
            |---|---|---|
            | `400` | `INVALID_INPUT` | `baseDate` 누락 또는 형식 오류 |
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더 누락 또는 형식 오류 |
            | `404` | `USER_NOT_FOUND` | 존재하지 않는 사용자 |
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
    ApiResponse<SleepInterpretationResponse> getInterpretation(
            @CurrentUserId Long userId,

            @Parameter(description = "조회 기준일 (`YYYY-MM-DD`). **기상일 기준이며 앱의 로컬 날짜를 보낸다**",
                    required = true, example = "2026-08-07")
            LocalDate baseDate);

}
