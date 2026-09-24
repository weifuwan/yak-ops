package io.yak.ops.business.datasource.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 数据源管理模块基础设施配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(BusinessDatabaseConfiguration.class)
@MapperScan(
    basePackages = "io.yak.ops.business.datasource.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class DataSourceConfiguration {

}
