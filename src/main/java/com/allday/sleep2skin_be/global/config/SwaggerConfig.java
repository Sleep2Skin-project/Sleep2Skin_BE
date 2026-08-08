package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.resolver.CurrentUserIdArgumentResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerConfig {

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

}
