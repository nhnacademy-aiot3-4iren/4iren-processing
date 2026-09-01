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

        // 헤더 누락: 401 Unauthorized
        if (userIdStr == null && userRoleStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 누락되었습니다.");
        }

        // 부분 누락: 400 Bad Request
        if (userIdStr == null || userRoleStr == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id 또는 X-User-Role 헤더가 누락되었습니다.");
        }

        // X-User-Id 파싱: 400 Bad Request
        long userId;
        try {
            userId = Long.parseLong(userIdStr);
            if (userId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 X-User-Id 입니다.");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id는 숫자여야 합니다.");
        }

        // X-User-Role 파싱: 400 Bad Request
        Role role;
        try {
            role = Role.from(userRoleStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 Role 값입니다.");
        }

        AuthUser authUser = new AuthUser(userId, role);

        // @RequireAdmin 권한 검사 (ADMIN 또는 OWNER 허용)
        boolean hasRequireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (hasRequireAdmin && !authUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자(ADMIN/OWNER) 권한이 필요합니다.");
        }

        request.setAttribute(AuthHeaders.AUTH_USER_ATTR, authUser);
        return true;
    }
}