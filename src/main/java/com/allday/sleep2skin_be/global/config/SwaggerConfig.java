package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.resolver.CurrentUserIdArgumentResolver;
import io.swagger.v3.oas.models.OpenAPI;
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
                                response.setContent(new Content().addMediaType(APPLICATION_JSON,
                                        new MediaType().schema(new Schema<>()
                                                .$ref(SCHEMA_REF_PREFIX + ERROR_WRAPPER_SCHEMA))));
                            }
                        });
                    }));
        };
    }

}
