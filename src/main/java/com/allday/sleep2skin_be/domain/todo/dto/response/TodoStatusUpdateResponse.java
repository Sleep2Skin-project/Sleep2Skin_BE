package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TODO-05 상태 변경 응답. exp는 {@code DO} 항목의 상태가 실제로 바뀔 때만 움직인다.
 *
 * <p><b>{@code exp}는 적립이 일어나는 네 API가 공유하는 객체다</b>(api.md §1) — 출석·수면
 * 업로드·셀피 검증과 같은 모양이라 앱이 파싱 코드와 레벨 업 연출을 한 번만 만들면 된다.
 * 예전의 {@code expGained}·{@code totalExp} 두 필드를 대체한 것이며, 그 둘은 각각
 * {@code exp.gained}·{@code exp.totalExp}에 들어 있다.
 *
 * @param allCompleted <b>지금 그날 {@code DO}가 전부 {@code DONE}인가</b> — "이번 요청으로
 *                     그렇게 됐는가"가 아니다. 전이는 {@code exp.reasons}에 {@code TODO_ALL_DONE}이
 *                     실렸는지로 이미 알 수 있어, 여기까지 전이를 담으면 <b>같은 사실을 두 곳이
 *                     말하게 된다.</b> 상태로 두면 같은 요청을 다시 보내도 값이 참으로 남는다
 */
@Schema(description = "TODO 상태 변경 결과")
public record TodoStatusUpdateResponse(

        @Schema(description = "daily_todo PK")
        Long id,

        @Schema(description = "변경된 상태")
        TodoStatus status,

        @Schema(description = "지금 그날 `DO`가 전부 완료됐는가. **이번 요청으로 그렇게 됐는지가 "
                + "아니라 현재 상태다** — 전이는 `exp.reasons`의 `TODO_ALL_DONE`으로 알 수 있다",
                example = "true")
        boolean allCompleted,

        @Schema(description = "exp 적립 결과. 적립이 일어나는 네 API가 같은 모양을 쓴다")
        ExpResponse exp
) {

    public static TodoStatusUpdateResponse of(DailyTodo todo, boolean allCompleted, ExpResponse exp) {
        return new TodoStatusUpdateResponse(todo.getId(), todo.getStatus(), allCompleted, exp);
    }

}
