package io.yak.framework.security.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Collections;

/**
 * 安全模块独立数据源与 MyBatis 配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
@ConditionalOnClass({
        DataSource.class,
        SqlSessionFactory.class,
        MybatisSqlSessionFactoryBean.class,
        Flyway.class
})
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {
                "database-enabled",
                "datasource.enabled"
        },
        havingValue = "true",
        matchIfMissing = true
)
@MapperScan(
        basePackages = "io.yak.framework.security.dao.mapper",
        sqlSessionTemplateRef = "yakSecuritySqlSessionTemplate"
)
public class DataSourceConfig {

    static final String FLYWAY_MIGRATION_LOCATION =
            "classpath:yak-security/db/migration";

    /**
     * MyBatis-Plus 全局配置。
     */
    @Bean("yakSecurityGlobalConfig")
    public GlobalConfig yakSecurityGlobalConfig(
            YakSecurityProperties properties) {

        requireText(
                properties.getApplicationName(),
                "yak.security.application-name"
        );

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        /*
         * AppBasePO.appName 使用 FieldFill.INSERT，
         * 必须为自定义 SqlSessionFactory 显式注册填充器。
         */
        globalConfig.setMetaObjectHandler(
                new YakSecurityMetaObjectHandler(
                        properties.getApplicationName()
                )
        );

        GlobalConfig.DbConfig dbConfig =
                new GlobalConfig.DbConfig();

        dbConfig.setIdType(IdType.AUTO);

        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }

    /**
     * 安全模块独立数据源。
     */
    @Bean(
            name = "yakSecurityDataSource",
            destroyMethod = "close"
    )
    public DataSource yakSecurityDataSource(
            YakSecurityProperties properties) {

        YakSecurityProperties.DataSourceProperties datasource =
                properties.getDatasource();

        if (datasource == null) {
            throw new IllegalStateException(
                    "Missing required configuration: "
                            + "yak.security.datasource");
        }

        requireText(
                datasource.getUrl(),
                "yak.security.datasource.url"
        );

        requireText(
                datasource.getUsername(),
                "yak.security.datasource.username"
        );

        requireText(
                datasource.getDriverClassName(),
                "yak.security.datasource.driver-class-name"
        );

        DruidDataSource result = new DruidDataSource();

        result.setUrl(datasource.getUrl());
        result.setUsername(datasource.getUsername());
        result.setPassword(datasource.getPassword());
        result.setDriverClassName(
                datasource.getDriverClassName());

        result.setInitialSize(
                datasource.getInitialSize());

        result.setMinIdle(
                datasource.getMinIdle());

        result.setMaxActive(
                datasource.getMaxActive());

        result.setMaxWait(
                datasource.getMaxWait());

        result.setValidationQuery(
                datasource.getValidationQuery());

        result.setTestWhileIdle(
                datasource.isTestWhileIdle());

        result.setTestOnBorrow(
                datasource.isTestOnBorrow());

        result.setTestOnReturn(
                datasource.isTestOnReturn());

        return result;
    }

    /**
     * MyBatis-Plus 插件配置。
     *
     * <p>插件顺序：
     * 1. 多租户插件；
     * 2. 分页插件。
     */
    @Bean("yakSecurityMybatisPlusInterceptor")
    public MybatisPlusInterceptor
    yakSecurityMybatisPlusInterceptor(
            YakSecurityProperties properties) {

        requireText(
                properties.getApplicationName(),
                "yak.security.application-name"
        );

        String applicationName =
                properties.getApplicationName();

        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        /*
         * 多租户插件需要放在分页插件前面。
         *
         * 这里并非传统的 tenant_id，
         * 而是通过 app_name 隔离不同应用的数据。
         */
        TenantLineHandler tenantLineHandler =
                new TenantLineHandler() {

                    @Override
                    public Expression getTenantId() {
                        return new StringValue(
                                applicationName);
                    }

                    @Override
                    public String getTenantIdColumn() {
                        return "app_name";
                    }
                };

        interceptor.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        tenantLineHandler));

        interceptor.addInnerInterceptor(
                new PaginationInnerInterceptor(
                        DbType.MARIADB));

        return interceptor;
    }

    /**
     * 在安全模块独立数据源上执行 Flyway 迁移。
     */
    @Bean(
            name = "yakSecurityFlyway",
            initMethod = "migrate"
    )
    public Flyway yakSecurityFlyway(
            @Qualifier("yakSecurityDataSource")
                    DataSource dataSource,
            YakSecurityProperties properties) {

        requireText(
                properties.getApplicationName(),
                "yak.security.application-name"
        );

        return Flyway.configure()
                .dataSource(dataSource)
                // 使用模块专属目录，避免独立数据源误执行宿主应用的迁移脚本。
                .locations(FLYWAY_MIGRATION_LOCATION)
                .placeholders(Collections.singletonMap(
                        "appName",
                        properties.getApplicationName()))
                /*
                 * 宿主模块可能已经在共享 schema 中创建了业务表。
                 * 默认基线版本 1 会跳过安全模块的 V1 建表脚本，因此显式从 0 开始。
                 */
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                /*
                 * 安全模块可能与宿主应用共享 Flyway history table。
                 * 当宿主已经记录了更高版本时，后续新增的安全模块迁移会被视为迟到版本。
                 * 允许 out-of-order 执行，确保合法的安全模块迁移不会阻塞应用启动。
                 */
                .outOfOrder(true)
                .load();
    }

    /**
     * 安全模块独立 SqlSessionFactory。
     */
    @Bean("yakSecuritySqlSessionFactory")
    @DependsOn("yakSecurityFlyway")
    public SqlSessionFactory
    yakSecuritySqlSessionFactory(
            @Qualifier("yakSecurityDataSource")
                    DataSource dataSource,

            @Qualifier("yakSecurityGlobalConfig")
                    GlobalConfig globalConfig,

            @Qualifier(
                    "yakSecurityMybatisPlusInterceptor")
                    MybatisPlusInterceptor interceptor)
            throws Exception {

        MybatisSqlSessionFactoryBean factory =
                new MybatisSqlSessionFactoryBean();

        factory.setDataSource(dataSource);

        MybatisConfiguration configuration =
                new MybatisConfiguration();

        configuration.setMapUnderscoreToCamelCase(true);

        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        factory.setPlugins(interceptor);

        SqlSessionFactory sqlSessionFactory =
                factory.getObject();

        if (sqlSessionFactory == null) {
            throw new IllegalStateException(
                    "Failed to create "
                            + "yakSecuritySqlSessionFactory");
        }

        return sqlSessionFactory;
    }

    /**
     * 安全模块 SqlSessionTemplate。
     */
    @Bean("yakSecuritySqlSessionTemplate")
    public SqlSessionTemplate
    yakSecuritySqlSessionTemplate(
            @Qualifier(
                    "yakSecuritySqlSessionFactory")
                    SqlSessionFactory factory) {

        return new SqlSessionTemplate(factory);
    }

    /**
     * 安全模块事务管理器。
     */
    @Bean("yakSecurityTransactionManager")
    public PlatformTransactionManager
    yakSecurityTransactionManager(
            @Qualifier("yakSecurityDataSource")
                    DataSource dataSource) {

        return new DataSourceTransactionManager(
                dataSource);
    }

    /**
     * 校验必填字符串配置。
     */
    private static void requireText(
            String value,
            String key) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Missing required configuration: "
                            + key);
        }
    }
}
