package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.resolver.CurrentUserIdArgumentResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class SwaggerConfig {

    /** 실패 응답 래퍼의 스키마 이름. 런타임 타입이 아니라 문서용으로 직접 만든다. */
    private static final String ERROR_WRAPPER_SCHEMA = "ErrorApiResponse";

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final String APPLICATION_JSON = "application/json";

    static {
        // @CurrentUserId는 헤더에서 채워지는데 springdoc은 그걸 모른다. 무시시키지 않으면
        // userId가 필수 쿼리 파라미터로 문서에 나와 프론트가 잘못된 요청을 만든다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("sleep2skin API")
                        .description("sleep2skin 백엔드 API 문서")
                        .version("v0.0.1"));
    }

    /**
     * {@link CurrentUserId}를 쓰는 API에 {@code X-User-Id} 헤더 파라미터를 붙인다.
     *
     * <p>모든 오퍼레이션에 일괄로 붙이지 않는 이유는 헬스체크처럼 사용자와 무관한 API가 있기 때문이다.
     * 파라미터를 보고 판단하므로 API가 늘어도 여기를 고칠 일이 없다.
     */
    @Bean
    public OperationCustomizer currentUserIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean usesCurrentUserId = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentUserId.class));

            if (usesCurrentUserId) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(CurrentUserIdArgumentResolver.USER_ID_HEADER)
                        .description("사용자 식별자. 인증이 없어 클라이언트가 직접 지정한다.")
                        .required(true)
                        .example("1")
                        .schema(new IntegerSchema().format("int64")));
            }
            return operation;
        };
    }

    /**
     * 문서에 나오는 응답 모양을 실제로 나가는 응답에 맞춘다.
     *
     * <p>고치는 것은 두 가지다.
     *
     * <p><b>1. 실패 응답이 성공 스키마를 가리킨다.</b> springdoc은 {@code @ApiResponse}에
     * {@code content}가 없으면 <b>선언된 모든 상태 코드를 메서드 반환 타입으로 채운다.</b>
     * 그래서 400·404를 펼치면 {@code data}가 채워진 성공 예시가 나오는데, 실제 실패 응답에는
     * {@code data} 키가 아예 없다. <b>앱 팀이 이걸 보고 에러 처리를 짜면 틀린 모양을 기준으로 짠다.</b>
     * 컨트롤러마다 {@code content}를 적는 대신 여기서 4xx·5xx를 한 번에 바꾼다.
     *
     * <p><b>2. 성공 스키마에 {@code error}가 남아 있다.</b> {@code @JsonInclude(NON_NULL)}은
     * 런타임 직렬화만 바꾸고 springdoc은 그걸 읽지 않는다. 타입을 보고 만들기 때문에
     * 실제로는 나가지 않는 필드가 예시에 뜬다.
     *
     * <p>{@code OperationCustomizer}가 아니라 {@code OpenApiCustomizer}인 이유는 두 번째 작업이
     * 오퍼레이션이 아니라 {@code components.schemas}를 건드려야 하기 때문이다.
     */
    @Bean
    public OpenApiCustomizer responseShapeCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(ERROR_WRAPPER_SCHEMA, new ObjectSchema()
                    .description("실패 응답 래퍼")
                    .addProperty("success", new BooleanSchema()
                            .description("항상 false")
                            .example(false))
                    .addProperty("error", new Schema<>()
                            .$ref(SCHEMA_REF_PREFIX + "ErrorResponse")));

            registerErrorExamples(openApi);

            openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
                if (schemaName.startsWith("ApiResponse") && schema.getProperties() != null) {
                    schema.getProperties().remove("error");
                }
            });

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }
                        operation.getResponses().forEach((statusCode, response) -> {
                            if (statusCode.startsWith("4") || statusCode.startsWith("5")) {
                                applyErrorSchema(response);
                            }
                        });
                    }));
        };
    }

    /**
     * {@link ErrorCode} 하나마다 실패 응답 예시를 만들어 {@code components.examples}에 등록한다.
     * 문서에서는 {@code #/components/examples/{코드 이름}}으로 참조한다.
     *
     * <p><b>예시를 손으로 적지 않기 위한 것이다.</b> 전에는 {@code ErrorResponse}에 예시가 하드코딩돼
     * 있어서 <b>모든 API가 같은 예시를 보여줬고, 그나마도 실제로는 나갈 수 없는 문구였다</b> —
     * 문서에 적힌 메시지와 enum의 메시지가 서로 달랐다. 손으로 옮겨 적는 한 언제든 다시 어긋난다.
     *
     * <p>여기서 만들면 코드·메시지·상태 코드가 전부 enum에서 나오므로 어긋날 수가 없고,
     * {@code ErrorCode}에 새 값을 추가하면 예시도 저절로 생긴다. 각 API는 <b>어떤 코드가 나오는지만</b>
     * 고르면 된다.
     */
    private void registerErrorExamples(OpenAPI openApi) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", errorCode.name());
            error.put("message", errorCode.getMessage());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", error);

            openApi.getComponents().addExamples(errorCode.name(), new Example()
                    .summary(errorCode.getStatus().value() + " " + errorCode.name())
                    .description(errorCode.getMessage())
                    .value(body));
        }
    }

    /**
     * 실패 응답이 실패 래퍼 스키마를 가리키게 한다.
     *
     * <p><b>이미 있는 내용을 덮어쓰지 않는다.</b> 스펙이 {@code @Content}로 예시를 직접 지정했다면
     * 그건 "이 API에서 어떤 코드가 나오는가"라는, 여기서는 알 수 없는 정보다. 스키마만 바로잡고
     * 예시는 그대로 둔다.
     */
    private void applyErrorSchema(io.swagger.v3.oas.models.responses.ApiResponse response) {
        Content content = response.getContent();

        if (content == null || content.isEmpty()) {
            response.setContent(new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(errorWrapperRef())));
            return;
        }

        content.values().forEach(mediaType -> mediaType.setSchema(errorWrapperRef()));
    }

    private Schema<?> errorWrapperRef() {
        return new Schema<>().$ref(SCHEMA_REF_PREFIX + ERROR_WRAPPER_SCHEMA);
    }

}
