package com.allday.sleep2skin_be.domain.todo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code todo} 도메인의 API 문서 검증.
 *
 * <p>문서가 어긋나도 서버 테스트는 전부 통과한다 — 문서는 프론트만 보기 때문에 앱이 잘못
 * 구현한 뒤에야 드러난다(conventions.md §11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoApiDocsTest {

    private static final String TODO_GET = "$.paths.['/api/v1/todo'].get";
    private static final String TODO_PATCH = "$.paths.['/api/v1/todo/{id}'].patch";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".summary").value(containsString("TODO 목록 조회")))
                .andExpect(jsonPath(TODO_PATCH + ".summary").value(containsString("TODO-05")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Todo')].description")
                        .value(hasItem(containsString("오늘의 행동 추천"))))
                // TODO-01의 요약 멘트는 서버가 만들지 않는다 — 태그가 범위를 부풀리면 안 된다
                .andExpect(jsonPath("$.tags[?(@.name == 'Todo')].description")
                        .value(hasItem(containsString("TODO-02~05"))));
    }

    /**
     * <b>이 응답은 한때 404였다.</b> 수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면
     * 일상적으로 발생하므로, 앱이 에러 화면을 띄우면 안 된다.
     */
    @Test
    @DisplayName("예보가 없는 날이 200이라는 것과 status로 분기하라는 안내가 문서에 있다")
    void 빈_상태_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("404가 아니다")))
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("`status`로 분기")))
                .andExpect(jsonPath(TODO_GET + ".responses.['200'].description")
                        .value(containsString("예보가 없는 경우도")))
                // 404는 남아 있지만 예보 부재가 아니라 사용자 부재일 때만이다
                .andExpect(jsonPath(TODO_GET + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"))
                .andExpect(jsonPath(TODO_GET + ".responses.['404'].content.['application/json'].examples.SKIN_FORECAST_NOT_FOUND")
                        .doesNotExist());
    }

    /**
     * 앱이 두 상태를 같게 다루면 <b>처방할 것이 없는 날에 "수면을 동기화하세요"라고 띄운다.</b>
     * 둘 다 배열이 비어 있어 페이로드만으로는 구분되지 않는다.
     */
    @Test
    @DisplayName("후보 0개와 예보 부재가 다른 상태라는 것이 문서에 있다")
    void 두_빈_상태의_구분이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".description")
                        .value(containsString("\"예보가 없다\"와 \"처방할 것이 없다\"는 다른 상태")));
    }

    /**
     * <b>목록이 조회마다 달라지면 이미 체크한 항목이 사라진 것처럼 보인다.</b> 프론트가 캐시
     * 전략을 정할 때 필요한 정보이기도 하다.
     */
    @Test
    @DisplayName("목록이 그날 첫 조회에 고정된다는 것이 문서에 있다")
    void 목록_고정_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".description")
                        .value(containsString("그날 첫 조회 시 만들어져 고정된다")));
    }

    /**
     * <b>서버가 진행도를 내려주면 항목 배열과 숫자 두 곳이 같은 사실을 말하게 된다.</b> 체크
     * 직후 화면에서 둘이 어긋나므로, 계산 주체를 문서가 못 박아 둔다.
     */
    @Test
    @DisplayName("진행도 계산이 클라이언트 몫이라는 것이 문서에 있다")
    void 진행도_주체가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("n/5")))
                .andExpect(jsonPath(TODO_GET + ".description")
                        .value(containsString("서버는 계산된 진행도를 내려주지")));
    }

    /**
     * 점수 축과 위험 축이 반대라 <b>문구에서 뒤집히기 가장 쉬운 자리</b>다.
     * {@code OVERESTIMATED}는 점수를 높게 낸 것 = 위험을 과소평가한 것이다.
     */
    @Test
    @DisplayName("verdictBonus의 방향이 문서에 있다")
    void 보너스_방향이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("OVERESTIMATED")))
                .andExpect(jsonPath(TODO_GET + ".description").value(containsString("과소평가")));
    }

    /**
     * <b>되돌리기에서 음수가 나온다는 것을 앱이 모르면</b> 부호를 무시하고 더해 exp가 계속
     * 올라가는 화면이 된다. 서버가 고친 버그가 클라이언트에서 되살아난다.
     *
     * <p>전체 완료 보너스도 같은 자리다 — {@code +30}만 알고 {@code −30}을 모르면 마찬가지다.
     */
    @Test
    @DisplayName("exp 회수와 음수 gained가 문서에 있다")
    void exp_회수가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("되돌리면 회수한다")))
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("`-5`")))
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("`-35`")))
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("실제 증감")))
                // 회수 전의 설명이 남아 있으면 안 된다
                .andExpect(jsonPath(TODO_PATCH + ".description")
                        .value(not(containsString("되돌리는 요청은 `expGained`가 0"))))
                // 확정값이 +5인데 문서에 옛 +10이 남아 있으면 앱이 그것을 믿는다
                .andExpect(jsonPath(TODO_PATCH + ".description")
                        .value(not(containsString("`+10`"))));
    }

    /**
     * <b>{@code exp}는 네 API가 공유하는 객체다</b>(api.md §1). 이 엔드포인트만 옛
     * {@code expGained}·{@code totalExp}로 남으면 앱이 파싱 코드를 두 벌 갖게 된다.
     */
    @Test
    @DisplayName("exp 객체와 전체 완료 보너스가 문서에 있다")
    void exp_객체와_보너스가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("TODO_ALL_DONE")))
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("allCompleted")))
                .andExpect(jsonPath(TODO_PATCH + ".description").value(containsString("현재 상태")));
    }

    @Test
    @DisplayName("AVOID를 체크할 수 없다는 것과 400 예시가 문서에 있다")
    void 체크_불가_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_PATCH + ".description")
                        .value(containsString("ACTION_NOT_CHECKABLE")))
                .andExpect(jsonPath(TODO_PATCH + ".responses.['400'].content.['application/json'].examples.ACTION_NOT_CHECKABLE.['$ref']")
                        .value("#/components/examples/ACTION_NOT_CHECKABLE"))
                .andExpect(jsonPath(TODO_PATCH + ".responses.['404'].content.['application/json'].examples.TODO_NOT_FOUND.['$ref']")
                        .value("#/components/examples/TODO_NOT_FOUND"));
    }

    /**
     * <b>인덱스가 아니라 이름으로 찾는다.</b> springdoc이 매기는 파라미터 순서는 보장되지 않는다.
     */
    @Test
    @DisplayName("X-User-Id 헤더와 baseDate 쿼리 파라미터가 둘 다 필수로 문서에 붙는다")
    void 기준일_파라미터가_문서에_붙는다() throws Exception {
        String userIdParameter = TODO_GET + ".parameters[?(@.name == 'X-User-Id')]";
        String baseDateParameter = TODO_GET + ".parameters[?(@.name == 'baseDate')]";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(userIdParameter + ".in").value(hasItem("header")))
                .andExpect(jsonPath(userIdParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(baseDateParameter + ".in").value(hasItem("query")))
                .andExpect(jsonPath(baseDateParameter + ".required").value(hasItem(true)))
                // 서버는 "오늘"을 모른다 — 앱이 로컬 날짜를 보내야 한다는 것이 문서의 유일한 방어선이다
                .andExpect(jsonPath(baseDateParameter + ".description")
                        .value(hasItem(containsString("앱의 로컬 날짜를 보낸다"))));
    }

    /**
     * <b>AVOID와 DO가 서로 다른 필드를 채운다.</b> nullable을 빼면 코드 생성기가 non-null로
     * 타입을 만들어, 반대쪽 카테고리를 파싱할 때 클라이언트가 깨진다.
     */
    @Test
    @DisplayName("카테고리에 따라 비는 필드가 nullable로 문서에 표시된다")
    void 비는_필드가_nullable이다() throws Exception {
        String item = "$.components.schemas.TodoItemResponse.properties";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".causeLabel.type").value(hasItem("null")))
                .andExpect(jsonPath(item + ".reason.type").value(hasItem("null")))
                .andExpect(jsonPath(item + ".status.description").value(containsString("항상 `null`")))
                .andExpect(jsonPath("$.components.schemas.TodoListResponse.properties.message.type")
                        .value(hasItem("null")));
    }

    @Test
    @DisplayName("성공 응답이 목록 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TODO_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseTodoListResponse"));
    }

}
