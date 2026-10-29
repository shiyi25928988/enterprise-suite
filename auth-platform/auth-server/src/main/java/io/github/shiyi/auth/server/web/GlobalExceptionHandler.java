package io.github.shiyi.auth.server.web;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;

/**
 * Translates exceptions into the unified {@link ApiResult} envelope.
 *
 * <p>HTTP status mirrors the error code family; the body code is the contract
 * frontend code actually branches on.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(toHttpStatus(e.errorCode()))
                .body(ApiResult.fail(e.errorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.BAD_REQUEST.message());
        return ResponseEntity.badRequest().body(ApiResult.fail(ErrorCode.BAD_REQUEST, msg));
    }

    /**
     * Method-security denials ({@code @PreAuthorize}) surface as
     * {@code AuthorizationDeniedException} (a subclass of AccessDeniedException).
     * Without this handler the generic fallback turns a 403 into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResult.fail(ErrorCode.FORBIDDEN));
    }

    /**
     * Unmatched navigation-style GET paths are forwarded to the SPA shell so
     * the Vue router can render its own 404 page — bookmarks of stale routes
     * get a friendly page instead of a JSON 500. Unknown /api/** paths return
     * a proper 404 envelope; everything else keeps the fallback behaviour.
     *
     * <p>The forward is done by hand because a view-name return value would
     * be written as the response body inside a {@code @RestControllerAdvice}.</p>
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Object handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e,
                                   HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response)
            throws jakarta.servlet.ServletException, java.io.IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResult.fail(ErrorCode.NOT_FOUND));
        }
        boolean navigable = "GET".equalsIgnoreCase(request.getMethod())
                && !path.contains(".")
                && !path.startsWith("/oauth2/")
                && !path.startsWith("/connect/")
                && !path.startsWith("/actuator/")
                && !path.startsWith("/assets/");
        if (navigable) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return null;
        }
        return handleFallback(e, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleFallback(Exception e, HttpServletRequest request) {
        log.error("unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus toHttpStatus(ErrorCode code) {
        return switch (code) {
            case BAD_REQUEST, PASSWORD_EXPIRED -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED, BAD_CREDENTIALS, ACCOUNT_LOCKED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
