package io.github.shiyi.auth.user.sync;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;

/**
 * Directory-sync wiring. Every piece is conditional: with no
 * {@code auth.sync.*.enabled=true} and no datasource, none of these beans
 * exist and the platform behaves exactly as before (loose coupling — the
 * feature is configuration-activated, never a hard dependency).
 */
@Configuration
@EnableConfigurationProperties(SyncProperties.class)
public class SyncConfig {

    @Bean
    @ConditionalOnProperty(name = "auth.sync.dingtalk.enabled", havingValue = "true")
    DingTalkDirectory dingTalkDirectory(SyncProperties props) {
        return new DingTalkDirectory(props.getDingtalk());
    }

    @Bean
    @ConditionalOnProperty(name = "auth.sync.wecom.enabled", havingValue = "true")
    WeComDirectory weComDirectory(SyncProperties props) {
        return new WeComDirectory(props.getWecom());
    }

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    UserSyncService userSyncService(SysOrgMapper orgMapper, SysUserMapper userMapper,
                                    ObjectProvider<DirectoryProvider> providers) {
        return new UserSyncService(orgMapper, userMapper, providers.orderedStream().toList());
    }

    /** Scheduled full sync across every enabled source; auth.sync.cron="-" disables. */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    UserSyncScheduler scheduledDirectorySync(ObjectProvider<UserSyncService> service) {
        return new UserSyncScheduler(service);
    }
}
