package com.allday.sleep2skin_be.global.resolver;

import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUserId} 파라미터에 {@code X-User-Id} 헤더 값을 채운다.
 *
 * <p><b>서비스 전체에서 헤더를 읽는 유일한 자리다.</b> JWT를 도입하면 이 클래스만 바뀐다.
 *
 * <p><b>여기서 사용자 존재 여부를 확인하지 않는다.</b> 확인하려면 {@code UserRepository}가 필요한데
 * 그건 {@code domain} 패키지에 있고, 의존 방향은 {@code domain → global} 한쪽뿐이다(CLAUDE.md).
 * {@code global}이 {@code domain}을 참조하는 순간 그 규칙이 깨진다. 존재 검증은 각 Service가
 * {@code USER_NOT_FOUND}로 처리한다 — Service는 어차피 그 사용자의 엔티티를 읽어야 한다.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
        String rawUserId = webRequest.getHeader(USER_ID_HEADER);

        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException(ErrorCode.USER_ID_HEADER_INVALID,
                    USER_ID_HEADER + " 헤더가 없다");
        }

        try {
            return Long.parseLong(rawUserId.trim());
        } catch (NumberFormatException e) {
            // 상세 값은 로그에만 남는다. 사용자에게는 ErrorCode의 메시지만 나간다.
            throw new BusinessException(ErrorCode.USER_ID_HEADER_INVALID,
                    USER_ID_HEADER + " 헤더가 숫자가 아니다: " + rawUserId);
        }
    }

}
