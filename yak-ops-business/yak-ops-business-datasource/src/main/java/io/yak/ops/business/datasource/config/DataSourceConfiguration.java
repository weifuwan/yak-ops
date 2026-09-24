package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 Datasource capability 自身的配置属性，不负责应用级 DataSource/MyBatis 装配。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfiguration {}
