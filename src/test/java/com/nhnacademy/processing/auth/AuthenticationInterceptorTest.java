package com.nhnacademy.processing.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationInterceptorTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HandlerMethod handlerMethod;
    @Mock private PrintWriter printWriter;

    @InjectMocks
    private AuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    @DisplayName("HandlerMethod가 아닌 요청(정적 리소스 등)은 true를 반환한다")
    void preHandle_NotHandlerMethod_ReturnsTrue() throws Exception {
        Object staticResourceHandler = new Object();
        boolean result = interceptor.preHandle(request, response, staticResourceHandler);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("인증 헤더가 모두 없으면 401 UNAUTHORIZED를 반환한다")
    void preHandle_MissingAllHeaders_ReturnsFalseAnd401() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn(null);
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(printWriter).write(contains("인증 정보가 없습니다."));
    }

    @Test
    @DisplayName("인증 헤더 중 하나만 누락되면 400 BAD_REQUEST를 반환한다")
    void preHandle_MissingOneHeader_ReturnsFalseAnd400() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("1");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verify(printWriter).write(contains("X-User-Id 및 X-User-Role 헤더가 필요합니다."));
    }

    @Test
    @DisplayName("X-User-Id가 숫자가 아니면 400 BAD_REQUEST를 반환한다")
    void preHandle_InvalidUserIdFormat_ReturnsFalseAnd400() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("invalid");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("NORMAL");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verify(printWriter).write(contains("X-User-Id는 숫자여야 합니다."));
    }

    @Test
    @DisplayName("X-User-Id가 0 이하이면 400 BAD_REQUEST를 반환한다")
    void preHandle_NegativeUserId_ReturnsFalseAnd400() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("0");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("NORMAL");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verify(printWriter).write(contains("유효하지 않은 X-User-Id 입니다."));
    }

    @Test
    @DisplayName("유효하지 않은 Role이면 400 BAD_REQUEST를 반환한다")
    void preHandle_InvalidRole_ReturnsFalseAnd400() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("1");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("UNKNOWN_ROLE");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verify(printWriter).write(contains("유효하지 않은 Role 입니다."));
    }

    @Test
    @DisplayName("@RequireAdmin이 필요한데 NORMAL 권한이면 403 FORBIDDEN을 반환한다")
    void preHandle_RequireAdminButNormalRole_ReturnsFalseAnd403() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("1");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("NORMAL");

        when(handlerMethod.hasMethodAnnotation(RequireAdmin.class)).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
        verify(printWriter).write(contains("관리자(ADMIN/OWNER) 권한이 필요합니다."));
    }

    @Test
    @DisplayName("@RequireAdmin이 필요하고 ADMIN 권한이면 true를 반환하고 속성을 세팅한다")
    void preHandle_RequireAdminAndAdminRole_ReturnsTrue() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("1");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("ADMIN");

        when(handlerMethod.hasMethodAnnotation(RequireAdmin.class)).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        verify(request).setAttribute(eq(AuthHeaders.AUTH_USER_ATTR), any(AuthUser.class));
    }

    @Test
    @DisplayName("권한 체크가 필요 없는 엔드포인트는 정상 사용자의 경우 true를 반환한다")
    void preHandle_ValidUser_ReturnsTrue() throws Exception {
        when(request.getHeader(AuthHeaders.USER_ID)).thenReturn("2");
        when(request.getHeader(AuthHeaders.USER_ROLE)).thenReturn("NORMAL");

        when(handlerMethod.hasMethodAnnotation(RequireAdmin.class)).thenReturn(false);
        doReturn(Object.class).when(handlerMethod).getBeanType();

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        verify(request).setAttribute(eq(AuthHeaders.AUTH_USER_ATTR), any(AuthUser.class));
    }
}