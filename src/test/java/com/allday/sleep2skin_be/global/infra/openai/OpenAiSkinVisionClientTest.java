package com.allday.sleep2skin_be.global.infra.openai;

import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 를 실제로 호출하지 않고 배선과 파싱을 검증한다. 실호출은 비용이 들고 값이 매번 다르다.
 *
 * <p><b>여기서 잡지 못하는 것이 하나 있다 — 점수 방향이다.</b> 프롬프트가 실제로 모델을 어느
 * 방향으로 움직이는지는 스텁이 알려주지 않는다. 프롬프트를 고쳤다면 눈 밑이 뚜렷하게 어두운
 * 샘플로 <b>실호출 회귀 확인</b>을 한 번 한다(architecture.md §7).
 */
class OpenAiSkinVisionClientTest {

    private static final String BASE_URL = "https://api.openai.test";
    private static final byte[] IMAGE = "가짜-셀피-바이트".getBytes(StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private OpenAiSkinVisionClient client;

    @BeforeEach
    void setUp() {
        client = clientWith(properties("sk-test-key"));
    }

    private OpenAiSkinVisionClient clientWith(OpenAiProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiSkinVisionClient(builder.build(), properties, new ObjectMapper());
    }

    private OpenAiProperties properties(String apiKey) {
        return new OpenAiProperties(apiKey, BASE_URL, "gpt-5.6-terra",
                Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    /** Responses API 성공 응답. {@code output} 배열에 메시지가 아닌 항목이 섞인 실제 모양이다. */
    private String responseWith(int darkCircle, int complexion, int barrier) {
        return responseWith(darkCircle, complexion, barrier, false, false, false);
    }

    private String responseWith(int darkCircle, int complexion, int barrier,
                                boolean pigmentationDetected, boolean acneScarDetected,
                                boolean agingDetected) {
        return """
                {
                  "id": "resp_1",
                  "status": "completed",
                  "output": [
                    { "type": "reasoning", "id": "rs_1", "summary": [] },
                    { "type": "message", "id": "msg_1", "role": "assistant",
                      "content": [
                        { "type": "output_text",
                          "text": "{\\"darkCircle\\":%d,\\"complexion\\":%d,\\"barrier\\":%d,\\"pigmentationDetected\\":%b,\\"acneScarDetected\\":%b,\\"agingDetected\\":%b}" }
                      ] }
                  ]
                }
                """.formatted(darkCircle, complexion, barrier,
                pigmentationDetected, acneScarDetected, agingDetected);
    }

    @Nested
    @DisplayName("요청")
    class Request {

        @Test
        @DisplayName("이미지를 base64 data URL 로 본문에 싣는다 — 업로드할 URL 이 없기 때문이다")
        void 이미지를_본문에_인라인한다() {
            String expected = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(IMAGE);

            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer sk-test-key"))
                    .andExpect(jsonPath("$.model").value("gpt-5.6-terra"))
                    .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
                    .andExpect(jsonPath("$.input[0].content[1].image_url").value(expected))
                    .andRespond(withSuccess(responseWith(61, 55, 78), MediaType.APPLICATION_JSON));

            client.analyze(IMAGE, "image/jpeg");
            server.verify();
        }

        @Test
        @DisplayName("Structured Outputs 를 strict 로 강제한다 — 자유 텍스트를 긁지 않는다")
        void 스키마를_강제한다() {
            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                    .andExpect(jsonPath("$.text.format.strict").value(true))
                    .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                    .andExpect(jsonPath("$.text.format.schema.required.length()").value(6))
                    .andRespond(withSuccess(responseWith(61, 55, 78), MediaType.APPLICATION_JSON));

            client.analyze(IMAGE, "image/jpeg");
            server.verify();
        }

        @Test
        @DisplayName("API 키가 없으면 호출조차 하지 않는다 — 실패할 요청에 돈을 쓰지 않는다")
        void 키가_없으면_호출하지_않는다() {
            OpenAiSkinVisionClient keyless = clientWith(properties(""));

            assertThatThrownBy(() -> keyless.analyze(IMAGE, "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_ANALYSIS_FAILED);

            server.verify();   // 기대한 요청이 없고 실제 요청도 없어야 통과한다
        }
    }

    @Nested
    @DisplayName("응답 해석")
    class ParseResponse {

        @Test
        @DisplayName("output 배열에 다른 항목이 섞여 있어도 output_text 를 찾아낸다")
        void 지표_3종을_파싱한다() {
            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andRespond(withSuccess(responseWith(61, 55, 78), MediaType.APPLICATION_JSON));

            assertThat(client.analyze(IMAGE, "image/jpeg"))
                    .isEqualTo(new SkinVisionScores(61, 55, 78, false, false, false));
        }

        @Test
        @DisplayName("클리닉 트리아지 플래그 3종도 함께 파싱한다")
        void 트리아지_플래그를_파싱한다() {
            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andRespond(withSuccess(
                            responseWith(61, 55, 78, true, false, true), MediaType.APPLICATION_JSON));

            assertThat(client.analyze(IMAGE, "image/jpeg"))
                    .isEqualTo(new SkinVisionScores(61, 55, 78, true, false, true));
        }

        @Test
        @DisplayName("점수가 0~100 을 벗어나면 자르지 않고 실패한다 — 모델이 다른 척도로 답했다는 신호다")
        void 범위를_벗어나면_실패한다() {
            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andRespond(withSuccess(responseWith(61, 550, 78), MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.analyze(IMAGE, "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_ANALYSIS_FAILED);
        }

        @Test
        @DisplayName("모델이 결과를 내지 않으면(거절·중단) 실패한다")
        void output_text가_없으면_실패한다() {
            String refused = """
                    { "id": "resp_1", "status": "incomplete",
                      "incomplete_details": { "reason": "content_filter" },
                      "output": [ { "type": "reasoning", "id": "rs_1", "summary": [] } ] }
                    """;
            server.expect(requestTo(BASE_URL + "/v1/responses"))
                    .andRespond(withSuccess(refused, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.analyze(IMAGE, "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_ANALYSIS_FAILED);
        }

        @Test
        @DisplayName("OpenAI 가 5xx 를 주면 502 로 바꿔 내보낸다 — 우리 서버 오류가 아니다")
        void 외부_오류는_502가_된다() {
            server.expect(requestTo(BASE_URL + "/v1/responses")).andRespond(withServerError());

            assertThatThrownBy(() -> client.analyze(IMAGE, "image/jpeg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .satisfies(code -> assertThat(((ErrorCode) code).getStatus())
                            .isEqualTo(HttpStatus.BAD_GATEWAY));
        }
    }

    @Nested
    @DisplayName("프롬프트 — 점수 방향 (유일한 방어선)")
    class PromptDirection {

        /**
         * <b>이 테스트가 지키는 것은 값이 아니라 문장이다.</b> {@code darkCircle} 은 이름만 보면
         * "다크서클이 심한 정도"로 읽혀 모델이 방향을 뒤집기 쉬운데, 뒤집혀도 값은 0~100 정수라
         * {@code CHECK} 제약도 스키마도 걸러내지 못한다 — 적중률만 조용히 무너진다.
         *
         * <p>설명에서 양 끝 문장이 사라지면 여기서 걸린다.
         */
        @Test
        @DisplayName("세 지표 모두 0 과 100 이 무엇인지 문장으로 적혀 있다")
        void 양_끝을_문장으로_말한다() {
            @SuppressWarnings("unchecked")
            var properties = (java.util.Map<String, Object>) SkinVisionPrompt.schema().get("properties");

            for (String field : java.util.List.of("darkCircle", "complexion", "barrier")) {
                @SuppressWarnings("unchecked")
                var definition = (java.util.Map<String, Object>) properties.get(field);
                String description = (String) definition.get("description");

                assertThat(description)
                        .as("%s 의 설명에 0 과 100 의 의미가 모두 있어야 한다", field)
                        .contains("0 =")
                        .contains("100 =");
            }
        }

        @Test
        @DisplayName("다크서클은 '회복된 정도'라고 못 박는다 — 높을수록 다크서클이 없다")
        void 다크서클_방향을_뒤집지_못하게_한다() {
            @SuppressWarnings("unchecked")
            var properties = (java.util.Map<String, Object>) SkinVisionPrompt.schema().get("properties");
            @SuppressWarnings("unchecked")
            var darkCircle = (java.util.Map<String, Object>) properties.get("darkCircle");

            assertThat((String) darkCircle.get("description"))
                    .containsIgnoringCase("RECOVERY")
                    .contains("100 = the under-eye area looks clear");
        }

        @Test
        @DisplayName("strict 모드가 지원하지 않는 minimum·maximum 을 넣지 않는다 — 넣으면 요청이 400 이다")
        void 범위_키워드를_넣지_않는다() {
            @SuppressWarnings("unchecked")
            var properties = (java.util.Map<String, Object>) SkinVisionPrompt.schema().get("properties");

            properties.values().forEach(definition -> {
                @SuppressWarnings("unchecked")
                var field = (java.util.Map<String, Object>) definition;
                assertThat(field).doesNotContainKeys("minimum", "maximum");
            });
        }
    }

}
