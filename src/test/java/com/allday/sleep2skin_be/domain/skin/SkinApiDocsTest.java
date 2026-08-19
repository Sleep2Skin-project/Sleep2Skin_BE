package com.allday.sleep2skin_be.domain.skin;

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
 * {@code skin} 도메인의 API 문서 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkinApiDocsTest {

    private static final String FORECAST_GET = "$.paths.['/api/v1/skin/forecast'].get";
    private static final String SELFIE_POST = "$.paths.['/api/v1/skin/selfie'].post";
    private static final String SUMMARY_GET = "$.paths.['/api/v1/skin/verification/summary'].get";
    private static final String MODEL_GET = "$.paths.['/api/v1/skin/model'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ControllerSpec 인터페이스에 붙인 설명이 문서에 실린다")
    void 인터페이스의_문서가_반영된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".summary").value(containsString("피부 예보 조회")))
                .andExpect(jsonPath(FORECAST_GET + ".summary").value(containsString("HOME-03")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Skin')].description")
                        .value(hasItem("피부 예보 · 검증 · 개인 모델 API")));
    }

    /**
     * 빈 상태를 4xx로 오해하면 앱이 신규 사용자에게 에러 화면을 띄운다. <b>가장 흔한 오독</b>이라
     * 문서에서 사라지지 않게 붙들어 둔다.
     */
    @Test
    @DisplayName("빈 상태가 200이라는 것과 status로 분기하라는 안내가 문서에 있다")
    void 빈_상태_규약이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("NO_SLEEP_DATA")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("404가 아니다")))
                .andExpect(jsonPath(FORECAST_GET + ".responses.['200'].description")
                        .value(containsString("예보가 없는 경우도")));
    }

    /**
     * {@code DARK_CIRCLE}의 방향이 뒤집히면 값 범위는 정상이라 어떤 검증에도 안 걸리고
     * 화면 문구만 정반대로 나간다. 프론트가 읽는 유일한 방어선이 이 문서다.
     */
    @Test
    @DisplayName("지표 방향과 등급 구간이 문서에 있다")
    void 지표_방향이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("회복된 정도")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("높을수록 좋은 상태")))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("STABLE")));
    }

    /**
     * <b>인덱스가 아니라 이름으로 찾는다.</b> springdoc이 매기는 파라미터 순서는 보장되지 않아
     * (여기서는 쿼리 파라미터가 헤더보다 앞에 온다) 인덱스로 단언하면 순서만 바뀌어도 깨진다.
     */
    @Test
    @DisplayName("X-User-Id 헤더와 baseDate 쿼리 파라미터가 둘 다 필수로 문서에 붙는다")
    void 기준일_파라미터가_문서에_붙는다() throws Exception {
        String userIdParameter = FORECAST_GET + ".parameters[?(@.name == 'X-User-Id')]";
        String baseDateParameter = FORECAST_GET + ".parameters[?(@.name == 'baseDate')]";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(userIdParameter + ".in").value(hasItem("header")))
                .andExpect(jsonPath(userIdParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(baseDateParameter + ".in").value(hasItem("query")))
                .andExpect(jsonPath(baseDateParameter + ".required").value(hasItem(true)))
                .andExpect(jsonPath(FORECAST_GET + ".description").value(containsString("하루 밀린다")));
    }

    @Test
    @DisplayName("상태 코드마다 그 상황에 맞는 에러 예시가 붙는다")
    void 에러_예시가_상황별로_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".responses.['400'].content.['application/json'].examples.INVALID_INPUT.['$ref']")
                        .value("#/components/examples/INVALID_INPUT"))
                .andExpect(jsonPath(FORECAST_GET + ".responses.['404'].content.['application/json'].examples.USER_NOT_FOUND.['$ref']")
                        .value("#/components/examples/USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("성공 응답이 조회 응답 스키마를 가리킨다")
    void 성공_스키마가_연결돼_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FORECAST_GET + ".responses.['200'].content.['application/json'].schema.['$ref']")
                        .value("#/components/schemas/ApiResponseSkinForecastQueryResponse"));
    }

    /**
     * 셀피 API의 문서가 프론트에게 특히 중요한 이유는 <b>여기만 규칙이 반대</b>이기 때문이다 —
     * 다른 곳에서 200 + 빈 상태인 상황이 여기서는 4xx다.
     */
    @Test
    @DisplayName("셀피 API의 예보 부재가 404라는 것과 그 이유가 문서에 있다")
    void 셀피는_예보_부재가_404다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SELFIE_POST + ".summary").value(containsString("셀피 분석·검증")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("SKIN_FORECAST_NOT_FOUND")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("동작 자체가 성립하지 않는다")))
                .andExpect(jsonPath(SELFIE_POST + ".responses.['404'].content.['application/json'].examples.SKIN_FORECAST_NOT_FOUND.['$ref']")
                        .value("#/components/examples/SKIN_FORECAST_NOT_FOUND"))
                .andExpect(jsonPath(SELFIE_POST + ".responses.['409'].content.['application/json'].examples.VERIFICATION_ALREADY_DONE.['$ref']")
                        .value("#/components/examples/VERIFICATION_ALREADY_DONE"));
    }

    /**
     * <b>분모를 3으로 읽으면 앱이 서버와 다른 적중률을 표시한다.</b> 빈 지표가 있는 날에만
     * 어긋나므로 신규 사용자에게서만 드러나고, 값 범위는 정상이라 알아채기 어렵다.
     */
    @Test
    @DisplayName("적중률 분모와 skipped에도 실측이 실린다는 것이 문서에 있다")
    void 적중률_분모가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("분모는 `verifications`의 길이이지 3이 아니다")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("실측은 항상 3종이 나온다")));
    }

    /**
     * 점수 축과 위험 축이 반대라 <b>문구에서 뒤집히기 가장 쉬운 자리</b>다. 프론트가 읽는 유일한
     * 방어선이 이 문서다.
     */
    @Test
    @DisplayName("과소·과대가 점수 축 기준이라는 설명이 문서에 있다")
    void 판정_방향이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("예보 − 실측")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("피부 위험을 과대평가")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("점수 축과 위험 축이 반대다")));
    }

    /**
     * 고지 문구는 <b>주어를 생략하지 않는다</b>(prd.md §2). "저장하지 않습니다"는 주어가 없어
     * "아무도 저장하지 않는다"로 읽히는데, 제공자 쪽 보관 여부는 우리가 모른다.
     */
    @Test
    @DisplayName("셀피 취급 고지가 주어를 붙인 형태로 문서에 있다")
    void 셀피_고지가_주어를_붙인다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("외부 AI(OpenAI)로 이미지가 전송됩니다")))
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(containsString("**서버에** 저장하지 않으며")))
                // "어디에도 저장되지 않습니다"는 확인되지 않은 주장이라 쓸 수 없다
                .andExpect(jsonPath(SELFIE_POST + ".description")
                        .value(not(containsString("어디에도 저장되지"))));
    }

    /**
     * <b>앱이 두 숫자를 바꿔 쓰면 화면이 다른 뜻을 말한다.</b> 최상위는 "예보가 얼마나 믿을
     * 만한가", {@code latest}는 "어제 예보가 얼마나 맞았나"다. 문서가 이 구분을 잃으면 프론트가
     * 알아낼 방법이 없다.
     */
    @Test
    @DisplayName("배너의 적중률이 누적이라는 것과 latest와 다르다는 것이 문서에 있다")
    void 누적_적중률_구분이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SUMMARY_GET + ".summary").value(containsString("HOME-09")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("`hitRate`는 누적이다")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("latest.hitRate")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("검증 일수 × 3이 아니다")));
    }

    /**
     * 상승폭을 서버가 담지 않기로 한 것과 {@code previous}가 {@code null}일 수 있다는 것이
     * 문서에서 사라지면, <b>앱이 첫 검증에서 없던 상승폭을 그리거나</b> 있지도 않은 델타 필드를
     * 기다린다.
     */
    @Test
    @DisplayName("previous가 기준선이고 상승폭은 앱이 계산한다는 것이 문서에 있다")
    void 직전_검증_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("상승폭은 서버가 내려주지 않는다")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("전날이 아니라 바로 앞 검증일이다")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("검증이 1건뿐이면 `previous`는 `null`이다")));
    }

    /**
     * 연속 규칙이 문서에서 사라지면 앱이 "오늘 안 했으니 0"으로 표시하게 된다 —
     * <b>아직 하지 않은 일로 사용자를 벌주는 것</b>처럼 읽힌다.
     */
    @Test
    @DisplayName("오늘 미검증이 연속을 끊지 않는다는 규칙이 문서에 있다")
    void 연속_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("끊기지 않는다")))
                .andExpect(jsonPath(SUMMARY_GET + ".description")
                        .value(containsString("연속이 하루 밀린다")))
                .andExpect(jsonPath(SUMMARY_GET + ".parameters[?(@.name == 'baseDate')].required")
                        .value(hasItem(true)));
    }

    /**
     * 재정규화 때문에 지표를 넘은 비교는 성립하지 않는다. 이 경고가 사라지면 앱이 다크서클의
     * {@code 1.23}과 장벽의 {@code 1.8}을 나란히 놓고 "장벽이 더 민감"이라고 말하게 된다.
     */
    @Test
    @DisplayName("비율이 같은 지표 안에서만 의미를 갖는다는 것이 문서에 있다")
    void 비율의_범위가_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MODEL_GET + ".summary").value(containsString("REP-12")))
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("같은 지표 안에서만 의미를 갖는다")))
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("다른 지표의 숫자와 비교하지 말 것")))
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("오류가 아니다")));
    }

    /**
     * 등급 컷오프를 서버에 두면 바꿀 때마다 배포해야 한다(L8 해결). 문서가 이걸 말하지 않으면
     * 앱이 서버에 등급을 요구하게 된다.
     */
    @Test
    @DisplayName("신뢰도 등급을 클라이언트가 만든다는 것과 baseDate가 없다는 것이 문서에 있다")
    void 등급과_파라미터_규칙이_문서에_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("등급은 클라이언트가 만든다")))
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("바꿀 때마다 배포")))
                .andExpect(jsonPath(MODEL_GET + ".description")
                        .value(containsString("`baseDate`를 받지 않는다")))
                // 날짜를 받는 다른 조회 API와 달리 파라미터가 헤더 하나뿐이다
                .andExpect(jsonPath(MODEL_GET + ".parameters[?(@.name == 'baseDate')]").isEmpty());
    }

}
