package io.yak.ops.modeling.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring integration for the data modeling application services.
 *
 * <p>All Modeling persistence components are provided by yak-ops-dao. This
 * module only registers application services and their orchestration helpers.
 */
@Configuration
@ComponentScan(basePackages = "io.yak.ops.modeling.application")
public class ModelingConfiguration {
}
