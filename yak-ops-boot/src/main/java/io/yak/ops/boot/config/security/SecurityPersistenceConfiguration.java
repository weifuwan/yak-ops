package io.yak.ops.boot.config.security;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.security.config.YakSecurityMetaObjectHandler;
import io.yak.ops.security.config.YakSecurityProperties;
import java.util.Collections;
import javax.sql.DataSource;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@MapperScan(
    basePackages = "io.yak.ops.security.dao.mapper",
    sqlSessionTemplateRef = "yakSecuritySqlSessionTemplate")
public class SecurityPersistenceConfiguration {

  private static final String MIGRATION_LOCATION = "classpath:yak-security/db/migration";

  @Bean("yakSecurityGlobalConfig")
  GlobalConfig securityGlobalConfig(YakSecurityProperties properties) {
    String applicationName = requireApplicationName(properties);
    GlobalConfig globalConfig = new GlobalConfig();
    globalConfig.setBanner(false);
    globalConfig.setMetaObjectHandler(new YakSecurityMetaObjectHandler(applicationName));
    GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
    dbConfig.setIdType(IdType.AUTO);
    globalConfig.setDbConfig(dbConfig);
    return globalConfig;
  }

  @Bean("yakSecurityMybatisPlusInterceptor")
  MybatisPlusInterceptor securityMybatisPlusInterceptor(YakSecurityProperties properties) {
    String applicationName = requireApplicationName(properties);
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    TenantLineHandler tenantLineHandler = new TenantLineHandler() {
      @Override
      public Expression getTenantId() {
        return new StringValue(applicationName);
      }

      @Override
      public String getTenantIdColumn() {
        return "app_name";
      }
    };
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
  }

  @Bean(name = "yakSecurityFlyway", initMethod = "migrate")
  Flyway securityFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      YakSecurityProperties properties) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(MIGRATION_LOCATION)
        .placeholders(Collections.singletonMap(
            "appName", requireApplicationName(properties)))
        .baselineOnMigrate(true)
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .outOfOrder(true)
        .load();
  }

  @Bean("yakSecuritySqlSessionFactory")
  @DependsOn("yakSecurityFlyway")
  SqlSessionFactory securitySqlSessionFactory(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("yakSecurityGlobalConfig") GlobalConfig globalConfig,
      @Qualifier("yakSecurityMybatisPlusInterceptor") MybatisPlusInterceptor interceptor)
      throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    factory.setConfiguration(configuration);
    factory.setGlobalConfig(globalConfig);
    factory.setPlugins(interceptor);
    SqlSessionFactory sqlSessionFactory = factory.getObject();
    if (sqlSessionFactory == null) {
      throw new IllegalStateException("Failed to create yakSecuritySqlSessionFactory");
    }
    return sqlSessionFactory;
  }

  @Bean("yakSecuritySqlSessionTemplate")
  SqlSessionTemplate securitySqlSessionTemplate(
      @Qualifier("yakSecuritySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @Bean("yakSecurityTransactionManager")
  PlatformTransactionManager securityTransactionManager(
      @Qualifier("yakBusinessTransactionManager")
          PlatformTransactionManager transactionManager) {
    return transactionManager;
  }

  private static String requireApplicationName(YakSecurityProperties properties) {
    String applicationName = properties.getApplicationName();
    if (!StringUtils.hasText(applicationName)) {
      throw new IllegalStateException("Missing required configuration: yak.security.application-name");
    }
    return applicationName;
  }
}
