package io.yak.ops.modeling.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring integration for the data modeling application services.
 *
 * <p>All Modeling persistence components are provided by yak-ops-dao. This
 * module registers application services, DDL implementations, and the datasource
 * infrastructure bridge used to open physical database sessions.
 */
@Configuration
@ComponentScan(basePackages = {
        "io.yak.ops.modeling.application",
        "io.yak.ops.modeling.ddl",
        "io.yak.ops.modeling.infrastructure.ddl"
})
public class ModelingConfiguration {
}
