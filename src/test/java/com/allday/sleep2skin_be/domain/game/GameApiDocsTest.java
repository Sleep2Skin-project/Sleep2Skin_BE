package com.allday.sleep2skin_be.domain.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code game} 도메인의 API 문서 검증 (HOME-04).
 *
 * <p>도메인과 무관한 규칙(실패 스키마·미디어 타입·에러 예시 생성)은 {@code SwaggerConfigTest}가
 * 문서 전체를 순회하며 본다. 여기서는 <b>이 도메인만의 것</b>을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameApiDocsTest {

    private static final String ATTENDANCE_POST = "$.paths.['/api/v1/users/me/attendance'].post";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 문서는 {@link GameControllerSpec} 인터페이스에 있다. springdoc이 인터페이스의 어노테이션을
     * 못 찾으면 <b>설명만 조용히 사라지고 API는 그대로 뜬다.</b>
     */
    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST + ".summary").value(containsString("출석")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".summary").value(containsString("HOME-04")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".description")
                        .value(containsString("앱이 시작될 때 한 번 호출한다")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Game')].description")
                        .value(hasItem("레벨 · 경험치 API (HOME-04)")));
    }

    /**
     * <b>여기서만 문서로 지킬 수 있는 것이다.</b> 앱이 재호출을 에러로 처리하면 하루에 네 번씩
     * 실패 화면을 띄운다 — 서버는 정상 응답을 냈는데도.
     */
    @Test
    @DisplayName("재호출이 200이라는 것과 checkedIn 표가 문서에 있다")
    void 재호출_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST + ".description").value(containsString("checkedIn")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".description")
                        .value(containsString("`409`가 아니라 `200`이다")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".responses.['200'].description")
                        .value(containsString("재호출")));
    }

    /**
     * <b>연속 보상을 여기서 주지 않는다는 것이 문서에 있어야 한다.</b> 앱이 이 응답의
     * {@code streakCount}를 보고 보상 팝업을 그리는데, 지급이 셀피 쪽이라는 걸 모르면
     * 출석만 하고도 보상을 받은 것처럼 표시한다.
     */
    @Test
    @DisplayName("streakCount가 출석 연속이 아니라는 것과 보상 지급처가 문서에 있다")
    void 연속_보상의_지급처가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST + ".description")
                        .value(containsString("출석 연속이 아니다")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".description")
                        .value(containsString("POST /skin/selfie")));
    }

    /** 서버는 "오늘"을 모른다 — 없이 처리하면 출석이 어제 날짜로 찍힌다. */
    @Test
    @DisplayName("baseDate 파라미터와 X-User-Id 헤더가 문서에 붙는다")
    void 파라미터가_문서에_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST + ".parameters[?(@.name == 'baseDate')].in")
                        .value(hasItem("query")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".parameters[?(@.name == 'baseDate')].required")
                        .value(hasItem(true)))
                .andExpect(jsonPath(ATTENDANCE_POST + ".parameters[?(@.name == 'X-User-Id')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath(ATTENDANCE_POST + ".parameters[?(@.name == 'X-User-Id')].required")
                        .value(hasItem(true)));
    }

    @Test
    @DisplayName("성공 응답이 출석 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST
                        + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseAttendanceResponse"));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ATTENDANCE_POST
                        + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(ATTENDANCE_POST
                        + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    /**
     * <b>exp 객체는 네 API가 함께 쓴다</b>(api.md §1). 앱이 파싱 코드를 한 번만 만들 수 있어야
     * 하므로 필드 이름이 흔들리면 안 된다.
     */
    @Test
    @DisplayName("exp 응답 스키마가 6필드를 그대로 갖는다")
    void exp_스키마가_공통_규격이다() throws Exception {
        String expSchema = "$.components.schemas.ExpResponse.properties";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(expSchema + ".gained").exists())
                .andExpect(jsonPath(expSchema + ".reasons").exists())
                .andExpect(jsonPath(expSchema + ".totalExp").exists())
                .andExpect(jsonPath(expSchema + ".level").exists())
                .andExpect(jsonPath(expSchema + ".levelUp").exists())
                .andExpect(jsonPath(expSchema + ".nextLevelExp").exists());
    }

}
