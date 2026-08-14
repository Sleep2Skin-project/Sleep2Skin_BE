package com.allday.sleep2skin_be.domain.user;

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
 * {@code user} 도메인의 API 문서 검증.
 *
 * <p>도메인과 무관한 규칙(실패 스키마·미디어 타입·에러 예시 생성)은
 * {@code SwaggerConfigTest}가 문서 전체를 순회하며 본다. 여기서는 <b>이 도메인만의 것</b>을 본다 —
 * 어떤 설명이 실렸는지, 어떤 에러 예시를 골랐는지.
 *
 * <p>도메인별로 파일을 나눈 이유는 나중에 도메인 단위로 작업을 나누기 위해서다(workflow.md §4).
 * 한 파일에 모아두면 두 사람이 계속 같은 파일을 건드린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiDocsTest {

    private static final String CONSENTS_POST = "$.paths.['/api/v1/users/me/consents'].post";
    private static final String ONBOARDING_PATCH = "$.paths.['/api/v1/users/me/onboarding'].patch";
    private static final String ME_GET = "$.paths.['/api/v1/users/me'].get";
    private static final String ME_DELETE = "$.paths.['/api/v1/users/me'].delete";
    private static final String DATA_STATUS_GET = "$.paths.['/api/v1/users/me/data-status'].get";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 문서는 {@link UserControllerSpec} 인터페이스에 있다. springdoc이 인터페이스의 어노테이션을
     * 못 찾으면 <b>설명만 조용히 사라지고 API는 그대로 뜬다.</b> 컴파일도 테스트도 통과하므로
     * 프론트가 빈 문서를 보기 전까지 아무도 모른다.
     */
    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".summary").value(containsString("동의")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("멱등")))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].description")
                        .value(containsString("newlyAgreed")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".summary").value(containsString("온보딩")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("ONB-02")));
    }

    /**
     * {@code @Tag} 설명은 Swagger UI에서 태그를 접어도 계속 펼쳐진 채 목록 맨 위를 차지한다.
     * 공통 규약을 거기 넣으면 API 목록을 훑기가 불편해져, 태그는 한 줄로 두고 규약은 각 API 설명에
     * 되풀이해 적기로 했다.
     */
    @Test
    @DisplayName("공통 규약은 태그가 아니라 각 API 설명에 들어 있다")
    void 공통_규약이_API마다_적혀_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[?(@.name == 'User')].description")
                        .value(hasItem("사용자 · 동의 · 온보딩 API")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(CONSENTS_POST + ".description").value(containsString("USER_NOT_FOUND")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("X-User-Id")))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".description").value(containsString("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['400'].content.['application/json'].examples.USER_ID_HEADER_INVALID.['$ref']")
                        .value("#/components/examples/USER_ID_HEADER_INVALID"))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("@CurrentUserId를 쓰는 API에 X-User-Id 헤더가 붙는다")
    void 헤더_파라미터가_문서에_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].name").value("X-User-Id"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].in").value("header"))
                .andExpect(jsonPath(CONSENTS_POST + ".parameters[0].required").value(true))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".parameters[0].name").value("X-User-Id"));
    }

    @Test
    @DisplayName("성공 응답은 각 API의 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['201'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath(CONSENTS_POST + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseConsentAgreeResponse"))
                .andExpect(jsonPath(ONBOARDING_PATCH + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseOnboardingCompleteResponse"))
                .andExpect(jsonPath(ME_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseUserProfileResponse"))
                .andExpect(jsonPath(DATA_STATUS_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseSleepDataStatusResponse"))
                .andExpect(jsonPath(ME_DELETE + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseUserDeleteResponse"));
    }

    /**
     * <b>앱이 시작될 때 가장 먼저 보는 API다.</b> 두 불리언의 조합이 어느 화면으로 가는지를
     * 정하므로, 그 표가 문서에서 사라지면 프론트가 분기를 추측해야 한다.
     */
    @Test
    @DisplayName("진입 분기 표와 consentAgreed의 뜻이 문서에 있다")
    void 진입_분기가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_GET + ".summary").value(containsString("ONB-01")))
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("가장 먼저 호출한다")))
                // "동의한 적이 있는가"로 읽으면 약관 개정 후 재동의 화면이 영원히 안 뜬다
                .andExpect(jsonPath(ME_GET + ".description")
                        .value(containsString("\"현재 약관 버전에 동의했는가\"다")))
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("로컬 플래그로는")));
    }

    /**
     * 서버는 "오늘"을 모른다. 이 설명이 없으면 앱이 baseDate 를 왜 보내야 하는지 알 수 없고,
     * 서버 시각으로 대신하려는 시도가 나온다.
     */
    @Test
    @DisplayName("프로필 조회의 baseDate가 필수이고 왜 필요한지가 문서에 있다")
    void 기준일_이유가_문서에_있다() throws Exception {
        String baseDateParameter = ME_GET + ".parameters[?(@.name == 'baseDate')]";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(baseDateParameter + ".in").value(hasItem("query")))
                .andExpect(jsonPath(baseDateParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(baseDateParameter + ".description")
                        .value(hasItem(containsString("앱의 로컬 날짜를 보낸다"))))
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("하루 밀린다")))
                // 나머지 둘은 날짜에 따라 달라지는 값이 없어 받지 않는다
                .andExpect(jsonPath(DATA_STATUS_GET + ".parameters[?(@.name == 'baseDate')]").isEmpty());
    }

    /**
     * <b>MY-01과 HOME-09가 같은 숫자를 써야 한다</b>(prd.md §4.2). 문서가 그 사실을 잃으면
     * 프론트가 두 화면에서 다른 값을 기대하게 된다.
     */
    @Test
    @DisplayName("MY-01 숫자가 HOME-09와 같은 값이고 등급이 아니라는 설명이 문서에 있다")
    void 프로필_숫자_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("HOME-09 배너와 같은 계산")))
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("등급이 아니라 숫자")))
                .andExpect(jsonPath(ME_GET + ".description")
                        .value(containsString("오늘 미검증은 연속을 끊지 않는다")));
    }

    /**
     * 다른 조회 API와 규칙이 <b>반대</b>인 자리다 — 여기만 {@code status}가 없다.
     * 프론트가 습관적으로 {@code data.status}를 읽으면 undefined 가 나온다.
     */
    @Test
    @DisplayName("프로필에는 빈 상태가 없다는 것이 문서에 있다")
    void 빈_상태가_없다는_것이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_GET + ".description").value(containsString("빈 상태가 없다")))
                .andExpect(jsonPath(ME_GET + ".responses.['200'].description")
                        .value(containsString("신규 사용자도 여기에 해당한다")))
                // 반면 연결 상태는 빈 상태가 있다
                .andExpect(jsonPath(DATA_STATUS_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(DATA_STATUS_GET + ".description").value(containsString("`status`로 분기")));
    }

    /**
     * <b>"마지막으로 잔 날"로 오해하면 화면이 거짓말을 한다</b> — 며칠 전 데이터를 방금 올린
     * 경우에 "동기화가 며칠째 안 됐다"고 표시된다.
     */
    @Test
    @DisplayName("연결 상태가 수신 시각이라는 것과 서버가 알 수 없는 것이 문서에 있다")
    void 연결_상태_의미가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DATA_STATUS_GET + ".summary").value(containsString("MY-02")))
                .andExpect(jsonPath(DATA_STATUS_GET + ".description")
                        .value(containsString("\"마지막으로 잔 날\"이 아니라")))
                .andExpect(jsonPath(DATA_STATUS_GET + ".description")
                        .value(containsString("HealthKit 권한이 살아 있는지")));
    }

    /**
     * <b>되돌릴 수 없는 API다.</b> 확인 다이얼로그가 클라이언트 몫이라는 것을 문서가 말하지
     * 않으면, 앱이 버튼 하나로 바로 호출하는 구현이 나온다.
     */
    @Test
    @DisplayName("삭제가 복구 불가이고 확인이 클라이언트 몫이라는 것이 문서에 있다")
    void 삭제_경고가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_DELETE + ".summary").value(containsString("MY-04")))
                .andExpect(jsonPath(ME_DELETE + ".description").value(containsString("복구 불가 영구 삭제")))
                .andExpect(jsonPath(ME_DELETE + ".description")
                        .value(containsString("2단계 확인 다이얼로그는 클라이언트 몫")))
                .andExpect(jsonPath(ME_DELETE + ".description").value(containsString("셀피를 다시 찍어")))
                .andExpect(jsonPath(ME_DELETE + ".responses.['200'].description")
                        .value(containsString("되돌릴 수 없다")));
    }

    @Test
    @DisplayName("새 API 셋 다 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_GET + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(ME_GET + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"))
                .andExpect(jsonPath(DATA_STATUS_GET + ".responses.['400'].content.['application/json'].examples.USER_ID_HEADER_INVALID.['$ref']")
                        .value("#/components/examples/USER_ID_HEADER_INVALID"))
                .andExpect(jsonPath(ME_DELETE + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

}
