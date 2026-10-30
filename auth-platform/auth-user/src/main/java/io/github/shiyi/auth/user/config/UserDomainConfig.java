package io.github.shiyi.auth.user.config;

import java.util.List;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.shiyi.auth.user.account.InMemoryUserAccountService;
import io.github.shiyi.auth.user.account.JdbcUserAccountService;
import io.github.shiyi.auth.user.account.UserAccountService;
import io.github.shiyi.auth.user.dto.OrgDTO;
import io.github.shiyi.auth.user.dto.RoleRequest;
import io.github.shiyi.auth.user.entity.SysRole;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.mapper.SysOperationLogMapper;
import io.github.shiyi.auth.user.service.InMemoryLoginLogService;
import io.github.shiyi.auth.user.service.JdbcLoginLogService;
import io.github.shiyi.auth.user.service.InMemoryOperationLogService;
import io.github.shiyi.auth.user.service.JdbcOperationLogService;
import io.github.shiyi.auth.user.service.LoginLogService;
import io.github.shiyi.auth.user.service.LoginLogRetention;
import io.github.shiyi.auth.user.service.OperationLogService;
import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysRoleMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.mapper.SysUserRoleMapper;
import io.github.shiyi.auth.user.service.InMemoryUserService;
import io.github.shiyi.auth.user.service.JdbcOrgService;
import io.github.shiyi.auth.user.service.JdbcRoleService;
import io.github.shiyi.auth.user.service.JdbcUserService;
import io.github.shiyi.auth.user.service.OrgService;
import io.github.shiyi.auth.user.service.RoleService;
import io.github.shiyi.auth.user.service.UserService;

/**
 * User-domain wiring, dual mode (same principle as PersistenceConfig in auth-server):
 *
 * <ul>
 *   <li>DataSource present → MyBatis-Plus mappers + JDBC services;</li>
 *   <li>no DataSource → in-memory fallback (dev/demo only, read-only mutations).</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class UserDomainConfig {

    /** Daily purge of audit rows past auth.audit.retention-days (no-op in-memory). */
    @Bean
    LoginLogRetention loginLogRetention(LoginLogService loginLogs,
                                        @org.springframework.beans.factory.annotation.Value(
                                                "${auth.audit.retention-days:90}") int retentionDays) {
        return new LoginLogRetention(loginLogs, retentionDays);
    }

    /** MapperScan only when a DataSource exists; otherwise mappers have no factory and would fail boot. */
    @Configuration
    @ConditionalOnProperty(name = "spring.datasource.url")
    @MapperScan("io.github.shiyi.auth.user.mapper")
    static class MapperScanConfig {
    }

    /** MyBatis-Plus pagination interceptor — without it selectPage() silently returns all rows. */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    // ---------- account / login ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    UserAccountService jdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper,
            @org.springframework.beans.factory.annotation.Value(
                    "${auth.password.max-age-days:90}") int passwordMaxAgeDays,
            org.springframework.beans.factory.ObjectProvider<io.github.shiyi.auth.user.notify.NotificationService> notifications) {
        return new JdbcUserAccountService(userMapper, loginLogMapper, passwordMaxAgeDays, notifications);
    }

    @Bean
    @ConditionalOnMissingBean(UserAccountService.class)
    UserAccountService inMemoryUserAccountService() {
        return new InMemoryUserAccountService();
    }

    // ---------- user CRUD ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    UserService jdbcUserService(SysUserMapper userMapper, SysOrgMapper orgMapper,
                                SysUserRoleMapper userRoleMapper,
                                org.springframework.context.ApplicationEventPublisher events) {
        return new JdbcUserService(userMapper, orgMapper, userRoleMapper, events);
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    UserService inMemoryUserService() {
        return new InMemoryUserService();
    }

    // ---------- user batch import / export (xlsx, jdbc mode only) ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    io.github.shiyi.auth.user.service.UserImportExportService userImportExportService(
            SysUserMapper userMapper, SysOrgMapper orgMapper, SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            org.springframework.transaction.PlatformTransactionManager txManager) {
        return new io.github.shiyi.auth.user.service.UserImportExportService(
                userMapper, orgMapper, roleMapper, userRoleMapper, txManager);
    }

    // ---------- organization ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    OrgService jdbcOrgService(SysOrgMapper orgMapper, SysUserMapper userMapper) {
        return new JdbcOrgService(orgMapper, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean(OrgService.class)
    OrgService inMemoryOrgService() {
        return new OrgService() {
            @Override
            public List<OrgDTO> tree() {
                return List.of(
                        new OrgDTO(1L, 0L, "HQ", "Headquarters", 1, "/1/", 0, 1, List.of(
                                new OrgDTO(2L, 1L, "P1", "Plant No.1", 2, "/1/2/", 0, 1, List.of()))));
            }

            @Override
            public io.github.shiyi.auth.user.dto.OrgDTO create(Long parentId, String orgCode,
                                                               String orgName, Integer orgType, Integer sortNo) {
                throw readOnly();
            }

            @Override
            public io.github.shiyi.auth.user.dto.OrgDTO update(Long id, String orgName,
                                                               Integer sortNo, Long leaderId, Integer status) {
                throw readOnly();
            }

            @Override
            public void delete(Long id) {
                throw readOnly();
            }

            private io.github.shiyi.auth.common.exception.BizException readOnly() {
                return new io.github.shiyi.auth.common.exception.BizException(
                        io.github.shiyi.auth.common.api.ErrorCode.BAD_REQUEST,
                        "in-memory mode is read-only; start with the jdbc profile to mutate");
            }
        };
    }

    // ---------- roles ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    RoleService jdbcRoleService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                                org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new JdbcRoleService(roleMapper, userRoleMapper, jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(RoleService.class)
    RoleService inMemoryRoleService() {
        return new RoleService() {
            @Override
            public List<SysRole> list() {
                SysRole admin = new SysRole();
                admin.setId(1L);
                admin.setRoleCode("ADMIN");
                admin.setRoleName("Administrator");
                admin.setDataScope(1);
                admin.setStatus(1);
                return List.of(admin);
            }

            @Override
            public List<io.github.shiyi.auth.user.dto.RoleUsageDTO> usage() {
                return list().stream()
                        .map(r -> new io.github.shiyi.auth.user.dto.RoleUsageDTO(r.getId(), 0, 0,
                                "ADMIN".equals(r.getRoleCode())))
                        .toList();
            }

            @Override
            public SysRole create(RoleRequest request) {
                throw readOnly();
            }

            @Override
            public SysRole update(Long id, String roleName, Integer dataScope, Integer status) {
                throw readOnly();
            }

            @Override
            public void delete(Long id) {
                throw readOnly();
            }

            private io.github.shiyi.auth.common.exception.BizException readOnly() {
                return new io.github.shiyi.auth.common.exception.BizException(
                        io.github.shiyi.auth.common.api.ErrorCode.BAD_REQUEST,
                        "in-memory mode is read-only; start with the jdbc profile to mutate");
            }
        };
    }

    // ---------- management operation audit ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    OperationLogService jdbcOperationLogService(SysOperationLogMapper mapper) {
        return new JdbcOperationLogService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(OperationLogService.class)
    OperationLogService inMemoryOperationLogService() {
        return new InMemoryOperationLogService();
    }

    /** Registered as a bean so Boot applies it to every security chain (filters to /api writes only). */
    @Bean
    org.springframework.web.filter.OncePerRequestFilter operationAuditFilter(OperationLogService operationLogs) {
        return new io.github.shiyi.auth.user.web.OperationAuditFilter(operationLogs);
    }

    // ---------- login audit ----------

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    LoginLogService jdbcLoginLogService(SysLoginLogMapper loginLogMapper) {
        return new JdbcLoginLogService(loginLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean(LoginLogService.class)
    LoginLogService inMemoryLoginLogService() {
        return new InMemoryLoginLogService();
    }
}
