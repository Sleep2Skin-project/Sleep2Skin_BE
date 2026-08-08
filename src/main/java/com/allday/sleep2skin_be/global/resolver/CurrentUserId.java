package com.allday.sleep2skin_be.global.resolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청을 보낸 사용자의 식별자를 주입받는다.
 *
 * <pre>
 * public ApiResponse&lt;Xxx&gt; get(&#64;CurrentUserId Long userId) { ... }
 * </pre>
 *
 * <p><b>컨트롤러가 {@code X-User-Id} 헤더를 직접 읽지 않게 하려고 둔 것이다.</b>
 * 지금은 인증이 없어 클라이언트가 헤더로 알려주지만, 나중에 JWT를 붙이면
 * {@link CurrentUserIdArgumentResolver} 안쪽만 토큰에서 꺼내도록 바뀌고
 * 컨트롤러 시그니처는 전부 그대로다(conventions.md §8).
 *
 * <p>타입은 {@code Long}이어야 한다. 다른 타입에 붙이면 리졸버가 처리하지 않아
 * Spring이 다른 방식으로 바인딩을 시도한다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
