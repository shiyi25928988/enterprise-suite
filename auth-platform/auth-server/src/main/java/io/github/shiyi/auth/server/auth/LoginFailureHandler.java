package io.github.shiyi.auth.server.auth;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;

/**
 * Login failure: replies HTTP 401 + JSON with a stable error code
 * so the SPA can show the right message (wrong password vs locked vs generic).
 */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    public LoginFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        ApiResult<Void> body;
        if (exception instanceof org.springframework.security.authentication.CredentialsExpiredException) {
            body = ApiResult.fail(ErrorCode.PASSWORD_EXPIRED);
        } else if (exception instanceof LockedException || exception instanceof DisabledException
                || exception instanceof AccountStatusException) {
            body = ApiResult.fail(ErrorCode.ACCOUNT_LOCKED);
        } else if (exception instanceof BadCredentialsException) {
            body = ApiResult.fail(ErrorCode.BAD_CREDENTIALS);
        } else {
            body = ApiResult.fail(ErrorCode.UNAUTHENTICATED);
        }
        JsonResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED, body);
    }
}
