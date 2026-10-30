package io.github.shiyi.auth.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.shiyi.auth.server.web.GlobalExceptionHandler;
import io.github.shiyi.auth.workflow.web.FlowController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the authorization table of the workflow chain.
 *
 * <p>Two things here are easy to break silently and hard to spot in review:</p>
 * <ol>
 *   <li><b>Chain order.</b> The {@code /api/**} chain matches a superset of
 *       {@code /api/v1/flow/**}. If its {@code @Order} ever drops below this
 *       chain's, every ADMIN rule below degrades to "authenticated" and any
 *       happy-path-only test would still pass.</li>
 *   <li><b>Method mismatch.</b> {@code GET /forms} is ADMIN, {@code GET
 *       /forms/{key}} is not. Collapsing them into one path-prefix rule would
 *       lock employees out of the form they must fill in.</li>
 * </ol>
 *
 * <p>The context is deliberately minimal: the flow chain, the real
 * {@link FlowController}, the exception handler that shapes the response, and
 * no persistence at all. In that mode the controller answers 400 ("workflow
 * requires the jdbc profile") — which is exactly the signal we want, because it
 * proves the request <em>passed</em> the security gate and reached the handler.
 * A 403 means the gate held.</p>
 *
 * <p>Authentication is injected with the {@code user(..)} post-processor rather
 * than {@code @WithMockUser}: this chain loads its context from the servlet
 * session ({@code SecurityContextHolderFilter} clears the holder first), so a
 * context parked in {@code TestSecurityContextHolder} is gone by the time the
 * request is authorized — the test would then assert 401 for everyone and pass
 * for the wrong reason.</p>
 */
@SpringBootTest(classes = WorkflowApiSecurityTest.MinimalApp.class)
@AutoConfigureMockMvc
class WorkflowApiSecurityTest {

    /** No component scan: only the security chain under test plus the web bits. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({WorkflowApiSecurityConfig.class, FlowController.class, GlobalExceptionHandler.class})
    static class MinimalApp {

        @Bean
        CsrfTokenRepository csrfTokenRepository() {
            return CookieCsrfTokenRepository.withHttpOnlyFalse();
        }
    }

    /** "Reached the controller" — the workflow module reports its missing engine. */
    private static final int REACHED_CONTROLLER = 400;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor employee() {
        return user("alice").roles("USER");
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    @Test
    @DisplayName("anonymous callers get 401 in the platform envelope, not an HTML page")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/flow/todo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("any signed-in employee may read their own work")
    void employeeReachesOwnWork() throws Exception {
        mvc.perform(get("/api/v1/flow/todo").with(employee()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(get("/api/v1/flow/done").with(employee()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(get("/api/v1/flow/startable").with(employee()))
                .andExpect(status().is(REACHED_CONTROLLER));
    }

    @Test
    @DisplayName("a plain employee may read a form schema — GET /forms/{key} is not admin-only")
    void employeeReadsOneForm() throws Exception {
        mvc.perform(get("/api/v1/flow/forms/leave-approval").with(employee()))
                .andExpect(status().is(REACHED_CONTROLLER));
    }

    @Test
    @DisplayName("a plain employee may not list form schemas")
    void employeeCannotListForms() throws Exception {
        mvc.perform(get("/api/v1/flow/forms").with(employee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("design-time endpoints are admin-only: deploy / definitions / forms / analytics")
    void employeeCannotDeploy() throws Exception {
        mvc.perform(post("/api/v1/flow/deploy").with(employee()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"bpmnXml\":\"<definitions/>\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mvc.perform(get("/api/v1/flow/definitions").with(employee()))
                .andExpect(status().isForbidden());
        // the XML is the routing logic itself (who approves what) — same gate
        mvc.perform(get("/api/v1/flow/definitions/leave-approval/xml").with(employee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(get("/api/v1/flow/analytics/overview").with(employee()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/flow/forms/leave-approval").with(employee()).with(csrf())
                        .contentType("application/json")
                        .content("{\"formName\":\"x\",\"schemaJson\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin passes the gate and reaches the controller")
    void adminReachesDesignTimeEndpoints() throws Exception {
        mvc.perform(get("/api/v1/flow/definitions").with(admin()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(get("/api/v1/flow/definitions/leave-approval/xml").with(admin()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(get("/api/v1/flow/forms").with(admin()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(get("/api/v1/flow/analytics/overview").with(admin()))
                .andExpect(status().is(REACHED_CONTROLLER));
        mvc.perform(post("/api/v1/flow/deploy").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"bpmnXml\":\"<definitions/>\"}"))
                .andExpect(status().is(REACHED_CONTROLLER));
    }

    @Test
    @DisplayName("CSRF still applies to state-changing calls on this chain")
    void csrfIsStillEnforced() throws Exception {
        // same call as above, minus the token: 403 while the identical request
        // WITH csrf() reaches the controller — that difference is the assertion
        mvc.perform(post("/api/v1/flow/deploy").with(admin())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"bpmnXml\":\"<definitions/>\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * The minimal context only holds one chain, so the ordering itself has to be
     * read off the beans that {@code auth-server} actually registers. Cheap and
     * exact: it fails the moment someone renumbers the chains and lets
     * {@code /api/**} swallow the workflow paths (all of them would then answer
     * "authenticated" instead of honouring the ADMIN rows above).
     */
    @Test
    @DisplayName("the workflow chain is ordered ahead of the generic /api/** chain")
    void flowChainPrecedesGenericApiChain() throws Exception {
        int flow = orderOf(WorkflowApiSecurityConfig.class, "workflowApiSecurityFilterChain");
        int api = orderOf(SecurityConfig.class, "apiSecurityFilterChain");
        int shell = orderOf(SecurityConfig.class, "defaultSecurityFilterChain");
        int protocol = orderOf(SecurityConfig.class, "authorizationServerSecurityFilterChain");

        org.assertj.core.api.Assertions.assertThat(flow)
                .as("flow chain must match before /api/**")
                .isLessThan(api);
        org.assertj.core.api.Assertions.assertThat(protocol)
                .as("SAS protocol endpoints keep chain 1")
                .isLessThan(flow);
        org.assertj.core.api.Assertions.assertThat(api)
                .as("SPA shell stays last")
                .isLessThan(shell);
    }

    private static int orderOf(Class<?> config, String name) {
        for (java.lang.reflect.Method m : config.getDeclaredMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            org.springframework.core.annotation.Order order =
                    m.getAnnotation(org.springframework.core.annotation.Order.class);
            org.assertj.core.api.Assertions.assertThat(order)
                    .as("%s#%s must carry @Order", config.getSimpleName(), name).isNotNull();
            return order.value();
        }
        throw new AssertionError("no @Bean method named " + name + " on " + config.getSimpleName());
    }
}
