package com.nhnacademy.processing.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String userIdStr = request.getHeader(USER_ID_HEADER);
        String userRole = request.getHeader(USER_ROLE_HEADER);

        if(userIdStr == null && userRole == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 헤더가 모두 누락되었습니다.");
        }

        if(userIdStr == null || userRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id 또는 X-User-Role 헤더가 누락되었습니다.");
        }

        try {
            long userId = Long.parseLong(userIdStr);
            if (userId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id는 양의 정수여야 합니다.");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id 형식이 올바르지 않습니다.");
        }

        if (!userRole.equals("ADMIN") && !userRole.equals("NORMAL")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 Role 값입니다.");
        }

        boolean hasRequireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (hasRequireAdmin && !userRole.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자(ADMIN) 권한이 필요합니다.");
        }

        return true;
    }
}
