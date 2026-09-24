package io.yak.ops.boot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.ops.common.mybatis.MybatisPlusFactorySupport;
import java.util.Objects;
import javax.sql.DataSource;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

/** Central DataSource and MyBatis-Plus runtime assembly for Yak Ops. */
@Configuration(proxyBeanMethods = false)
@MapperScans({
    @MapperScan(
        basePackages = "io.yak.ops.business.datasource.dao.mapper",
        sqlSessionTemplateRef = "yakBusinessSqlSessionTemplate"),
    @MapperScan(
        basePackages = "io.yak.ops.dao.mapper.security",
        sqlSessionTemplateRef = "yakSecuritySqlSessionTemplate")
})
public class MybatisPlusConfiguration {

  @Primary
  @Bean(
      name = {
          "dataSource",
          "yakBusinessDataSource",
          "opsDataSource",
          "opsResourceDataSource",
          "offlineSyncDataSource",
          "yakSecurityDataSource"
      },
      destroyMethod = "close")
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource yakOpsDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Primary
  @Bean(
      name = {
          "transactionManager",
          "yakBusinessTransactionManager",
          "opsDataSourceTransactionManager",
          "opsResourceTransactionManager",
          "offlineSyncTransactionManager",
          "yakSecurityTransactionManager"
      })
  public PlatformTransactionManager yakOpsTransactionManager(
      @Qualifier("dataSource") DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Primary
  @DependsOn("yakOpsFlyway")
  @Bean(
      name = {
          "sqlSessionFactory",
          "yakBusinessSqlSessionFactory",
          "opsDataSourceSqlSessionFactory",
          "opsResourceSqlSessionFactory",
          "offlineSyncSqlSessionFactory"
      })
  public SqlSessionFactory yakBusinessSqlSessionFactory(
      @Qualifier("dataSource") DataSource dataSource) throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setTypeAliasesPackage("io.yak.ops.common.bean.po.datasource");

    Resource[] mapperLocations =
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml");
    if (mapperLocations.length > 0) factory.setMapperLocations(mapperLocations);

    factory.setConfiguration(MybatisPlusFactorySupport.createConfiguration());
    factory.setGlobalConfig(MybatisPlusFactorySupport.createGlobalConfig());

    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    factory.setPlugins(interceptor);
    return requireFactory(factory.getObject(), "yakBusinessSqlSessionFactory");
  }

  @Primary
  @Bean(
      name = {
          "sqlSessionTemplate",
          "yakBusinessSqlSessionTemplate",
          "opsDataSourceSqlSessionTemplate",
          "opsResourceSqlSessionTemplate",
          "offlineSyncSqlSessionTemplate"
      })
  public SqlSessionTemplate yakBusinessSqlSessionTemplate(
      @Qualifier("yakBusinessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @DependsOn("yakOpsFlyway")
  @Bean("yakSecuritySqlSessionFactory")
  public SqlSessionFactory yakSecuritySqlSessionFactory(
      @Qualifier("dataSource") DataSource dataSource,
      YakSecurityProperties properties) throws Exception {
    String applicationName = requireText(properties.getApplicationName(), "yak.security.application-name");

    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);

    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    factory.setConfiguration(configuration);
    factory.setGlobalConfig(securityGlobalConfig(applicationName));
    factory.setPlugins(securityInterceptor(applicationName));
    return requireFactory(factory.getObject(), "yakSecuritySqlSessionFactory");
  }

  @Bean("yakSecuritySqlSessionTemplate")
  public SqlSessionTemplate yakSecuritySqlSessionTemplate(
      @Qualifier("yakSecuritySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  private GlobalConfig securityGlobalConfig(String applicationName) {
    GlobalConfig globalConfig = new GlobalConfig();
    globalConfig.setBanner(false);
    globalConfig.setMetaObjectHandler(new SecurityMetaObjectHandler(applicationName));

    GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
    dbConfig.setIdType(IdType.AUTO);
    globalConfig.setDbConfig(dbConfig);
    return globalConfig;
  }

  private MybatisPlusInterceptor securityInterceptor(String applicationName) {
    TenantLineHandler tenantLineHandler =
        new TenantLineHandler() {
          @Override
          public Expression getTenantId() {
            return new StringValue(applicationName);
          }

          @Override
          public String getTenantIdColumn() {
            return "app_name";
          }
        };

    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MARIADB));
    return interceptor;
  }

  private static SqlSessionFactory requireFactory(
      SqlSessionFactory sqlSessionFactory,
      String beanName) {
    return Objects.requireNonNull(sqlSessionFactory, "Failed to create " + beanName);
  }

  private static String requireText(String value, String key) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("Missing required configuration: " + key);
    }
    return value;
  }

  private static final class SecurityMetaObjectHandler implements MetaObjectHandler {

    private final String applicationName;

    private SecurityMetaObjectHandler(String applicationName) {
      this.applicationName = applicationName;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
      strictInsertFill(metaObject, "appName", String.class, applicationName);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
      // appName is an ownership key and must not change during update.
    }
  }
}
