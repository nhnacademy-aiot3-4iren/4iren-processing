package com.nhnacademy.processing.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthUserArgumentResolverTest {

    @Mock private MethodParameter parameter;
    @Mock private NativeWebRequest webRequest;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private AuthUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(webRequest.getNativeRequest()).thenReturn(request);
    }

    @Test
    @DisplayName("@LoginUser 어노테이션과 AuthUser 타입이 맞으면 true를 반환한다")
    void supportsParameter_ValidParameter_ReturnsTrue() {
        when(parameter.hasParameterAnnotation(LoginUser.class)).thenReturn(true);
        doReturn(AuthUser.class).when(parameter).getParameterType();

        boolean result = resolver.supportsParameter(parameter);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("어노테이션이 없거나 타입이 다르면 false를 반환한다")
    void supportsParameter_InvalidParameter_ReturnsFalse() {
        // 어노테이션만 있는 경우
        when(parameter.hasParameterAnnotation(LoginUser.class)).thenReturn(true);
        doReturn(String.class).when(parameter).getParameterType();
        assertThat(resolver.supportsParameter(parameter)).isFalse();

        // 타입만 맞는 경우
        when(parameter.hasParameterAnnotation(LoginUser.class)).thenReturn(false);
        doReturn(AuthUser.class).when(parameter).getParameterType();
        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("요청 속성에 AuthUser가 존재하면 정상적으로 반환한다")
    void resolveArgument_AuthUserExists_ReturnsAuthUser() {
        AuthUser mockUser = new AuthUser(1L, Role.NORMAL);
        when(request.getAttribute(AuthHeaders.AUTH_USER_ATTR)).thenReturn(mockUser);

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(AuthUser.class);
        assertThat(((AuthUser) result).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("요청 속성에 AuthUser가 없거나 타입이 다르면 ResponseStatusException을 던진다")
    void resolveArgument_AuthUserMissing_ThrowsException() {
        when(request.getAttribute(AuthHeaders.AUTH_USER_ATTR)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("인증 정보가 존재하지 않습니다.");
    }
}