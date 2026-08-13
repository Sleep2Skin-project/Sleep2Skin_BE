package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TODO-05 상태 변경 응답. exp는 DO 항목이 처음 DONE으로 바뀔 때만 지급된다.
 *
 * @param expGained 이번 요청으로 새로 지급된 exp. 이미 DONE이던 항목을 다시 PATCH하거나
 *                  DONE→PENDING으로 되돌리는 경우는 0 (멱등 처리, 중복 지급 방지)
 * @param totalExp  지급 이후 사용자의 누적 exp 총합
 */
@Schema(description = "TODO 상태 변경 결과")
public record TodoStatusUpdateResponse(

        @Schema(description = "daily_todo PK")
        Long id,

        @Schema(description = "변경된 상태")
        TodoStatus status,

        @Schema(description = "이번 요청으로 새로 지급된 exp. 중복 체크·되돌리기는 0", example = "10")
        int expGained,

        @Schema(description = "지급 이후 누적 exp 총합", example = "120")
        int totalExp
) {
}
