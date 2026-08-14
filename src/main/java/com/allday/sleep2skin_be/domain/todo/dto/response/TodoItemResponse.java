package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.todo.CauseLabelMapper;
import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;
import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TODO 항목 하나. AVOID·DO가 서로 다른 필드를 채운다.
 *
 * <p><b>AVOID</b>: {@code causeLabel}(태그) + {@code reason}(롱프레스 노출용 긴 설명) 포함,
 * {@code status}는 항상 null — 체크 대상이 아니다.
 *
 * <p><b>DO</b>: {@code status}만 포함, {@code causeLabel}·{@code reason}은 항상 null.
 * "n/5" 같은 진행도 계산은 프론트가 {@code status}를 세어서 한다 — 서버는 계산된 진행도를
 * 내려주지 않는다.
 */
@Schema(description = "TODO 항목 (AVOID 또는 DO)")
public record TodoItemResponse(

        @Schema(description = "daily_todo PK. PATCH 요청의 경로 변수로 쓰인다")
        Long id,

        @Schema(description = "AVOID(피하세요) 또는 DO(체크리스트)")
        ActionCategory category,

        @Schema(description = "행동 제목", example = "강한 각질 제거")
        String title,

        @Schema(description = "AVOID 카드에만 노출되는 원인 태그. target_metric에서 자동 생성된다. "
                + "**DO 항목은 항상 `null`**", nullable = true,
                example = "장벽 약화의 원인")
        String causeLabel,

        @Schema(description = "AVOID 카드를 꾹 눌렀을 때(long press) 추가로 보이는 설명. "
                + "**DO 항목은 항상 `null`**", nullable = true,
                example = "과도한 각질 제거는 피부를 자극하고 장벽을 약하게 만들 수 있어요")
        String reason,

        @Schema(description = "DO 항목의 체크 상태. **AVOID는 체크 대상이 아니라 항상 `null`**",
                nullable = true)
        TodoStatus status
) {

    public static TodoItemResponse of(DailyTodo todo) {
        ActionMaster action = todo.getActionMaster();
        if (action.getCategory() == ActionCategory.AVOID) {
            return new TodoItemResponse(todo.getId(), ActionCategory.AVOID, action.getTitle(),
                    CauseLabelMapper.labelOf(action.getTargetMetric()), action.getReason(), null);
        }
        return new TodoItemResponse(todo.getId(), ActionCategory.DO, action.getTitle(),
                null, null, todo.getStatus());
    }

}
