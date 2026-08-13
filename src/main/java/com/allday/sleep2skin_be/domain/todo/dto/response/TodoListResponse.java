package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO-01~04 통합 응답. "오늘은 피하세요"(상위 3) / "오늘 밤 체크리스트"(상위 5) 두 섹션.
 */
@Schema(description = "오늘의 TODO 목록")
public record TodoListResponse(

        @Schema(description = "기준일", example = "2026-08-13")
        LocalDate baseDate,

        @Schema(description = "오늘은 피하세요 (상위 3개)")
        List<TodoItemResponse> avoidItems,

        @Schema(description = "오늘 밤 체크리스트 (상위 5개)")
        List<TodoItemResponse> checklistItems
) {

    /**
     * baseDate를 파라미터로 받는다 — todos가 비어 있을 수 있어(예: 그날 모든 지표가 임계값보다
     * 좋아서 AVOID·DO 후보가 0개인 경우) todos.getFirst()로 날짜를 꺼내면 예외가 난다.
     */
    public static TodoListResponse from(LocalDate baseDate, List<DailyTodo> todos) {
        List<TodoItemResponse> items = todos.stream().map(TodoItemResponse::of).toList();

        return new TodoListResponse(
                baseDate,
                filterBy(items, ActionCategory.AVOID),
                filterBy(items, ActionCategory.DO));
    }

    private static List<TodoItemResponse> filterBy(List<TodoItemResponse> items, ActionCategory category) {
        return items.stream()
                .filter(item -> item.category() == category)
                .collect(Collectors.toList());
    }

}
