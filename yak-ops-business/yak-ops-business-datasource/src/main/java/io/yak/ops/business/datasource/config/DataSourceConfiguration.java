package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Datasource capability configuration. Application infrastructure wiring is owned by yak-ops-boot. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfiguration {}
