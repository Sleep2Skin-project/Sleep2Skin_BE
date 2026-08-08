package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 도메인과 무관하게 <b>모든 API가 지켜야 하는</b> 문서 규칙.
 *
 * <p><b>경로를 짚지 않고 문서 전체를 순회한다.</b> 특정 경로를 하드코딩하면 API가 늘 때마다 이
 * 파일이 커지고, 도메인을 나눠 작업할 때 여러 사람이 같은 파일을 건드리게 된다(workflow.md §4).
 * 순회로 두면 새 도메인이 추가돼도 여기는 손댈 필요가 없고 자동으로 검증 대상이 된다.
 *
 * <p>도메인별 문서 검증(경로·설명·예시 선택)은 각 도메인의 {@code *ApiDocsTest}에 둔다.
 *
 * <p><b>문서가 어긋나도 서버 테스트는 전부 통과한다.</b> 문서는 프론트만 보기 때문에 앱이 잘못
 * 구현된 뒤에야 드러난다. 그래서 문서 자체를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerConfigTest {

    private static final String ERROR_WRAPPER_REF = "#/components/schemas/ErrorApiResponse";
    private static final String APPLICATION_JSON = "application/json";

    @Autowired
    private MockMvc mockMvc;

    /** Spring Boot 4가 등록하는 것은 Jackson 3({@code tools.jackson})의 ObjectMapper다. */
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("모든 실패 응답은 실패 전용 스키마를 가리킨다")
    void 모든_4xx_5xx가_실패_래퍼를_가리킨다() throws Exception {
        List<String> checked = new ArrayList<>();

        forEachResponse(apiDocs(), (operationId, statusCode, response) -> {
            if (!statusCode.startsWith("4") && !statusCode.startsWith("5")) {
                return;
            }
            // springdoc은 @ApiResponse에 content가 없으면 성공 타입으로 채운다. 그대로 두면
            // 404를 펼쳤을 때 data가 들어찬 성공 예시가 나온다.
            Map<String, Object> json = asMap(asMap(response.get("content")).get(APPLICATION_JSON));
            assertThat(asMap(json.get("schema")).get("$ref"))
                    .as("%s 의 %s 응답", operationId, statusCode)
                    .isEqualTo(ERROR_WRAPPER_REF);
            checked.add(operationId + " " + statusCode);
        });

        // 순회가 비어 있으면 위 단언은 한 번도 실행되지 않고 통과한다.
        assertThat(checked).as("검사한 실패 응답").isNotEmpty();
    }

    @Test
    @DisplayName("모든 응답의 미디어 타입이 application/json이다")
    void 모든_응답이_JSON이다() throws Exception {
        List<String> checked = new ArrayList<>();

        forEachResponse(apiDocs(), (operationId, statusCode, response) -> {
            if (!response.containsKey("content")) {
                return;
            }
            // produces 미선언 시 springdoc이 */* 로 문서화한다. 실제로는 전부 JSON이라
            // springdoc.default-produces-media-type 으로 기본값을 잡아뒀다.
            assertThat(asMap(response.get("content")).keySet())
                    .as("%s 의 %s 응답 미디어 타입", operationId, statusCode)
                    .containsExactly(APPLICATION_JSON);
            checked.add(operationId + " " + statusCode);
        });

        assertThat(checked).as("검사한 응답").isNotEmpty();
    }

    @Test
    @DisplayName("성공 래퍼 스키마에는 error가 없고 실패 래퍼에는 data가 없다")
    void 래퍼_스키마가_실제_응답과_같다() throws Exception {
        Map<String, Object> schemas = asMap(asMap(apiDocs().get("components")).get("schemas"));

        List<String> successWrappers = schemas.keySet().stream()
                .filter(name -> name.startsWith("ApiResponse"))
                .toList();

        // @JsonInclude(NON_NULL)은 런타임 직렬화만 바꾸고 springdoc은 읽지 않는다.
        assertThat(successWrappers).as("성공 래퍼 스키마").isNotEmpty();
        successWrappers.forEach(name ->
                assertThat(properties(schemas, name))
                        .as("%s 의 프로퍼티", name)
                        .contains("success", "data")
                        .doesNotContain("error"));

        assertThat(properties(schemas, "ErrorApiResponse")).containsExactly("success", "error");
    }

    @Test
    @DisplayName("에러 예시는 ErrorCode에서 생성된다")
    void 에러_예시가_ErrorCode에서_나온다() throws Exception {
        Map<String, Object> examples = asMap(asMap(apiDocs().get("components")).get("examples"));

        // 손으로 적으면 ErrorCode가 바뀌어도 문서만 조용히 틀린 채 남는다. 실제로 그런 적이 있다.
        assertThat(examples.keySet())
                .as("등록된 에러 예시")
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ErrorCode.values()).map(Enum::name).toList());

        for (ErrorCode errorCode : ErrorCode.values()) {
            Map<String, Object> value = asMap(asMap(examples.get(errorCode.name())).get("value"));
            Map<String, Object> error = asMap(value.get("error"));

            assertThat(value.get("success")).isEqualTo(false);
            assertThat(value).doesNotContainKey("data");
            assertThat(error.get("code")).isEqualTo(errorCode.name());
            assertThat(error.get("message")).isEqualTo(errorCode.getMessage());
        }
    }

    @Test
    @DisplayName("사용자와 무관한 API에는 X-User-Id 헤더가 붙지 않는다")
    void 헬스체크에는_헤더가_붙지_않는다() throws Exception {
        Map<String, Object> paths = asMap(apiDocs().get("paths"));

        // 없는 것을 단언하는 검사는 경로가 틀려도 통과한다. 경로가 잡히는지 먼저 확인한다.
        assertThat(paths).as("문서의 경로 목록").containsKey("/api/v1/health");

        Map<String, Object> health = asMap(asMap(paths.get("/api/v1/health")).get("get"));
        assertThat(health).as("헬스체크 오퍼레이션").doesNotContainKey("parameters");
    }

    private Map<String, Object> apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return asMap(objectMapper.readValue(body, Map.class));
    }

    /** 문서에 있는 모든 오퍼레이션의 모든 응답을 훑는다. */
    private void forEachResponse(Map<String, Object> apiDocs, ResponseVisitor visitor) {
        asMap(apiDocs.get("paths")).forEach((path, pathItem) ->
                asMap(pathItem).forEach((method, operation) -> {
                    Map<String, Object> responses = asMap(asMap(operation).get("responses"));
                    if (responses == null) {
                        return;
                    }
                    String operationId = method.toUpperCase() + " " + path;
                    responses.forEach((statusCode, response) ->
                            visitor.visit(operationId, statusCode, asMap(response)));
                }));
    }

    private List<String> properties(Map<String, Object> schemas, String schemaName) {
        return List.copyOf(asMap(asMap(schemas.get(schemaName)).get("properties")).keySet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }

    @FunctionalInterface
    private interface ResponseVisitor {
        void visit(String operationId, String statusCode, Map<String, Object> response);
    }

}
