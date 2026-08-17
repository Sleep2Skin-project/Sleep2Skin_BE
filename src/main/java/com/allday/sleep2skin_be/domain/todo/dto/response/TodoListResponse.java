package com.allday.sleep2skin_be.domain.todo.dto.response;

import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * TODO-01~04 통합 응답. "오늘은 피하세요"(상위 3) / "오늘 밤 체크리스트"(상위 5) 두 섹션.
 *
 * <p><b>모든 조회 API가 공유하는 {@code {status, message, 페이로드}} 형태다</b>(conventions.md §2).
 * 화면마다 다른 스키마가 생기지 않게 하는 것이 이 규칙의 핵심이며, {@code SkinForecastQueryResponse}가
 * 기준 패턴이다.
 *
 * <p><b>빈 상태에서 두 배열은 {@code null}이 아니라 {@code []}다.</b> 예보 객체 하나를 통째로
 * 비우는 다른 조회 API와 달리 여기 페이로드는 배열이라, 목록을 그리는 코드가 null 검사 없이
 * 그대로 돌아가는 편이 낫다. 앱은 {@code status}로만 분기한다.
 *
 * @param status 앱은 <b>문구가 아니라 이 값으로 분기한다</b>
 * @param message 사용자에게 그대로 보여줄 수 있는 한국어 문장. 정상이면 {@code null}
 */
@Schema(description = "오늘의 TODO 목록")
public record TodoListResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "기준일", example = "2026-08-13")
        LocalDate baseDate,

        @Schema(description = "오늘은 피하세요 (상위 3개)")
        List<TodoItemResponse> avoidItems,

        @Schema(description = "오늘 밤 체크리스트 (상위 5개)")
        List<TodoItemResponse> checklistItems
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "수면 데이터가 없어 오늘의 처방이 없습니다.";

    /**
     * baseDate를 파라미터로 받는다 — todos가 비어 있을 수 있어(그날 예보가 산출된 지표를
     * 겨냥한 후보가 하나도 없는 경우) todos.getFirst()로 날짜를 꺼내면 예외가 난다.
     *
     * <p><b>후보가 0개인 날도 {@code AVAILABLE}이다.</b> "예보가 없다"와 "예보는 있는데 처방할
     * 것이 없다"는 다른 상태이고, 앱이 띄울 문구도 다르다.
     */
    public static TodoListResponse of(LocalDate baseDate, List<DailyTodo> todos) {
        List<TodoItemResponse> items = todos.stream().map(TodoItemResponse::of).toList();

        return new TodoListResponse(
                QueryStatus.AVAILABLE, null, baseDate,
                filterBy(items, ActionCategory.AVOID),
                filterBy(items, ActionCategory.DO));
    }

    /**
     * 그날 예보가 없어 목록을 만들 수 없다. <b>에러가 아니라 정상 응답이다</b> — 수면을 아직
     * 올리지 않은 신규 사용자가 TODO 탭을 열면 일상적으로 발생한다. 4xx로 내리면 경로 오타나
     * 잘못된 {@code userId}와 섞여 모니터링에서 신규 유입이 에러 급증으로 보인다.
     *
     * <p>사유가 수면 데이터 부재로 예보 조회와 같으므로 {@code NO_SLEEP_DATA}를 그대로 쓴다.
     */
    public static TodoListResponse empty(LocalDate baseDate) {
        return new TodoListResponse(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE, baseDate,
                List.of(), List.of());
    }

    private static List<TodoItemResponse> filterBy(List<TodoItemResponse> items, ActionCategory category) {
        return items.stream()
                .filter(item -> item.category() == category)
                .toList();
    }

}
