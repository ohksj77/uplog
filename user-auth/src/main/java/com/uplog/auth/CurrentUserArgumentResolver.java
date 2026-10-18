package com.uplog.auth;

import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 컨트롤러 파라미터 {@code CurrentUser}(로그인 필수)와 {@code Optional<CurrentUser>}를 채운다. */
final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.nestedIfOptional().getNestedParameterType() == CurrentUser.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Optional<CurrentUser> user = CurrentUser.fromContext();
        if (parameter.getParameterType() == Optional.class) {
            return user;
        }
        return user.orElseThrow(UnauthenticatedException::new);
    }
}
