package com.allday.sleep2skin_be.domain.todo.dto.request;

import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "TODO 상태 변경 요청")
public record TodoStatusUpdateRequest(

        @Schema(description = "변경할 상태. PENDING/DONE 양방향 모두 이 필드로 처리한다", example = "DONE")
        @NotNull(message = "상태는 필수입니다.")
        TodoStatus status
) {
}
