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
            long existing = repo.createProcessDefinitionQuery()
                    .processDefinitionKey("leave-approval").count();
            if (existing > 0) {
                return;
            }
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
