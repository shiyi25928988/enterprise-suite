package io.github.shiyi.auth.server.config;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.server.auth.JsonResponses;
import io.github.shiyi.auth.server.web.GlobalExceptionHandler;
import io.github.shiyi.auth.user.entity.SysMessage;
import io.github.shiyi.auth.user.service.MessageService;
import io.github.shiyi.auth.user.web.MessageController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the message center's access matrix.
 *
 * <p>The inbox itself carries no new security surface — every endpoint sits on
 * the generic {@code /api/**} chain and is "authenticated only", which that
 * chain (and its real wiring) already guarantees. What IS new and therefore
 * pinned here:</p>
 * <ul>
 *   <li>inbox scoping: {@code markRead} on someone else's row is 404/40400 —
 *       the service layer filters by the session username, never by trust in
 *       the client;</li>
 *   <li>announcements are {@code ADMIN}-only through method security;</li>
 *   <li>CSRF applies to every state-changing call.</li>
 * </ul>
 *
 * <p>Same caveat as {@code WorkflowApiSecurityTest}: this context holds a
 * replica of the {@code /api/**} chain's gate (session + CSRF + JSON
 * 401/403), because importing the full {@link SecurityConfig} drags in the
 * whole SAS assembly. The replica reproduces exactly the semantics this test
 * relies on; the real chain is covered by the workflow security test and the
 * live E2E runs. Authentication uses the {@code user(..)} post-processor for
 * the same session-filter reason documented there.</p>
 */
@SpringBootTest(classes = MessageApiTest.MinimalApp.class)
@AutoConfigureMockMvc
class MessageApiTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({MessageController.class, GlobalExceptionHandler.class})
    static class MinimalApp {

        @Bean
        CsrfTokenRepository csrfTokenRepository() {
            return CookieCsrfTokenRepository.withHttpOnlyFalse();
        }

        /** Replica of the /api/** gate: session auth, CSRF, JSON 401/403. */
        @Bean
        SecurityFilterChain apiChain(HttpSecurity http, CsrfTokenRepository repo,
                                     tools.jackson.databind.ObjectMapper om) throws Exception {
            http.securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(c -> c.csrfTokenRepository(repo))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(JsonResponses.unauthenticated(om))
                        .accessDeniedHandler(JsonResponses.forbidden(om)))
                .sessionManagement(s -> s.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED));
            return http.build();
        }

        @Bean
        MessageService messageService() {
            return Mockito.mock(MessageService.class);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MessageService messages;

    private static RequestPostProcessor employee() {
        return user("alice").roles("USER");
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private static SysMessage row(long id, String title, boolean read) {
        SysMessage m = new SysMessage();
        m.setId(id);
        m.setRecipient("alice");
        m.setType("event");
        m.setEvent("task.assigned");
        m.setTitle(title);
        m.setCreateTime(LocalDateTime.of(2026, 10, 30, 10, 0));
        m.setReadAt(read ? LocalDateTime.now() : null);
        return m;
    }

    @Test
    @DisplayName("anonymous callers get the 401 envelope, not an HTML page")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("the inbox answers only with the caller's own rows")
    void inboxIsPersonal() throws Exception {
        Page<SysMessage> page = new Page<>(1, 20);
        page.setRecords(List.of(row(1, "您有新的审批任务：总监审批", false)));
        page.setTotal(1);
        when(messages.inbox(eq("alice"), anyBoolean(), anyInt(), anyInt())).thenReturn(page);
        when(messages.unreadCount("alice")).thenReturn(3L);

        mvc.perform(get("/api/v1/messages").with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].title").value("您有新的审批任务：总监审批"))
                .andExpect(jsonPath("$.data.rows[0].read").value(false));
    }

    @Test
    @DisplayName("marking someone else's message read is 404 in the envelope")
    void foreignMessageIsNotFound() throws Exception {
        when(messages.markRead(eq(99L), eq("alice"))).thenReturn(false);

        mvc.perform(post("/api/v1/messages/99/read").with(employee()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("announcements are admin-only")
    void announcementsAreAdminOnly() throws Exception {
        String body = "{\"title\":\"停机维护\",\"body\":\"周六 22:00\",\"roleCode\":null}";
        mvc.perform(post("/api/v1/messages/announcements").with(employee()).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("CSRF still applies to announcements")
    void csrfApplies() throws Exception {
        mvc.perform(post("/api/v1/messages/announcements").with(admin())
                        .contentType("application/json")
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin broadcast reports its recipient count")
    void adminBroadcast() throws Exception {
        when(messages.announce(eq("admin"), eq("停机维护"), any(), any(), any())).thenReturn(5);

        mvc.perform(post("/api/v1/messages/announcements").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"停机维护\",\"body\":\"周六 22:00\",\"roleCode\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delivered").value(5));
    }

    @Test
    @DisplayName("an empty announcement title is 400, not a broadcast of blanks")
    void blankTitleRejected() throws Exception {
        mvc.perform(post("/api/v1/messages/announcements").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
