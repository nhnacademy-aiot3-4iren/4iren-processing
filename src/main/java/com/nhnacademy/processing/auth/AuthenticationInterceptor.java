package com.nhnacademy.processing.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // 정적 리소스 요청은 그대로 통과
        }

        String userIdStr = request.getHeader(AuthHeaders.USER_ID);
        String userRoleStr = request.getHeader(AuthHeaders.USER_ROLE);

        // 헤더 누락: 401 Unauthorized
        if (userIdStr == null && userRoleStr == null) {
            return sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        // 헤더 일부 누락: 400 Bad Request
        if (userIdStr == null || userRoleStr == null) {
            return sendErrorResponse(response, HttpStatus.BAD_REQUEST, "X-User-Id 및 X-User-Role 헤더가 필요합니다.");
        }

        long userId;
        try {
            userId = Long.parseLong(userIdStr);
            if (userId <= 0) {
                return sendErrorResponse(response, HttpStatus.BAD_REQUEST, "유효하지 않은 X-User-Id 입니다.");
            }
        } catch (NumberFormatException e) {
            return sendErrorResponse(response, HttpStatus.BAD_REQUEST, "X-User-Id는 숫자여야 합니다.");
        }

        Role role;
        try {
            role = Role.from(userRoleStr);
        } catch (IllegalArgumentException e) {
            return sendErrorResponse(response, HttpStatus.BAD_REQUEST, "유효하지 않은 Role 입니다.");
        }

        AuthUser authUser = new AuthUser(userId, role);

        boolean hasRequireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (hasRequireAdmin && !authUser.isAdmin()) {
            return sendErrorResponse(response, HttpStatus.FORBIDDEN, "관리자(ADMIN/OWNER) 권한이 필요합니다.");
        }

        request.setAttribute(AuthHeaders.AUTH_USER_ATTR, authUser);

        // 정상적인 인증이 완료된 경우에만 최종적으로 true 반환
        return true;
    }

    // 예외를 던지는 대신 Response에 직접 에러를 작성하고 false를 반환하여 요청 처리 중단
    private boolean sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"message\":\"%s\"}", message));
        return false;
    }
}