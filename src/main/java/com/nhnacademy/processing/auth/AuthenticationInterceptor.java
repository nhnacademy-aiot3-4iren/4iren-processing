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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String userIdStr = request.getHeader(AuthHeaders.USER_ID);
        String userRoleStr = request.getHeader(AuthHeaders.USER_ROLE);

        // 헤더가 모두 누락된 경우: 401 Unauthorized
        if(userIdStr == null && userRoleStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 헤더가 모두 누락되었습니다.");
        }

        // 헤더가 일부 누락된 경우: 400 Bad Request
        if(userIdStr == null || userRoleStr == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id 또는 X-User-Role 헤더가 누락되었습니다.");
        }

        // X-User-Id 검증(양의 정수): 400 Bad Request
        long userId;
        try {
            userId = Long.parseLong(userIdStr);
            if (userId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id는 양의 정수여야 합니다.");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id 형식이 올바르지 않습니다.");
        }

        // X-User-Role 검증: 400 Bad Request
        Role role = Role.fromString(userRoleStr);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 Role 값입니다.");
        }

        AuthUser authUser = new AuthUser(userId, role);

        // 권한 체크: 403 Forbidden
        boolean hasRequireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (hasRequireAdmin && !authUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자(ADMIN) 권한이 필요합니다.");
        }

        request.setAttribute(AuthHeaders.AUTH_USER_ATTR, authUser);

        return true;
    }
}
