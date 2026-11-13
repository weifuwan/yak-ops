package io.yak.ops.modeling.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring integration for the data modeling business module.
 *
 * <p>The module owns its services and persistence mappers while reusing Yak Ops'
 * shared datasource and MyBatis session factory. Its HTTP boundary delegates
 * authentication and project authorization to the existing API services.
 */
@Configuration
@ComponentScan(basePackages = {
        "io.yak.ops.modeling.application",
        "io.yak.ops.modeling.infrastructure"
})
@MapperScan(
        basePackages = "io.yak.ops.modeling.infrastructure.persistence.mapper",
        sqlSessionFactoryRef = "sqlSessionFactory")
public class ModelingConfiguration {
}
