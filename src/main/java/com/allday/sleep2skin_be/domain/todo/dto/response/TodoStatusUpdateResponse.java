package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TODO-05 상태 변경 응답. exp는 DO 항목의 상태가 실제로 바뀔 때만 움직인다.
 *
 * @param expGained 이번 요청으로 변한 exp의 양. 완료로 바뀌면 {@code +10}, 되돌리면 {@code -10},
 *                  같은 상태로 다시 PATCH하면 {@code 0}이다(멱등).
 *                  <b>회수가 0에서 막히면 {@code -10}보다 작은 값이 담긴다</b> — 요청한 양이
 *                  아니라 실제 증감이다
 * @param totalExp  조정 이후 사용자의 누적 exp 총합
 */
@Schema(description = "TODO 상태 변경 결과")
public record TodoStatusUpdateResponse(

        @Schema(description = "daily_todo PK")
        Long id,

        @Schema(description = "변경된 상태")
        TodoStatus status,

        @Schema(description = "이번 요청으로 변한 exp. 완료 `+10` · 되돌리기 `-10` · 같은 상태 재요청 `0`",
                example = "10")
        int expGained,

        @Schema(description = "조정 이후 누적 exp 총합", example = "120")
        int totalExp
) {
}
