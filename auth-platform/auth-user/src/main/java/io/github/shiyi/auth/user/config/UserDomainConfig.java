package io.github.shiyi.auth.user.config;

import javax.sql.DataSource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.shiyi.auth.user.account.InMemoryUserAccountService;
import io.github.shiyi.auth.user.account.JdbcUserAccountService;
import io.github.shiyi.auth.user.account.UserAccountService;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.service.InMemoryUserService;
import io.github.shiyi.auth.user.service.JdbcUserService;
import io.github.shiyi.auth.user.service.UserService;

/**
 * User-domain wiring, dual mode (same principle as PersistenceConfig in auth-server):
 *
 * <ul>
 *   <li>DataSource present → MyBatis-Plus mappers + JDBC services;</li>
 *   <li>no DataSource → in-memory fallback (dev/demo only).</li>
 * </ul>
 */
@Configuration
public class UserDomainConfig {

    /** MapperScan only when a DataSource exists; otherwise mappers have no factory and would fail boot. */
    @Configuration
    @ConditionalOnBean(DataSource.class)
    @MapperScan("io.github.shiyi.auth.user.mapper")
    static class MapperScanConfig {
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    UserAccountService jdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper) {
        return new JdbcUserAccountService(userMapper, loginLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean(UserAccountService.class)
    UserAccountService inMemoryUserAccountService() {
        return new InMemoryUserAccountService();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    UserService jdbcUserService(SysUserMapper userMapper) {
        return new JdbcUserService(userMapper);
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    UserService inMemoryUserService() {
        return new InMemoryUserService();
    }
}
