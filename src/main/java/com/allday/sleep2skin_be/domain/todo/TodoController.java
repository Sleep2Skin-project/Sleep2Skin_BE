package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.todo.dto.request.TodoStatusUpdateRequest;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoListResponse;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoStatusUpdateResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * TODO 목록 조회·상태 변경 API.
 *
 * <p>Swagger 문서는 {@link TodoControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/todo")
@RequiredArgsConstructor
public class TodoController implements TodoControllerSpec {

    private final TodoService todoService;

    @Override
    @GetMapping
    public ApiResponse<TodoListResponse> getTodos(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(todoService.getTodos(userId, baseDate));
    }

    @Override
    @PatchMapping("/{id}")
    public ApiResponse<TodoStatusUpdateResponse> updateStatus(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TodoStatusUpdateRequest request) {

        return ApiResponse.success(todoService.updateStatus(userId, id, request.status()));
    }

}
