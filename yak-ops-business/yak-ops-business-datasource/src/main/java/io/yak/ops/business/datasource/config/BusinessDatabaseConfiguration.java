package io.yak.ops.business.datasource.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.yak.ops.common.mybatis.MybatisPlusFactorySupport;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Yak Ops 业务模块共享的数据源、MyBatis 与事务基础设施。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.database",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(BusinessDatabaseProperties.class)
public class BusinessDatabaseConfiguration {

    @Primary
    @Bean(
            name = {
                    "yakBusinessDataSource",
                    "opsDataSource",
                    "opsResourceDataSource",
                    "offlineSyncDataSource"
            },
            destroyMethod = "close")
    public HikariDataSource yakBusinessDataSource(BusinessDatabaseProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("YakBusinessDatabasePool");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(
            name = {
                    "yakBusinessTransactionManager",
                    "opsDataSourceTransactionManager",
                    "opsResourceTransactionManager",
                    "offlineSyncTransactionManager"
            })
    public PlatformTransactionManager yakBusinessTransactionManager(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @DependsOn("yakOpsFlyway")
    @Bean(
            name = {
                    "yakBusinessSqlSessionFactory",
                    "opsDataSourceSqlSessionFactory",
                    "opsResourceSqlSessionFactory",
                    "offlineSyncSqlSessionFactory"
            })
    public SqlSessionFactory yakBusinessSqlSessionFactory(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("io.yak.ops.common.bean.po.datasource");

        // All Yak Ops business modules share this SqlSessionFactory. Each module keeps its XML files
        // under mapper/<domain>/ so complex SQL stays close to the owning business module.
        Resource[] mapperLocations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml");
        if (mapperLocations.length > 0) {
            factory.setMapperLocations(mapperLocations);
        }

        factory.setConfiguration(MybatisPlusFactorySupport.createConfiguration());
        factory.setGlobalConfig(MybatisPlusFactorySupport.createGlobalConfig());

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(interceptor);
        return factory.getObject();
    }

    @Primary
    @Bean(
            name = {
                    "yakBusinessSqlSessionTemplate",
                    "opsDataSourceSqlSessionTemplate",
                    "opsResourceSqlSessionTemplate",
                    "offlineSyncSqlSessionTemplate"
            })
    public SqlSessionTemplate yakBusinessSqlSessionTemplate(
            @Qualifier("yakBusinessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
