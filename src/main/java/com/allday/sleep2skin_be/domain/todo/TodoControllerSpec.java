package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.todo.dto.request.TodoStatusUpdateRequest;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoListResponse;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoStatusUpdateResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.time.LocalDate;

/**
 * {@link TodoController}의 API 문서.
 *
 * <p>Swagger 어노테이션을 컨트롤러에서 분리한 자리다. 매핑 어노테이션(`@GetMapping` 등)은
 * 여기 두지 않는다 — Controller 구현체에만 붙인다.
 *
 * <p><b>{@link CurrentUserId}는 구현체 쪽에도 반드시 붙어 있어야 한다.</b> 파라미터 어노테이션은
 * 인터페이스에서 상속되지 않는다.
 */
@Tag(name = "Todo", description = "오늘의 행동 추천 API (TODO-01~05)")
public interface TodoControllerSpec {

    @Operation(
            summary = "오늘의 TODO 목록 조회",
            description = """
                    "오늘은 피하세요"(상위 3개)와 "오늘 밤 체크리스트"(상위 5개) 두 섹션을 반환한다.

                    ### 언제 호출하나

                    TODO 탭 진입 시. `X-User-Id` 헤더와 `baseDate` 쿼리 파라미터가 필요하다.

                    ### 목록은 그날 첫 조회 시 만들어져 고정된다

                    그날 첫 조회면 추천 엔진을 돌려 목록을 만들어 저장하고, 이후 조회는 같은
                    목록을 그대로 반환한다 — 하루 안에서 임계값·가중치가 바뀌어도 그날 이미 만든
                    목록은 바뀌지 않는다.

                    ### 매칭·정렬 기준

                    후보 추출(뜰지 말지)은 그날 예보 점수 기준이다. 우선순위는
                    `impact_score × (100 − 예보 점수) + verdictBonus`로 계산되며, 가장 최근
                    검증에서 위험을 과소평가한(`OVERESTIMATED`) 지표가 있으면 관련 항목의
                    `verdictBonus`가 붙어 우선순위가 올라간다.

                    ### 응답 형태

                    `avoidItems`(최대 3개)의 각 항목엔 `causeLabel`(원인 태그) + `reason`(롱프레스
                    노출용 긴 설명)이 있고 `status`는 없다. `checklistItems`(최대 5개)의 각 항목엔
                    `status`(`PENDING`/`DONE`)만 있고 `causeLabel`·`reason`은 없다. 진행도("n/5")
                    표시는 클라이언트가 `status`를 세어 계산한다 — 서버는 계산된 진행도를 내려주지
                    않는다.

                    ### 빈 상태는 200이다 — 404가 아니다

                    그날 예보가 없으면(수면 데이터 미동기화) `status`가 **`NO_SLEEP_DATA`** 이고
                    두 배열이 `[]`로 나간다. **에러가 아니다** — 수면을 아직 올리지 않은 신규
                    사용자가 TODO 탭을 열면 일상적으로 발생한다. 앱은 `message`가 아니라
                    **`status`로 분기**한다.

                    후보가 0개인 날(그날 모든 지표가 임계값보다 좋음)은 `AVAILABLE`인데 배열만
                    비어 있다. **"예보가 없다"와 "처방할 것이 없다"는 다른 상태**이므로 문구도
                    달라야 한다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공 (첫 조회 시 생성 포함). **예보가 없는 경우도 여기로 나간다** (`status: NO_SLEEP_DATA`)")
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
    ApiResponse<TodoListResponse> getTodos(
            @CurrentUserId Long userId,
            @Parameter(description = "기준일", example = "2026-08-13")
            LocalDate baseDate);

    @Operation(
            summary = "TODO 항목 상태 변경 (TODO-05)",
            description = """
                    체크리스트(DO) 항목의 완료 처리·되돌리기를 같은 엔드포인트로 처리한다
                    (`PENDING` ↔ `DONE`).

                    ### AVOID 항목은 체크할 수 없다

                    "오늘은 피하세요" 카드는 완료 개념이 없다. 해당 id로 요청하면
                    **`400 ACTION_NOT_CHECKABLE`** 을 반환한다.

                    ### exp는 상태가 실제로 바뀔 때만 움직인다

                    | 요청 | `expGained` |
                    |---|---|
                    | `PENDING` → `DONE` | `+10` |
                    | `DONE` → `PENDING` (되돌리기) | `-10` |
                    | 같은 상태로 재요청 | `0` |

                    **되돌리면 회수한다.** 회수하지 않으면 체크를 껐다 켜는 것만으로 exp가 계속
                    붙는다 — 중복 호출만 막는 것으로는 닫히지 않는다.

                    `expGained`는 요청한 양이 아니라 **실제 증감**이다. 누적 exp는 0 밑으로
                    내려가지 않으므로, 0에서 되돌리면 `-10`보다 작은 값이 담긴다.
                    `totalExp`는 조정 이후 사용자의 누적 exp다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`ACTION_NOT_CHECKABLE` — AVOID 항목은 체크할 수 없음 · `INVALID_INPUT` — `status` 누락 또는 알 수 없는 값",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "ACTION_NOT_CHECKABLE",
                            ref = "#/components/examples/ACTION_NOT_CHECKABLE"),
                    @ExampleObject(name = "INVALID_INPUT",
                            ref = "#/components/examples/INVALID_INPUT")}))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`TODO_NOT_FOUND` — 해당 사용자의 TODO 항목이 없음 (**남의 항목도 404다** — 403이면 그 id의 존재가 새어 나간다) · `USER_NOT_FOUND`",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "TODO_NOT_FOUND",
                            ref = "#/components/examples/TODO_NOT_FOUND"),
                    @ExampleObject(name = "USER_NOT_FOUND",
                            ref = "#/components/examples/USER_NOT_FOUND")}))
    ApiResponse<TodoStatusUpdateResponse> updateStatus(
            @CurrentUserId Long userId,
            @Parameter(description = "daily_todo PK") Long id,
            @Valid TodoStatusUpdateRequest request);

}
