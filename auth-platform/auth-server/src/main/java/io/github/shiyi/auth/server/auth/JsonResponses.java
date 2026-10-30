package io.github.shiyi.auth.server.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;

/**
 * Small helper for writing {@link ApiResult} JSON in servlet handlers.
 */
final public class JsonResponses {

    private JsonResponses() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper,
                      HttpStatus status, ApiResult<?> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * 401 in the platform envelope — for filter chains that must not fall back
     * to the servlet container's HTML error page.
     */
    public static AuthenticationEntryPoint unauthenticated(ObjectMapper objectMapper) {
        return (request, response, authException) -> write(response, objectMapper,
                HttpStatus.UNAUTHORIZED, ApiResult.fail(ErrorCode.UNAUTHENTICATED));
    }

    /**
     * 403 in the platform envelope. A denial raised by the filter chain
     * (URL-level rule) never reaches {@code GlobalExceptionHandler}, which only
     * sees controller/method-security exceptions — without this handler the SPA
     * would receive an empty 403 body and could not branch on {@code 40300}.
     */
    public static AccessDeniedHandler forbidden(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> write(response, objectMapper,
                HttpStatus.FORBIDDEN, ApiResult.fail(ErrorCode.FORBIDDEN));
    }
}
