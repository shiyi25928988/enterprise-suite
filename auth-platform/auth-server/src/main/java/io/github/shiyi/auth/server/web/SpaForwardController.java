package io.github.shiyi.auth.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page console for its client-side routes.
 *
 * <p>In the packaged deployment the SPA is served from {@code static/}, so only
 * {@code /} resolves out of the box — a deep link such as {@code /apps} hit
 * {@code NoResourceFoundException} (HTTP 500 were it not for the error handler).
 * The paths are listed explicitly instead of using a catch-all so API, OAuth2
 * and actuator endpoints can never be swallowed by the forward.</p>
 *
 * <p>{@code /login} is included on purpose: with the generated form login
 * disabled this is what makes a protocol redirect to {@code /login} land on the
 * real login screen (which posts JSON to {@code /api/auth/login}).</p>
 */
@Controller
public class SpaForwardController {

    // annotation values must be compile-time constants, hence the inline literal
    @GetMapping({
            "/login",
            "/callback",
            "/wecom-callback",
            "/403",
            "/password",
            "/portal",
            "/users",
            "/orgs",
            "/roles",
            "/apps",
            "/sessions",
            "/sync",
            "/security",
            "/audit",
    })
    public String forward() {
        return "forward:/index.html";
    }
}
