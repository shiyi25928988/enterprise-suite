package io.github.shiyi.auth.workflow.config;

import org.flowable.engine.RepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import io.github.shiyi.auth.workflow.ai.AiReviewDelegate;
import io.github.shiyi.auth.workflow.ai.AiReviewProperties;
import io.github.shiyi.auth.workflow.ai.WorkflowVar;
import io.github.shiyi.auth.workflow.form.FlowFormService;
import io.github.shiyi.auth.workflow.mapper.FlowFormMapper;
import io.github.shiyi.auth.workflow.flow.WorkflowService;

/**
 * W1: workflow module wiring. Everything sits behind the engine bean, so
 * in-memory demo mode simply has no workflow beans (controllers report it).
 */
@Configuration
@EnableConfigurationProperties(AiReviewProperties.class)
@org.mybatis.spring.annotation.MapperScan("io.github.shiyi.auth.workflow.mapper")
public class WorkflowConfig {

    @Bean
    WorkflowService workflowService(org.flowable.engine.RepositoryService repository,
                                    org.flowable.engine.RuntimeService runtime,
                                    org.flowable.engine.TaskService tasks,
                                    org.flowable.engine.HistoryService history) {
        return new WorkflowService(repository, runtime, tasks, history);
    }

    @Bean
    io.github.shiyi.auth.workflow.analytics.FlowAnalyticsService flowAnalyticsService(
            org.flowable.engine.HistoryService history,
            org.flowable.engine.RuntimeService runtime,
            org.flowable.engine.TaskService tasks,
            org.flowable.engine.RepositoryService repository) {
        return new io.github.shiyi.auth.workflow.analytics.FlowAnalyticsService(
                history, runtime, tasks, repository);
    }

    /**
     * BPMN expression helper for due dates: {@code ${slaClock.hours(24)}}.
     */
    @Bean
    io.github.shiyi.auth.workflow.sla.SlaClock slaClock() {
        return new io.github.shiyi.auth.workflow.sla.SlaClock();
    }

    /**
     * SLA overdue sweep — only wired when webhook notifications exist; the
     * schedule lives on the bean method, the on/off switch is
     * {@code auth.flow.sla.enabled} via bean registration.
     */
    @org.springframework.context.annotation.Bean(name = "flowSlaScanner")
    Object flowSlaScanner(org.springframework.beans.factory.ObjectProvider<org.flowable.engine.TaskService> tasks,
                          org.springframework.beans.factory.ObjectProvider<io.github.shiyi.auth.user.notify.NotificationService> notifications,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${auth.flow.sla.enabled:true}") boolean enabled) {
        if (!enabled) {
            return null; // returning null from @Bean = no bean — intentional off switch
        }
        var taskService = tasks.getIfAvailable();
        var notify = notifications.getIfAvailable();
        if (taskService == null || notify == null) {
            return null;
        }
        return new io.github.shiyi.auth.workflow.sla.SlaScanner(taskService, notify);
    }

    @Bean
    FlowFormService flowFormService(FlowFormMapper mapper) {
        return new FlowFormService(mapper);
    }

    /**
     * The AI participant node. Registered under the BPMN-referenced name
     * {@code aiReviewDelegate}; behaviour is driven by auth.flow.ai.*.
     */
    @Bean(name = "aiReviewDelegate")
    AiReviewDelegate aiReviewDelegate(AiReviewProperties props) {
        return new AiReviewDelegate(props);
    }

    /**
     * Ships the leave-approval template on first boot: engine + form schema +
     * a ready-to-run example of the AI node. No-op once deployed.
     */
    @Bean
    org.springframework.boot.ApplicationRunner workflowTemplateBootstrap(
            ObjectProvider<RepositoryService> repository,
            ObjectProvider<FlowFormService> forms) {
        return args -> {
            RepositoryService repo = repository.getIfAvailable();
            FlowFormService formService = forms.getIfAvailable();
            if (repo == null || formService == null) {
                return;
            }
            // W1: leave-approval template (first boot only)
            long existing = repo.createProcessDefinitionQuery()
                    .processDefinitionKey("leave-approval").count();
            if (existing == 0) {
                try (var in = new org.springframework.core.io.ClassPathResource(
                        "flows/leave-approval.bpmn20.xml").getInputStream()) {
                    repo.createDeployment()
                            .name("leave-approval")
                            .addInputStream("leave-approval.bpmn20.xml", in)
                            .deploy();
                }
                if (formService.get("leave-approval") == null) {
                    formService.save("leave-approval", "请假申请", """
                            {"fields":[
                              {"key":"leaveType","label":"请假类型","type":"enum","required":true,
                               "options":["事假","年假","病假","调休"]},
                              {"key":"startDate","label":"开始日期","type":"date","required":true},
                              {"key":"days","label":"请假天数","type":"number","required":true},
                              {"key":"reason","label":"请假事由","type":"text","required":true}
                            ]}""", "system");
                }
            }
            // W2: countersign demo — parallel multi-instance approval with a
            // one-veto rule and SLA due dates on every task
            long existingPurchase = repo.createProcessDefinitionQuery()
                    .processDefinitionKey("purchase-approval").count();
            if (existingPurchase == 0) {
                try (var in = new org.springframework.core.io.ClassPathResource(
                        "flows/purchase-approval.bpmn20.xml").getInputStream()) {
                    repo.createDeployment()
                            .name("purchase-approval")
                            .addInputStream("purchase-approval.bpmn20.xml", in)
                            .deploy();
                }
                if (formService.get("purchase-approval") == null) {
                    formService.save("purchase-approval", "采购会签申请", """
                            {"fields":[
                              {"key":"item","label":"采购项目","type":"string","required":true},
                              {"key":"amount","label":"金额（元）","type":"number","required":true},
                              {"key":"approvers","label":"会签人（逗号分隔用户名）","type":"string","required":true},
                              {"key":"reason","label":"采购事由","type":"text","required":true}
                            ]}""", "system");
                }
            }
        };
    }

    /** MP pagination for this module's tables (mapper scan is module-scoped). */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    MybatisPlusInterceptor workflowMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
