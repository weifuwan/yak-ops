package io.yak.ops.boot.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.yak.ops.security.config.YakSecurityProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 统一配置 Yak Ops 的 MyBatis-Plus 插件和全局行为。
 *
 * <p>DataSource、TransactionManager、SqlSessionFactory 和 SqlSessionTemplate 由 Spring Boot /
 * MyBatis-Plus Starter 自动装配；Boot 不为 Datasource 或 Security 重复创建独立运行时。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("io.yak.ops.dao.mapper")
public class MybatisPlusConfiguration {

    private static final String SECURITY_USER_TABLE = "yak_security_user";
    private static final String SECURITY_TENANT_COLUMN = "app_name";

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor(YakSecurityProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (properties.isEnabled() && properties.isDatabaseEnabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(securityTenantLineHandler(properties)));
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    MybatisPlusPropertiesCustomizer mybatisPlusPropertiesCustomizer() {
        return properties -> {
            MybatisPlusProperties.CoreConfiguration configuration = new MybatisPlusProperties.CoreConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setJdbcTypeForNull(JdbcType.NULL);
            configuration.setCacheEnabled(false);
            properties.setConfiguration(configuration);
            properties.getGlobalConfig().setBanner(false);
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "yak.security",
            name = {"enabled", "database-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    MetaObjectHandler securityMetaObjectHandler(YakSecurityProperties properties) {
        String applicationName = requireText(properties.getApplicationName(), "yak.security.application-name");
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "appName", String.class, applicationName);
            }

            @Override
            public void updateFill(MetaObject metaObject) {}
        };
    }

    private TenantLineHandler securityTenantLineHandler(YakSecurityProperties properties) {
        String applicationName = requireText(properties.getApplicationName(), "yak.security.application-name");
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue(applicationName);
            }

            @Override
            public String getTenantIdColumn() {
                return SECURITY_TENANT_COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return !SECURITY_USER_TABLE.equalsIgnoreCase(tableName);
            }
        };
    }

    private static String requireText(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required configuration: " + key);
        }
        return value;
    }
}
