package roombook.accounts.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.*;
import roombook.accounts.application.CurrentUser;
import roombook.kernel.Actor;

public class ActorArgumentResolver implements HandlerMethodArgumentResolver {
    @Override public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == Actor.class; }
    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest request, WebDataBinderFactory binder) {
        Object value = request.getAttribute(CurrentUserFilter.CURRENT_USER, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof CurrentUser user)) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        return new Actor(user.userId(), user.role());
    }
}
