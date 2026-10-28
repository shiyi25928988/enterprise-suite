package io.github.shiyi.auth.server.auth;

/**
 * JSON login request body posted to {@code POST /api/auth/login}.
 *
 * @param username login name
 * @param password raw password
 * @param redirect optional: the original {@code /oauth2/authorize?...} URL to resume
 *                 after login; when absent the server falls back to the saved request
 */
public record LoginRequest(String username, String password, String redirect) {

    /** Request attribute key under which the parsed body travels to the success handler. */
    public static final String REQUEST_ATTR = "LOGIN_REQUEST";
}
