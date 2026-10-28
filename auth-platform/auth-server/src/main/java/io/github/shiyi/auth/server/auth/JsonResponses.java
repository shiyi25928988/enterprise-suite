package io.github.shiyi.auth.server.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import io.github.shiyi.auth.common.api.ApiResult;

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
}
