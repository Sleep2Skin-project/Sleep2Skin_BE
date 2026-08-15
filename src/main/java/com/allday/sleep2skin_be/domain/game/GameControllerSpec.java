package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

/**
 * {@link GameController}의 API 문서.
 *
 * <p><b>{@link CurrentUserId}는 구현체 쪽에도 반드시 붙어 있어야 한다.</b> 파라미터 어노테이션은
 * 인터페이스에서 상속되지 않아, 여기에만 두면 리졸버가 파라미터를 인식하지 못한다.
 */
@Tag(name = "Game", description = "레벨 · 경험치 API (HOME-04)")
public interface GameControllerSpec {

    @Operation(summary = "출석 체크인 (HOME-04)", description = """
            앱이 시작될 때 한 번 호출한다. **하루 첫 호출에만 `+10`**이고, 그 응답으로 홈 화면의
            출석 완료·연속 검증 보상 팝업과 **월~일 출석 도장판**까지 그린다.

            ### 요청

            **본문이 없다.** `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터만 있으면 된다.
            상태를 만드는 동작 API라 `GET`이 아니며, 보낼 것이 `baseDate`뿐이라 본문이 필요 없다.

            **`baseDate`가 필수다.** 서버는 "오늘"을 모른다 — `users`에 타임존이 없다.
            없이 처리하면 한국 시간 오전 9시 이전에 **출석이 어제 날짜로 찍히고**, 그날 다시
            호출할 때 오늘 몫이 **또** 지급된다.

            ### 응답

            ```jsonc
            { "success": true,
              "data": {
                "baseDate": "2026-08-14",
                "checkedIn": true,         // 이번 요청으로 출석이 기록됐는가
                "streakCount": 3,          // 팝업에 함께 뜨는 연속 검증 횟수
                "exp": {
                  "gained": 10,
                  "reasons": [ { "reason": "ATTENDANCE", "amount": 10 } ],
                  "totalExp": 320, "level": 3, "levelUp": false, "nextLevelExp": 450
                },
                "weekStartDate": "2026-08-10",   // 기준일이 속한 주의 월요일
                "weekDays": [
                  { "date": "2026-08-10", "dayOfWeek": "MONDAY",    "status": "ATTENDED" },
                  { "date": "2026-08-11", "dayOfWeek": "TUESDAY",   "status": "MISSED"   },
                  { "date": "2026-08-12", "dayOfWeek": "WEDNESDAY", "status": "ATTENDED" },
                  { "date": "2026-08-13", "dayOfWeek": "THURSDAY",  "status": "ATTENDED" },
                  { "date": "2026-08-14", "dayOfWeek": "FRIDAY",    "status": "ATTENDED" },
                  { "date": "2026-08-15", "dayOfWeek": "SATURDAY",  "status": "UPCOMING" },
                  { "date": "2026-08-16", "dayOfWeek": "SUNDAY",    "status": "UPCOMING" }
                ]
              } }
            ```

            ### 출석 도장판 — `weekDays`

            **기준일이 속한 주의 월요일부터 일요일까지 항상 7칸**이고, 첫 칸이 언제나 월요일이다.
            기록이 없는 날도 빠지지 않는다 — 빼면 도장판 칸 수가 주마다 달라진다.

            | `status` | 뜻 |
            |---|---|
            | `ATTENDED` | 그날 출석 기록이 있다. **재호출이어도 오늘 칸은 여기다** |
            | `MISSED` | 이미 지난 날인데 기록이 없다 |
            | `UPCOMING` | 기준일보다 **미래**라 아직 판정할 수 없다 |

            > ⚠️ **`MISSED`와 `UPCOMING`을 같은 빈 칸으로 그리지 말 것.** 오늘이 화요일인데 수·목·금이
            > "빠뜨림"으로 보이면, 사용자는 **하지도 않은 일로** 도장판이 비어 있는 것을 보게 된다.

            **달력 주다 — 리포트 주간(REP-06)과 앵커가 다르다.** 리포트는 `baseDate − 6 ~ baseDate`인
            롤링 7일이지만 도장판은 월요일에 고정된다. **응답 필드에 `weekly`를 쓰지 않는 이유**이며,
            두 기간을 같은 규칙으로 읽지 말 것.

            **주 시작일도 `baseDate`에서 역산한다.** 서버 시각으로 계산하면 한국 시간 오전 9시
            이전에 주가 통째로 하루 밀려 **월요일 아침에 지난주 도장판이 뜬다.**

            **`dayOfWeek`는 `MONDAY` 같은 영어 상수다.** "월"·"화" 같은 표시 문구는 클라이언트가
            만든다 — 서버가 내려보내면 문구 하나 바꾸는 데 배포가 필요하다.

            ### 재호출은 에러가 아니다

            | 상황 | 코드 | `checkedIn` | `exp.gained` | 오늘 칸 |
            |---|---|---|---|---|
            | 그날 첫 호출 | `200` | `true` | `+10` | `ATTENDED` |
            | 같은 날 재호출 | `200` | `false` | `0` | `ATTENDED` |

            **재호출이어도 오늘 칸은 `ATTENDED`다.** 도장판은 `checkedIn`이 아니라 **적립 이력이
            있는가**로 정해진다 — `checkedIn`을 근거로 삼으면 하루에 두 번째로 앱을 켠 사용자에게
            오늘 도장이 사라진다.

            **`409`가 아니라 `200`이다.** 앱은 시작할 때마다 호출하므로 하루에 다섯 번 켜면 네 번은
            재호출이다 — **정상 흐름을 에러로 만들면 진짜 문제가 묻힌다.** 대신 `checkedIn`으로
            팝업을 띄울지 정한다.

            하루 1회는 서버의 적립 이력 유니크가 보장한다. 스플래시와 홈에서 각각 호출해도
            중복 지급되지 않는다.

            ### `streakCount`는 연속 **검증** 횟수다

            출석 연속이 아니다 — 출석 연속은 어디에도 쓰지 않으므로 세지 않는다. 보상 구간이
            걸려 있는 것은 셀피 검증 쪽이다. **HOME-09 배너·MY-01 프로필과 같은 계산에서 나오므로
            세 화면이 같은 숫자를 보여준다.**

            > **연속 검증 보상 자체는 이 API가 주지 않는다.** 검증이 일어나는 `POST /skin/selfie`가
            > 준다 — 출석은 앱을 켠 사실에, 연속 보상은 검증한 사실에 붙는다.
            > **여기서 함께 지급하면 셀피를 찍지 않아도 보상이 나간다.**

            ### 레벨

            `exp.level`은 `totalExp`에서 계산되며 저장된 컬럼이 아니다. `nextLevelExp`는 다음 레벨
            **컷오프 절대값**이고 만렙(5)이면 `null`이다 — "남은 exp"는 앱이
            `nextLevelExp − totalExp`로 계산한다.

            **캐릭터 이름·이미지는 응답에 없다.** 서버는 레벨 숫자만 알고, 5단계의 캐릭터와 문구는
            클라이언트 리소스다. `levelUp: true`가 캐릭터 변경 연출을 띄우는 신호다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "처리 성공. **같은 날 재호출도 여기에 해당한다** (`checkedIn: false` · `exp.gained: 0`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "`INVALID_INPUT` — `baseDate` 누락 또는 형식 오류 · `USER_ID_HEADER_INVALID` — 헤더 누락",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "INVALID_INPUT",
                            ref = "#/components/examples/INVALID_INPUT"),
                    @ExampleObject(name = "USER_ID_HEADER_INVALID",
                            ref = "#/components/examples/USER_ID_HEADER_INVALID")}))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<AttendanceResponse> checkIn(
            @CurrentUserId Long userId,

            @Parameter(description = "기준일 (`YYYY-MM-DD`). **앱의 로컬 날짜를 보낸다** — "
                    + "서버는 \"오늘\"을 모른다. 서버 시각(UTC)으로 처리하면 한국 시간 오전 9시 "
                    + "이전에 출석이 어제 날짜로 찍히고, 그날 오늘 몫이 또 지급된다",
                    example = "2026-08-14")
            LocalDate baseDate);

}
