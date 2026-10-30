package io.github.shiyi.auth.workflow.config;

import javax.sql.DataSource;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W1: embedded Flowable 8 engine. Flowable 8's baseline is Spring Framework 7
 * / Boot 4 / Jackson 3 — the same stack as this project, so the engine is
 * wired programmatically against the platform's own DataSource and transaction
 * manager: one database, one transactional world, no extra service to deploy.
 *
 * <p>Conditional on a datasource like the rest of the persistence layer —
 * in-memory demo mode has no workflow engine (ACT_ tables need a real DB).
 * {@code databaseSchemaUpdate=true} lets the engine create/upgrade its ACT_
 * tables on boot, same spirit as Flyway for the platform tables.</p>
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class WorkflowEngineConfig {

    @Bean(destroyMethod = "close")
    ProcessEngine processEngine(DataSource dataSource,
                                PlatformTransactionManager transactionManager,
                                org.springframework.context.ApplicationContext applicationContext,
                                ObjectProvider<io.github.shiyi.auth.workflow.notify.WorkflowNotifier> notifier) {
        SpringProcessEngineConfiguration cfg = new SpringProcessEngineConfiguration();
        cfg.setDataSource(dataSource);
        cfg.setTransactionManager(transactionManager);
        cfg.setDatabaseSchemaUpdate("true");
        cfg.setDeploymentName("auth-workflow-auto-deploy");
        cfg.setAsyncExecutorActivate(false); // sync engine first; async jobs come later
        // The config object is built by hand (not by the starter), so the
        // ApplicationContextAware callback never fires — do it ourselves or
        // BPMN expressions like ${aiReviewDelegate} cannot see Spring beans.
        cfg.setApplicationContext(applicationContext);
        notifier.ifAvailable(n -> cfg.setEventListeners(java.util.List.of(
                new io.github.shiyi.auth.workflow.notify.TaskCreatedNotifier(n))));
        return cfg.buildProcessEngine();
    }

    @Bean
    @ConditionalOnBean(ProcessEngine.class)
    RepositoryService repositoryService(ProcessEngine engine) {
        return engine.getRepositoryService();
    }

    @Bean
    @ConditionalOnBean(ProcessEngine.class)
    RuntimeService runtimeService(ProcessEngine engine) {
        return engine.getRuntimeService();
    }

    @Bean
    @ConditionalOnBean(ProcessEngine.class)
    TaskService taskService(ProcessEngine engine) {
        return engine.getTaskService();
    }

    @Bean
    @ConditionalOnBean(ProcessEngine.class)
    HistoryService historyService(ProcessEngine engine) {
        return engine.getHistoryService();
    }
}
