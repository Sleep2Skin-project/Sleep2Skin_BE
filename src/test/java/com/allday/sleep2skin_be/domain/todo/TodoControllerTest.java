package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoItemResponse;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoListResponse;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoStatusUpdateResponse;
import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    private static final String PATH = "/api/v1/todo";
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 13);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoService todoService;

    @Test
    @DisplayName("목록은 두 섹션으로 나뉘어 나간다")
    void 두_섹션으로_응답한다() throws Exception {
        given(todoService.getTodos(USER_ID, BASE_DATE)).willReturn(new TodoListResponse(
                QueryStatus.AVAILABLE, null, BASE_DATE,
                List.of(new TodoItemResponse(41L, ActionCategory.AVOID, "강한 각질 제거",
                        "장벽 약화의 원인", "과도한 각질 제거는 피부를 자극할 수 있어요", null)),
                List.of(new TodoItemResponse(44L, ActionCategory.DO, "보습 크림 바르기",
                        null, null, TodoStatus.PENDING))));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.avoidItems[0].causeLabel").value("장벽 약화의 원인"))
                .andExpect(jsonPath("$.data.avoidItems[0].status").doesNotExist())
                .andExpect(jsonPath("$.data.checklistItems[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.checklistItems[0].causeLabel").doesNotExist());
    }

    /**
     * <b>이 응답이 404였다.</b> 수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면 일상적으로
     * 발생하므로, 4xx로 내리면 진짜 문제가 그 안에 묻힌다(conventions.md §2).
     */
    @Test
    @DisplayName("예보가 없는 날도 200이고 status로 알린다")
    void 빈_상태도_200이다() throws Exception {
        given(todoService.getTodos(USER_ID, BASE_DATE))
                .willReturn(TodoListResponse.empty(BASE_DATE));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NO_SLEEP_DATA"))
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                .andExpect(jsonPath("$.data.avoidItems").isArray())
                .andExpect(jsonPath("$.data.avoidItems").isEmpty())
                .andExpect(jsonPath("$.data.checklistItems").isEmpty());
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400이고 서비스를 부르지 않는다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.USER_ID_HEADER_INVALID.name()));

        verify(todoService, never()).getTodos(anyLong(), any());
    }

    /** 서버는 "오늘"을 모른다 — 기준일이 없으면 계산 자체가 성립하지 않는다. */
    @Test
    @DisplayName("baseDate가 없으면 400이다")
    void 기준일이_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));

        verify(todoService, never()).getTodos(anyLong(), any());
    }

    /**
     * <b>exp는 적립이 일어나는 네 API가 공유하는 객체다</b>(api.md §1). 이 엔드포인트만 다른
     * 모양이면 앱이 파싱 코드를 두 벌 갖게 된다.
     */
    @Test
    @DisplayName("완료 처리하면 exp 객체가 함께 나온다")
    void 완료하면_exp가_나온다() throws Exception {
        given(todoService.updateStatus(USER_ID, 44L, TodoStatus.DONE))
                .willReturn(new TodoStatusUpdateResponse(44L, TodoStatus.DONE, true,
                        ExpResponse.of(320, 355, List.of(
                                new ExpReasonResponse(ExpReason.TODO_DONE, 5),
                                new ExpReasonResponse(ExpReason.TODO_ALL_DONE, 30)))));

        mockMvc.perform(patch(PATH + "/44").header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.allCompleted").value(true))
                .andExpect(jsonPath("$.data.exp.gained").value(35))
                .andExpect(jsonPath("$.data.exp.totalExp").value(355))
                .andExpect(jsonPath("$.data.exp.reasons[0].reason").value("TODO_DONE"))
                .andExpect(jsonPath("$.data.exp.reasons[1].reason").value("TODO_ALL_DONE"))
                // 옛 필드가 남아 있으면 앱이 둘 다 읽는 코드를 유지하게 된다
                .andExpect(jsonPath("$.data.expGained").doesNotExist())
                .andExpect(jsonPath("$.data.totalExp").doesNotExist());
    }

    /** 되돌리기는 회수라 음수가 실린다 — 앱이 부호를 그대로 더하면 된다. */
    @Test
    @DisplayName("되돌리면 exp.gained가 음수로 나간다")
    void 되돌리면_음수가_나온다() throws Exception {
        given(todoService.updateStatus(USER_ID, 44L, TodoStatus.PENDING))
                .willReturn(new TodoStatusUpdateResponse(44L, TodoStatus.PENDING, false,
                        ExpResponse.of(355, 320, List.of(
                                new ExpReasonResponse(ExpReason.TODO_DONE, -5),
                                new ExpReasonResponse(ExpReason.TODO_ALL_DONE, -30)))));

        mockMvc.perform(patch(PATH + "/44").header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allCompleted").value(false))
                .andExpect(jsonPath("$.data.exp.gained").value(-35))
                .andExpect(jsonPath("$.data.exp.totalExp").value(320));
    }

    @Test
    @DisplayName("AVOID 항목을 체크하려 하면 400 ACTION_NOT_CHECKABLE이다")
    void 피하세요는_체크할_수_없다() throws Exception {
        given(todoService.updateStatus(USER_ID, 41L, TodoStatus.DONE))
                .willThrow(new BusinessException(ErrorCode.ACTION_NOT_CHECKABLE));

        mockMvc.perform(patch(PATH + "/41").header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ACTION_NOT_CHECKABLE.name()))
                .andExpect(jsonPath("$.error.message")
                        .value(ErrorCode.ACTION_NOT_CHECKABLE.getMessage()));
    }

    @Test
    @DisplayName("status가 없거나 알 수 없는 값이면 400이다")
    void 상태값이_잘못되면_400이다() throws Exception {
        mockMvc.perform(patch(PATH + "/44").header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));

        mockMvc.perform(patch(PATH + "/44").header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));

        verify(todoService, never()).updateStatus(anyLong(), anyLong(), any());
    }

}
