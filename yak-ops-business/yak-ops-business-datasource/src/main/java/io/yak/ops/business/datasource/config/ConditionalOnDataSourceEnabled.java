package io.yak.ops.business.datasource.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 仅在 yak.datasource.enabled 开启时装配 Datasource capability Bean。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "yak.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnDataSourceEnabled {}
