package com.allday.sleep2skin_be.global.config;

import com.allday.sleep2skin_be.global.resolver.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 웹 계층 공통 설정.
 *
 * <p>리졸버를 {@code @Component}로 두지 않고 여기서 직접 생성한다. 의존성이 없는 객체라
 * 빈으로 만들 이유가 없고, {@code @WebMvcTest} 슬라이스가 컴포넌트 스캔 범위에 따라
 * 리졸버를 못 찾는 상황도 생기지 않는다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

}
