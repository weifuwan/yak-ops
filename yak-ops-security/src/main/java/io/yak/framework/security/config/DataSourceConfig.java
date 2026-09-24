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

/**
 * 安全模块独立数据源与 MyBatis 配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YakSecurityProperties.class)
@ConditionalOnClass({
        DataSource.class,
        SqlSessionFactory.class,
        MybatisSqlSessionFactoryBean.class
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
        basePackages = "io.yak.ops.dao.mapper.security",
        sqlSessionTemplateRef = "yakSecuritySqlSessionTemplate"
)
public class DataSourceConfig {


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
     * 安全模块独立 SqlSessionFactory。
     *
     * <p>Schema migration is owned by yak-ops-dao and must complete first.</p>
     */
    @Bean("yakSecuritySqlSessionFactory")
    @DependsOn("yakOpsFlyway")
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
